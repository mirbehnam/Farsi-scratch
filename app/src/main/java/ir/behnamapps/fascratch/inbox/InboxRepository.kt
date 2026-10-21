package ir.behnamapps.fascratch.inbox

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.Network
import android.os.SystemClock
import android.util.LruCache
import ir.behnamapps.fascratch.BuildConfig
import ir.behnamapps.fascratch.inappbilling.learning.LearningIdentity
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.HttpsURLConnection

/** Lazy, home-scoped synchronization. Never owns an Activity, billing token or persistent timer. */
internal class InboxRepository private constructor(context: Context) {
    private val app = context.applicationContext
    private val disk = InboxDisk(app)
    private val identity get() = LearningIdentity.get(app)
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val metadata = Mutex()
    private val synchronization = Mutex()
    private val images = Semaphore(2)
    private val imageLocks = mutableMapOf<String, Mutex>()
    private val generation = AtomicLong()
    private val mutable = MutableStateFlow(InboxState())
    val state = mutable.asStateFlow()
    private val base = BuildConfig.COURSE_API_BASE.trimEnd('/')
    private val query = "?flavor=${BuildConfig.FLAVOR}&version=${BuildConfig.VERSION_CODE}"
    private data class Session(val uuid: String, val secret: String, val key: String)
    @Volatile private var session: Session? = null
    private var personalKey: String? = null
    private var publicFeed: InboxFeed? = null
    private var personalFeed: InboxFeed? = null
    private var publicRead = emptyMap<String,Int>()
    private var personalRead = emptyMap<String,Int>()
    private var lastPublic = 0L
    private var lastPersonal = 0L
    private var publicCooldown = 0L
    private var personalCooldown = 0L
    private val bitmaps = object: LruCache<String,Bitmap>(6*1024*1024) { override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount }

    private fun capture(): Session? = runCatching {
        val profile = identity.profile() ?: return@runCatching null
        val secret = identity.secret()
        Session(profile.uuid,secret,sha((profile.uuid + ":" + secret).toByteArray()))
    }.getOrNull()
    private fun sameSession(value: Session): Boolean = session?.key == value.key && capture()?.key == value.key
    private fun feedName(key: String?) = if(key == null) "public.json" else "personal-$key.bin"
    private fun readName(owner: String?) = if(owner == null) "public-read.json" else "reads-${sha(owner.toByteArray())}.bin"

    private fun readMarks(owner: String?): Map<String,Int> = runCatching {
        val json = JSONObject(String(disk.read(readName(owner),owner != null,32*1024) ?: return emptyMap(),Charsets.UTF_8))
        json.keys().asSequence().take(100).associateWith { json.optInt(it).coerceAtLeast(0) }
    }.getOrDefault(emptyMap())
    private fun writeMarks(owner: String?, marks: Map<String,Int>) = runCatching {
        disk.write(readName(owner),JSONObject(marks).toString().toByteArray(),owner != null)
    }
    private fun cachedFeed(value: Session?): InboxFeed? = runCatching {
        val wrapper = JSONObject(String(disk.read(feedName(value?.key),value != null,InboxPolicy.MAX_FEED_BYTES + 1024) ?: return null,Charsets.UTF_8))
        InboxPolicy.parse(wrapper.getJSONObject("body").toString().toByteArray(),wrapper.optString("etag").takeIf { it.isNotBlank() },
            if(value == null) "public" else "personal",value?.uuid,BuildConfig.FLAVOR,BuildConfig.VERSION_CODE,base)
    }.getOrNull()
    private fun storeFeed(value: Session?, feed: InboxFeed) {
        val bytes = JSONObject().put("etag",feed.etag ?: "").put("body",feed.json).toString().toByteArray()
        disk.write(feedName(value?.key),bytes,value != null)
    }
    private fun rebuild() {
        val privateVisible = session?.key != null && session?.key == personalKey
        val messages = InboxPolicy.visibleMessages(publicFeed?.messages.orEmpty(),personalFeed?.messages.orEmpty(),session?.key,personalKey,System.currentTimeMillis())
        mutable.value = mutable.value.copy(messages = messages,read = publicRead + if(privateVisible) personalRead else emptyMap(), sessionKey = session?.key)
    }

    private suspend fun initialize() = withContext(Dispatchers.IO) {
        metadata.withLock {
            if (!mutable.value.initialized) {
                publicFeed = cachedFeed(null); publicRead = readMarks(null)
                mutable.value = mutable.value.copy(initialized = true); rebuild()
            }
        }
        reconcileIdentity()
    }
    private suspend fun reconcileIdentity(): Boolean = withContext(Dispatchers.IO) {
        val next = capture()
        metadata.withLock {
            if (next?.key == session?.key && personalKey == next?.key) return@withLock false
            session = next; personalKey = next?.key; generation.incrementAndGet(); lastPersonal = 0L; personalCooldown = 0L
            personalFeed = if(next != null) cachedFeed(next) else null
            personalRead = if(next != null) readMarks(next.uuid) else emptyMap()
            runCatching { disk.clearPrivate(next?.key) }; rebuild(); true
        }
    }

    /** Called synchronously after identity changes: hide private content before any disk cleanup. */
    private fun identityChanged() {
        val epoch = generation.incrementAndGet(); session = null; bitmaps.evictAll()
        mutable.value = mutable.value.copy(messages = mutable.value.messages.filter { it.scope == "public" },
            read = mutable.value.read.filterKeys { it.startsWith("public:") },sessionKey = null)
        io.launch {
            metadata.withLock {
                if(generation.get() == epoch) { personalFeed = null; personalKey = null; personalRead = emptyMap(); runCatching { disk.clearPrivate() }; rebuild() }
            }
            reconcileIdentity()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun connections(): Flow<Unit> = callbackFlow {
        val manager = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val callback = object: ConnectivityManager.NetworkCallback() { override fun onAvailable(network: Network) { trySend(Unit) } }
        val registered = runCatching { manager?.registerDefaultNetworkCallback(callback); manager != null }.getOrDefault(false)
        awaitClose { if(registered) runCatching { manager?.unregisterNetworkCallback(callback) } }
    }.conflate()

    suspend fun active(): Unit = coroutineScope {
        initialize()
        launch { identity.changes.collect { if(reconcileIdentity()) refresh() } }
        launch { connections().collect { refresh() } }
        while(isActive) {
            refresh()
            metadata.withLock { rebuild() }
            val nextExpiry = state.value.messages.mapNotNull { it.expiresMs }.minOrNull()
            delay(nextExpiry?.let { (it-System.currentTimeMillis()).coerceIn(1000,300_000) } ?: 300_000)
        }
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        initialize()
        if (!synchronization.tryLock()) return@withContext
        try {
            mutable.value = mutable.value.copy(refreshing = true)
            var failed = false
            for (scope in listOf("public","personal")) {
                val account = if(scope == "personal") session else null
                if(scope == "personal" && account == null) continue
                val now = SystemClock.elapsedRealtime()
                val last = if(scope == "public") lastPublic else lastPersonal
                val cooldown = if(scope == "public") publicCooldown else personalCooldown
                if(!InboxPolicy.refreshDue(now,last,cooldown)) continue
                if(scope == "public") lastPublic = now else lastPersonal = now
                val epoch = generation.get()
                val old = metadata.withLock { if(scope == "public") publicFeed else personalFeed }
                try {
                    val response = request("$base/inbox/$scope$query",old?.etag,account?.secret,InboxPolicy.MAX_FEED_BYTES,"application/json")
                    if(response.code == 304) { require(old != null); continue }
                    val feed = InboxPolicy.parse(response.bytes!!,response.etag,scope,account?.uuid,BuildConfig.FLAVOR,BuildConfig.VERSION_CODE,base)
                    metadata.withLock {
                        if(account != null && (epoch != generation.get() || !sameSession(account))) return@withLock
                        storeFeed(account,feed)
                        if(account == null) { publicFeed = feed; publicRead = publicRead.filterKeys { key -> feed.messages.any { it.readKey == key } }; writeMarks(null,publicRead) }
                        else { personalFeed = feed; personalKey = account.key; personalRead = personalRead.filterKeys { key -> feed.messages.any { it.readKey == key } }; writeMarks(account.uuid,personalRead) }
                        rebuild(); pruneImages()
                    }
                } catch(cancelled: CancellationException) { throw cancelled }
                catch(error: InboxHttp) {
                    failed = true
                    if(scope == "public") publicCooldown = now + error.retryMs else personalCooldown = now + error.retryMs
                    if(account != null && error.code in setOf(401,403)) metadata.withLock {
                        if(epoch == generation.get() && sameSession(account)) { personalFeed = null; runCatching { disk.clearPrivate() }; rebuild() }
                    }
                } catch(_: Exception) { failed = true }
            }
            mutable.value = mutable.value.copy(failed = failed)
            // Bounded warm-up; never decode images or block the text snapshot on media downloads.
            coroutineScope {
                val candidates = state.value.messages.filter { it.image != null }.sortedBy { !InboxPolicy.unread(it.notificationVersion,state.value.read[it.readKey]) }.take(8)
                candidates.map { message -> async { try { mediaBytes(message,state.value.sessionKey) } catch(cancelled: CancellationException) { throw cancelled } catch(_: Exception) { null } } }.awaitAll()
            }
        } finally { mutable.value = mutable.value.copy(refreshing = false); synchronization.unlock() }
    }

    fun read(message: InboxMessage, expectedSession: String?) { io.launch {
        metadata.withLock {
            if(message.scope == "personal" && expectedSession != session?.key) return@withLock
            if(mutable.value.messages.none { it.readKey == message.readKey && it.notificationVersion == message.notificationVersion }) return@withLock
            if(message.scope == "public") { publicRead = publicRead + (message.readKey to message.notificationVersion); writeMarks(null,publicRead) }
            else { personalRead = personalRead + (message.readKey to message.notificationVersion); writeMarks(session?.uuid,personalRead) }
            rebuild()
        }
    } }
    fun readAll(expectedSession: String?) { io.launch {
        metadata.withLock {
            if(expectedSession != session?.key) return@withLock
            val rows = mutable.value.messages
            publicRead = rows.filter { it.scope == "public" }.associate { it.readKey to it.notificationVersion }
            personalRead = rows.filter { it.scope == "personal" }.associate { it.readKey to it.notificationVersion }
            writeMarks(null,publicRead); session?.let { writeMarks(it.uuid,personalRead) }; rebuild()
        }
    } }

    private fun imageName(message: InboxMessage, key: String?) = if(message.scope == "public") "public-image-${message.image!!.hash}.webp" else "private-image-$key-${message.image!!.hash}.bin"
    private fun visible(message: InboxMessage, key: String?): Boolean = (message.scope == "public" || (key != null && key == session?.key)) &&
        state.value.messages.any { it.readKey == message.readKey && it.image?.hash == message.image?.hash }
    private fun pruneImages() {
        val keep = state.value.messages.filter { it.image != null }.map { imageName(it,session?.key) }.toSet()
        disk.pruneImages(keep)
        synchronized(imageLocks) { imageLocks.keys.filter { it !in keep && imageLocks[it]?.isLocked != true }.forEach { imageLocks.remove(it) } }
    }
    private suspend fun mediaBytes(message: InboxMessage, key: String?): ByteArray? = images.withPermit {
        val image = message.image ?: return@withPermit null
        if(!visible(message,key)) return@withPermit null
        val name = imageName(message,key)
        val lock = synchronized(imageLocks) { imageLocks.getOrPut(name) { Mutex() } }
        lock.withLock {
            val cached = metadata.withLock { disk.read(name,message.scope == "personal",InboxPolicy.MAX_IMAGE_BYTES) }
            if(cached != null && cached.size == image.bytes && sha(cached) == image.hash) return@withLock cached
            val owner = if(message.scope == "personal") session?.takeIf { it.key == key } ?: return@withLock null else null
            if(!InboxPolicy.mediaUrl(image.url,message.scope,base,message.id,image.hash)) return@withLock null
            val bytes = request(image.url,null,owner?.secret,InboxPolicy.MAX_IMAGE_BYTES,"image/webp").bytes ?: return@withLock null
            require(bytes.size == image.bytes && sha(bytes) == image.hash)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
            require(bounds.outWidth == image.width && bounds.outHeight == image.height && bounds.outMimeType == "image/webp")
            metadata.withLock {
                if(!visible(message,key) || (owner != null && !sameSession(owner))) return@withLock
                disk.write(name,bytes,message.scope == "personal"); pruneImages()
            }
            bytes.takeIf { visible(message,key) }
        }
    }

    suspend fun bitmap(message: InboxMessage, key: String?): Bitmap? = withContext(Dispatchers.IO) {
        try {
            if(message.image == null || !visible(message,key)) return@withContext null
            val name = imageName(message,key); bitmaps.get(name)?.let { return@withContext it }
            val bytes = mediaBytes(message,key) ?: return@withContext null
            var sample = 1
            while(message.image.width/sample > 1024 || message.image.height/sample > 768) sample *= 2
            val result = BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply { inSampleSize = sample }) ?: return@withContext null
            if(!visible(message,key)) return@withContext null
            bitmaps.put(name,result); result
        } catch(cancelled: CancellationException) { throw cancelled } catch(_: Exception) { null }
    }

    private data class Reply(val code: Int, val bytes: ByteArray?, val etag: String?)
    private class InboxHttp(val code: Int, val retryMs: Long): Exception()
    private fun request(url: String, etag: String?, secret: String?, max: Int, mime: String): Reply {
        require(InboxPolicy.safeLink(url))
        val connection = URL(url).openConnection() as HttpsURLConnection
        try {
            connection.connectTimeout = 4000; connection.readTimeout = 6000; connection.instanceFollowRedirects = false
            connection.useCaches = false; connection.setRequestProperty("Accept",mime)
            etag?.let { connection.setRequestProperty("If-None-Match",it) }
            secret?.let { require(java.net.URI(url).host == java.net.URI(base).host); connection.setRequestProperty("X-Scratch-Account",it) }
            val code = connection.responseCode
            if(code == 304) return Reply(code,null,etag)
            if(code != 200) throw InboxHttp(code,(connection.getHeaderField("Retry-After")?.toLongOrNull() ?: 0).coerceIn(0,3600)*1000)
            require(connection.contentLengthLong <= max && connection.contentType.orEmpty().startsWith(mime))
            return Reply(code,connection.inputStream.use { InboxDisk.bounded(it,max) },connection.getHeaderField("ETag"))
        } finally { connection.disconnect() }
    }
    companion object {
        @Volatile private var instance: InboxRepository? = null
        fun get(context: Context) = instance ?: synchronized(this) { instance ?: InboxRepository(context).also { instance = it } }
        fun identityChanged(context: Context) { get(context).identityChanged() }
        private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
