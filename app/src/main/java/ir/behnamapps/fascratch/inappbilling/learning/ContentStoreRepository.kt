package ir.behnamapps.fascratch.inappbilling.learning

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import ir.behnamapps.fascratch.inappbilling.data.CourseApi
import ir.behnamapps.fascratch.inappbilling.data.readBytesBounded
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.UUID

internal data class ContentPackage(val uuid: String, val title: String, val description: String, val mode: String, val coins: Int,
    val owned: Boolean, val pricePaid: Int?, val purchasedAt: String?, val cover: String?, val types: Set<String>, val bytes: Long,
    val downloads: Long, val files: List<ContentPart>) {
    companion object {
        fun parse(j: JSONObject): ContentPackage {
            UUID.fromString(j.getString("uuid")); val types = j.optJSONArray("types") ?: JSONArray(); val files = j.optJSONArray("files") ?: JSONArray()
            return ContentPackage(j.getString("uuid"), j.getString("title").take(120), j.optString("description").take(5000), j.getString("access_mode"),
                j.optInt("price_coins"), j.optBoolean("owned"), j.optInt("purchase_price").takeIf { !j.isNull("purchase_price") }, j.optString("purchased_at").takeUnless { it.isBlank() || it == "null" },
                j.optString("cover_url").takeUnless { it.isBlank() || it == "null" }, (0 until types.length()).map { types.getString(it) }.toSet(), j.optLong("bytes"), j.optLong("downloads"),
                (0 until files.length().coerceAtMost(100)).map { val f = files.getJSONObject(it); ContentPart(f.getString("uuid"), f.getString("kind"), contentDisplayName(f.optJSONObject("names"), f.getString("name")), f.optString("preview_url"), f.optString("thumbnail_url").takeUnless { it.isBlank() || it == "null" }) })
        }
    }
}
internal fun contentDisplayName(names: JSONObject?, filename: String, language: String = java.util.Locale.getDefault().language): String =
    names?.optString(language)?.takeIf { it.isNotBlank() && it != "null" } ?: names?.optString("en")?.takeIf { it.isNotBlank() && it != "null" } ?: filename.substringBeforeLast('.', filename)

internal data class ContentPart(val uuid: String, val kind: String, val name: String, val preview: String, val thumbnail: String? = null)
internal data class StorePicture(val bitmap: Bitmap? = null, val gif: ByteArray? = null)
internal data class ContentTransfer(val status: String, val downloaded: Long = 0, val total: Long = 0, val message: String? = null)
internal data class ContentCatalog(val items: List<ContentPackage>, val more: Boolean, val page: Int, val count: Int = 0, val spent: Long = 0)

/** Lazily created by the store UI. Uses the existing account, API connections and ad manager. */
internal class ContentStoreRepository private constructor(context: Context) {
    private val app = context.applicationContext
    private val api = CourseApi(app)
    private val identity = LearningIdentity.get(app)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val installer = ContentPackageInstaller.get(app.filesDir)
    private val pending = app.getSharedPreferences("content-store-operations", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val mutable = MutableStateFlow<Map<String, ContentTransfer>>(emptyMap())
    val transfers = mutable.asStateFlow()
    val ownedByAccount = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val wallet = MutableStateFlow(identity.profile()?.coins ?: 0.0)
    val owner = identity.changes.map { identity.profile()?.uuid }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, identity.profile()?.uuid)
    private val pictures = object : LruCache<String, StorePicture>(12 * 1048576) {
        override fun sizeOf(key: String, value: StorePicture) = value.gif?.size ?: value.bitmap?.allocationByteCount ?: 0
    }
    @Volatile private var localPackages = emptyMap<String, String>()
    init { installer.recover(); refreshLocal() }
    private fun refreshLocal() {
        val entries = installer.entries()
        localPackages = entries.keys().asSequence().filter { installer.installed(it) }.associateWith { entries.getJSONObject(it).optString("title", "بستهٔ دانلودی") }
    }
    private fun checkSession(secret: String) { check(identity.secret() == secret) { "حساب تغییر کرده؛ فروشگاه را دوباره باز کن." } }
    suspend fun load(query: String = "", kind: String = "", owned: Boolean = false, page: Int = 1): ContentCatalog = withContext(Dispatchers.IO) {
        if (identity.profile() == null) api.account()
        val secret = identity.secret()
        val path = if (owned) "owned?page=$page" else "?q=${URLEncoder.encode(query, "UTF-8")}&kind=$kind&page=$page"
        val json = api.contentStore(path, secret); checkSession(secret)
        json.optJSONObject("wallet")?.let { wallet.value = it.optDouble("coins", wallet.value) }
        val rows = json.getJSONArray("items"); retryAcknowledgements(secret)
        val packages = (0 until rows.length()).map { ContentPackage.parse(rows.getJSONObject(it)) }
        identity.profile()?.uuid?.let { account -> ownedByAccount.update { current ->
            val previous = if (owned && page == 1) emptySet() else current[account] ?: emptySet()
            current + (account to ((previous - packages.map { it.uuid }.toSet()) + packages.filter { it.owned }.map { it.uuid }))
        } }
        ContentCatalog(packages, json.optBoolean("has_more"), json.optInt("page", page), json.optInt("count"), json.optLong("spent_coins"))
    }
    fun installed(uuid: String) = uuid in localPackages
    fun installedPackages(): List<Pair<String, String>> = localPackages.toList()
    suspend fun remove(uuid: String) = withContext(Dispatchers.IO) { mutex.withLock {
        saveAcknowledgements()
        installer.remove(uuid); refreshLocal()
        val edit = pending.edit(); pending.all.keys.filter { it.contains(":$uuid:") }.forEach { edit.remove(it) }; edit.commit()
        mutable.update { it - uuid }
    } }
    suspend fun picture(url: String?): StorePicture? = withContext(Dispatchers.IO) {
        if (url == null) return@withContext null
        pictures.get(url)?.let { return@withContext it }
        val c = runCatching { api.contentDownload(url, identity.secret()) }.getOrNull() ?: return@withContext null
        try {
            if (c.responseCode != 200) return@withContext null
            val bytes = c.inputStream.use { it.readBytesBounded(16 * 1048576) }
            if (bytes.size >= 6 && bytes.copyOfRange(0, 6).toString(Charsets.US_ASCII) in setOf("GIF87a", "GIF89a")) return@withContext StorePicture(gif = bytes).also { pictures.put(url, it) }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
            val opts = BitmapFactory.Options().apply { inSampleSize = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / 640) }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.let { StorePicture(bitmap = it) }?.also { pictures.put(url, it) }
        } catch (_: Exception) { null } finally { api.finishConnection(c) }
    }
    private fun operation(key: String): String = pending.getString(key, null) ?: UUID.randomUUID().toString().also { check(pending.edit().putString(key, it).commit()) }
    fun acquire(item: ContentPackage, method: String, context: Context) {
        if (mutable.value[item.uuid]?.status in setOf("downloading", "preparing")) return
        mutable.update { it + (item.uuid to ContentTransfer("preparing", message = "در حال آماده‌سازی…")) }
        scope.launch {
            var currentKey: String? = null
            try {
                if (identity.profile() == null) api.account()
                val secret = identity.secret(); val owner = identity.profile()?.uuid ?: error("ابتدا حساب را تازه کن.")
                val key = "$owner:${item.uuid}:$method"; currentKey = key; val op = operation(key)
                val saved = pending.getString("ticket:$key", null)?.let { JSONObject(it) }
                if (saved != null) {
                    try {
                        download(item, saved, owner, secret)
                        pending.edit().remove(key).remove("ticket:$key").remove("earned:$key").apply()
                        return@launch
                    } catch (e: ir.behnamapps.fascratch.inappbilling.domain.CourseFailure) {
                        if (e.status != 403) throw e
                        // Refresh an expired signed URL using the same operation/ad grant.
                        // Ownership and an already earned advertisement remain intact.
                        pending.edit().remove("ticket:$key").commit()
                    }
                }
                if (method == "ad") {
                    if (pending.getBoolean("earned:$key", false)) { settleAd(item, op, key, owner, secret); return@launch }
                    val reserved = api.contentStore("${item.uuid}/ads/reserve", secret, JSONObject().put("uuid", op)); checkSession(secret)
                    val ticket = RewardedAdTicket.parse(reserved.getJSONObject("ticket")); val offer = RewardedAdOffer.parse(reserved.getJSONObject("offer")) ?: error("تبلیغ فعلاً در دسترس نیست.")
                    withContext(Dispatchers.Main) {
                        RewardedAdManager.prepareTicket(context.applicationContext, offer, ticket)
                        RewardedAdManager.showContent(owner, ticket,
                            start = { checkSession(secret); api.contentStore("${item.uuid}/ads/start", secret, JSONObject().put("uuid", op)); Unit },
                            finish = { earned ->
                                if (earned) {
                                    pending.edit().putBoolean("earned:$key", true).commit()
                                    try { settleAd(item, op, key, owner, secret) } catch (e: Exception) { fail(item.uuid, e) }
                                } else {
                                    runCatching { api.contentStore("${item.uuid}/ads/cancel", secret, JSONObject().put("uuid", op)) }
                                    pending.edit().remove(key).remove("earned:$key").apply(); fail(item.uuid, IllegalStateException("برای دریافت این بسته، تبلیغ را تا پایان ببین."))
                                }
                            })
                    }
                } else {
                    val result = api.contentStore("${item.uuid}/acquire", secret, JSONObject().put("operation_uuid", op).put("method", method).put("price_coins", item.coins)); checkSession(secret)
                    result.optJSONObject("wallet")?.let { wallet.value = it.optDouble("coins", wallet.value) }
                    if (method == "coins") ownedByAccount.update { it + (owner to ((it[owner] ?: emptySet()) + item.uuid)) }
                    val ticket = result.getJSONObject("download"); pending.edit().putString("ticket:$key", ticket.toString()).commit()
                    download(item, ticket, owner, secret); pending.edit().remove(key).remove("ticket:$key").apply()
                }
            } catch (e: TimeoutCancellationException) {
                cancelUnusedAd(item, currentKey)
                fail(item.uuid, IllegalStateException("ویدیوی تبلیغاتی فعلاً آماده نیست؛ کمی بعد دوباره تلاش کن."))
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                if (method == "ad") cancelUnusedAd(item, currentKey)
                if (e is ir.behnamapps.fascratch.inappbilling.domain.CourseFailure && e.status in setOf(409, 410)) currentKey?.let { pending.edit().remove(it).remove("ticket:$it").remove("earned:$it").apply() }
                fail(item.uuid, e)
            }
        }
    }
    private suspend fun cancelUnusedAd(item: ContentPackage, key: String?) {
        if (key == null || pending.getBoolean("earned:$key", false) || pending.contains("ticket:$key")) return
        val op = pending.getString(key, null) ?: return
        runCatching { api.contentStore("${item.uuid}/ads/cancel", identity.secret(), JSONObject().put("uuid", op)) }
        pending.edit().remove(key).apply()
    }
    private suspend fun settleAd(item: ContentPackage, op: String, key: String, owner: String, secret: String) {
        repeat(15) {
            checkSession(secret)
            val result = try {
                api.contentStore("${item.uuid}/ads/complete", secret, JSONObject().put("uuid", op).put("is_rewarded", true))
            } catch (e: ir.behnamapps.fascratch.inappbilling.domain.CourseFailure) {
                if (e.status in setOf(404, 409, 410)) pending.edit().remove(key).remove("ticket:$key").remove("earned:$key").commit()
                throw e
            }
            val ticket = result.optJSONObject("content_download")
            if (ticket != null) {
                pending.edit().putString("ticket:$key", ticket.toString()).commit(); download(item, ticket, owner, secret)
                pending.edit().remove(key).remove("ticket:$key").remove("earned:$key").apply(); return
            }
            if (result.optString("status") in setOf("rewarded", "cancelled")) {
                pending.edit().remove(key).remove("ticket:$key").remove("earned:$key").commit()
                error("مهلت دریافت قبلی تمام شده؛ برای دریافت دوباره، تبلیغ تازه‌ای ببین.")
            }
            delay(2000)
        }
        error("تأیید تبلیغ هنوز نرسیده؛ کمی بعد «تلاش مجدد» را بزن. نتیجهٔ مشاهده محفوظ است.")
    }
    private suspend fun download(item: ContentPackage, ticket: JSONObject, owner: String, secret: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val bytes = ticket.getLong("bytes"); require(bytes in 1..539000000)
            val folder = File(app.filesDir, "content_store").apply { mkdirs() }; val part = File(folder, "${item.uuid}.part")
            val c = api.contentDownload(ticket.getString("url"), secret)
            try {
                if (c.responseCode != 200) throw ir.behnamapps.fascratch.inappbilling.domain.CourseFailure("دریافت شروع نشد؛ اینترنت یا مهلت لینک را بررسی کن.", c.responseCode)
                c.inputStream.use { input -> FileOutputStream(part).use { output ->
                    val buffer = ByteArray(65536); var got = 0L; var last = 0L
                    while (true) { checkSession(secret); val n = input.read(buffer); if (n < 0) break; got += n; require(got <= bytes); output.write(buffer, 0, n)
                        if (got - last >= 1048576 || got == bytes) { mutable.update { it + (item.uuid to ContentTransfer("downloading", got, bytes)) }; last = got }
                    }
                    output.fd.sync(); require(got == bytes) { "دانلود ناقص است؛ دوباره تلاش کن." }
                } }
                installer.install(part, ticket, item.title, owner) { identity.secret() == secret }; refreshLocal()
            } finally { api.finishConnection(c); part.delete() }
            mutable.update { it + (item.uuid to ContentTransfer("installed", bytes, bytes, "آماده است! در کتابخانه، دستهٔ دانلودی‌ها را باز کن.")) }
            retryAcknowledgements(secret); LearningRepository.get(app).refreshProfile()
        }
    }
    private fun saveAcknowledgements() {
        val entries = installer.entries(); val edit = pending.edit()
        for (uuid in entries.keys()) {
            val e = entries.getJSONObject(uuid)
            if (e.has("ack_uuid")) edit.putString("ack:${e.getString("ack_uuid")}", JSONObject().put("owner", e.optString("ack_owner")).put("hash", e.optString("ack_hash")).put("item", uuid).toString())
        }
        edit.commit()
    }
    private suspend fun retryAcknowledgements(secret: String) {
        saveAcknowledgements(); val owner = identity.profile()?.uuid
        for ((key, value) in pending.all.filterKeys { it.startsWith("ack:") }.entries.take(40)) {
            val e = runCatching { JSONObject(value as String) }.getOrNull() ?: continue
            if (e.optString("owner") != owner) continue
            runCatching { checkSession(secret); api.contentStore("downloads/${key.removePrefix("ack:")}/complete", secret, JSONObject().put("sha256", e.getString("hash"))); installer.acknowledge(e.getString("item")); pending.edit().remove(key).apply() }
        }
    }
    private fun fail(uuid: String, error: Exception) { mutable.update { it + (uuid to ContentTransfer("error", message = error.message?.takeIf { msg -> msg.any { c -> c in '\u0600'..'\u06FF' } } ?: "دریافت کامل نشد؛ اینترنت و فضای گوشی را بررسی کن و دوباره تلاش کن.")) } }
    companion object {
        @Volatile private var instance: ContentStoreRepository? = null
        fun get(context: Context) = instance ?: synchronized(this) { instance ?: ContentStoreRepository(context).also { instance = it } }
    }
}
