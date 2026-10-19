package ir.behnamapps.fascratch.inappbilling.data

import android.content.Context
import android.os.SystemClock
import ir.behnamapps.fascratch.BuildConfig
import ir.behnamapps.fascratch.inappbilling.domain.ClientCommandPolicy
import ir.behnamapps.fascratch.inappbilling.domain.TrainingVersionRule
import ir.behnamapps.fascratch.home.HomeBannerCache
import ir.behnamapps.fascratch.inappbilling.presentation.clearArtworkMemory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

internal data class ProfileStop(val message: String, val icon: String, val imageUrl: String?, val imageHash: String?)
internal data class ClientCommandState(val initialized: Boolean = false, val blockedMessage: String? = null, val generation: Long = 0, val cacheEpoch: Long = 0, val profileStop: ProfileStop? = null)

/** Public control plane only. Never clears credentials, learning outbox or downloaded lessons. */
internal object ClientCommands {
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(ClientCommandState())
    val state = mutable.asStateFlow()
    private var attemptedAt: Long? = null

    suspend fun refresh(context: Context) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val app = context.applicationContext
            val prefs = app.getSharedPreferences("scratch-client-commands", Context.MODE_PRIVATE)
            if (!mutable.value.initialized) {
                val cached = runCatching { JSONObject(prefs.getString("config", "{}")!!) }.getOrNull()
                mutable.value = ClientCommandState(true, runCatching { cached?.takeIf { it.optInt("schema") == 1 }?.let { blockedMessage(it, BuildConfig.FLAVOR, BuildConfig.VERSION_CODE) } }.getOrNull(),
                    prefs.getLong("applied_generation", 0), profileStop = cached?.let { profileStop(it) })
            }
            val now = SystemClock.elapsedRealtime()
            if (attemptedAt?.let { now - it < 25_000 } == true) return@withLock
            attemptedAt = now
            val config = runCatching {
                val connection = URL(BuildConfig.COURSE_API_BASE.trimEnd('/') + "/client-controls").openConnection() as HttpsURLConnection
                try {
                    connection.connectTimeout = 2500; connection.readTimeout = 2500
                    connection.instanceFollowRedirects = false; connection.useCaches = false
                    connection.setRequestProperty("Accept", "application/json")
                    require(connection.responseCode == 200)
                    JSONObject(connection.inputStream.use { String(it.readBytesBounded(32 * 1024), Charsets.UTF_8) }).getJSONObject("data").also {
                        require(it.getInt("schema") == 1 && it.getLong("cache_generation") >= 0)
                        require(it.getJSONArray("rules").length() <= 30)
                        blockedMessage(it, BuildConfig.FLAVOR, BuildConfig.VERSION_CODE) // Validate before persistence/deletion.
                    }
                } finally { connection.disconnect() }
            }.getOrNull() ?: return@withLock
            val generation = config.getLong("cache_generation")
            val message = blockedMessage(config, BuildConfig.FLAVOR, BuildConfig.VERSION_CODE)
            prefs.edit().putString("config", config.toString()).commit()
            mutable.value = mutable.value.copy(blockedMessage = message, profileStop = profileStop(config))
            var applied = prefs.getLong("applied_generation", 0)
            val previous = applied
            if (ClientCommandPolicy.needsCacheClear(generation, applied)) {
                val success = runCatching {
                    CourseImageCache.clear(app)
                    HomeBannerCache.clear(app)
                    CourseCache(app).clearCatalogLayout()
                    clearArtworkMemory()
                }.isSuccess
                if (success) applied = generation // Failed deletions retry next refresh.
            }
            prefs.edit().putString("config", config.toString()).putLong("applied_generation", applied).commit()
            mutable.value = ClientCommandState(true, message, applied,
                mutable.value.cacheEpoch + if (applied > previous) 1 else 0, profileStop(config))
        }
    }

    internal fun profileStop(config: JSONObject): ProfileStop? {
        val stop = config.optJSONObject("profile_stop") ?: return null
        if (!stop.optBoolean("enabled", false)) return null
        val message = stop.optString("message").take(1000).trim()
        if (message.isEmpty()) return null
        val icon = stop.optString("icon").takeIf { it in setOf("update", "maintenance", "notice", "none", "custom") } ?: "none"
        val hash = stop.optString("image_hash").takeIf { it.matches(Regex("[a-f0-9]{64}")) }
        // Build the image URL from our API origin, never from an arbitrary admin-provided URL.
        val url = hash?.let { BuildConfig.COURSE_API_BASE.trimEnd('/') + "/control-images/" + it }
        return ProfileStop(message, icon, url, hash)
    }

    internal fun blockedMessage(config: JSONObject, flavor: String, version: Int): String? {
        val rules = config.getJSONArray("rules")
        val parsed = (0 until rules.length()).map { i ->
            val rule = rules.getJSONObject(i)
            TrainingVersionRule(rule.getString("store"), rule.getInt("min_version"), rule.getInt("max_version"), rule.getBoolean("enabled"), rule.getString("message"))
        }
        return ClientCommandPolicy.blockedMessage(config.getBoolean("maintenance"), config.getString("maintenance_message"), parsed, flavor, version)
    }
}
