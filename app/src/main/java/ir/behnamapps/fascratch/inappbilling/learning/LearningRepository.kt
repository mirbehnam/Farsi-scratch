package ir.behnamapps.fascratch.inappbilling.learning

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import ir.behnamapps.fascratch.BuildConfig
import ir.behnamapps.fascratch.inappbilling.data.CourseApi
import ir.behnamapps.fascratch.inappbilling.data.PurchaseVault
import ir.behnamapps.fascratch.inappbilling.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

data class LearnerProfile(val uuid: String, val name: String, val level: Int, val watchMs: Long,
    val xpIntoLevel: Double, val xpForNext: Double, val rank: Int?, val enabled: Boolean) {
    companion object {
        fun parse(json: JSONObject) = LearnerProfile(json.getString("uuid"), json.getString("display_name"),
            json.getInt("level"), json.getLong("watch_ms"), json.getDouble("xp_into_level"), json.getDouble("xp_for_next_level"),
            if (json.isNull("rank")) null else json.optInt("rank"), json.optBoolean("is_enabled", true))
    }
}

/** Process singleton: independent of the video/Activity lifecycle and of store SDK callbacks. */
class LearningRepository private constructor(context: Context) {
    private val app = context.applicationContext
    internal val store = LearningStore(app)
    private val api = CourseApi(app)
    private val identity = LearningIdentity.get(app)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val mutable = MutableStateFlow<Map<String, LearnerProfile>>(emptyMap())
    val profiles = mutable.asStateFlow()
    private val preparing = mutableSetOf<String>()
    init { scope.launch { mutex.withLock { mutable.value = store.profiles() }; if (store.pending().isNotEmpty()) schedule() } }
    fun visit() { scope.launch {
        mutex.withLock { runCatching { api.account() }; mutable.value = store.profiles() }
    } }

    private suspend fun access(course: Course, force: Boolean = false): String {
        val vault = PurchaseVault(app)
        require(vault.wasVerified(course.id))
        val current = vault.access(course.id)
        if (!force && current != null && current.expiresAtMillis > System.currentTimeMillis() + 60_000) return current.token
        val receipt = vault.receipt(course.id) ?: throw CourseFailure("خرید نیاز به بازیابی دارد.")
        // Server-only receipt restoration; no competing SDK inventory request and no stale vault write.
        return api.verify(course, BuildConfig.BILLING_PROVIDER, receipt, true).token
    }
    private suspend fun authorized(course: Course, path: String, body: JSONObject? = null): JSONObject {
        if (!PurchaseVault(app).wasVerified(course.id)) return api.learning(path, body = body)
        return try { api.learning(path, access(course), body) }
        catch (failure: CourseFailure) {
            if (failure.status != 401) throw failure
            api.learning(path, access(course, true), body)
        }
    }
    fun prepare(course: Course, forceGrant: Boolean = false) {
        synchronized(preparing) { if (!preparing.add(course.id)) return }
        scope.launch {
            try {
                mutex.withLock {
                    api.account()
                    val oldGrant = store.grant(course.id)
                    if (forceGrant || oldGrant == null || serverNow(oldGrant) - oldGrant.getLong("issued_ms") > 86400000) {
                        val result = authorized(course, "grants", JSONObject().put("course_uuid", course.id))
                        identity.accept(result.optJSONObject("account"))
                        val grant = result.getJSONObject("grant").put("anchor_elapsed", SystemClock.elapsedRealtime()).put("anchor_wall", System.currentTimeMillis())
                            .put("boot_count", bootCount())
                        // Keep old grant capabilities until their queued events are acknowledged.
                        saveCapability(course.id, grant)
                        store.saveGrant(course.id, grant)
                        store.saveProfile(course.id, result.getJSONObject("profile"))
                    } else store.saveProfile(course.id, authorized(course, "profile"))
                    mutable.value = store.profiles()
                }
                sync()
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { /* Progress failures never block legal playback or erase pending time. */ }
            finally { synchronized(preparing) { preparing.remove(course.id) } }
        }
    }
    // Store encrypted capability snapshots in the same private DB, not plaintext preferences.
    private fun saveCapability(course: String, grant: JSONObject) { store.saveGrant("grant:${grant.getString("uuid")}:$course", grant) }
    internal fun grant(course: String): JSONObject? = store.grant(course)
    internal fun serverNow(grant: JSONObject): Long {
        return serverAt(grant, SystemClock.elapsedRealtime())
    }
    private fun bootCount() = android.provider.Settings.Global.getInt(app.contentResolver, android.provider.Settings.Global.BOOT_COUNT, -1)
    internal fun serverAt(grant: JSONObject, elapsed: Long): Long {
        val anchor = grant.getLong("anchor_elapsed")
        return grant.getLong("issued_ms") + if (elapsed >= anchor && grant.optInt("boot_count", -1) == bootCount())
            elapsed - anchor else System.currentTimeMillis() - grant.getLong("anchor_wall")
    }
    fun tracker(course: Course, lesson: Lesson): WatchTracker? {
        if (!PurchaseVault(app).wasVerified(course.id) && !lesson.isPreview) return null
        if (mutable.value[course.id]?.enabled == false) return null
        val current = grant(course.id)
        if (current?.optJSONObject("lesson_versions")?.optInt(lesson.id, -1) != lesson.version) prepare(course, true)
        return WatchTracker(this, course.id, lesson)
    }
    internal fun recorded() { schedule() }
    internal fun flushNow() { scope.launch { runCatching { sync() }; if (store.pending().isNotEmpty()) schedule() } }
    fun counts(course: String) = store.counts(course)
    fun celebration(course: String) = store.celebration(course)
    fun clearCelebration(course: String) = store.clearCelebration(course)
    suspend fun name(course: Course?, value: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val result = api.learning("profile", body = JSONObject().put("display_name", value.trim()))
            store.saveProfile(LearningIdentity.KEY, result); mutable.value = store.profiles()
        }
    }
    suspend fun sync(): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            var sent = 0
            for ((course, grantId) in store.pending()) {
                while (sent < 6) {
                    val grant = store.grant("grant:$grantId:$course") ?: break
                    val rows = store.batch(course, grantId)
                    if (rows.isEmpty()) break
                    val body = JSONObject().put("grant_uuid", grantId).put("secret", grant.getString("secret")).put("events", JSONArray(rows))
                    val result = try { api.learning("events/batch", body = body) }
                    catch (failure: CourseFailure) {
                        if (failure.status == 410 || failure.status == 401) {
                            // Expired capabilities or a pruned guest cannot be recovered by retrying.
                            val reason = if (failure.status == 410) "offline_grant_expired" else "account_or_grant_removed"
                            rows.forEach { store.acknowledge(it.getString("uuid"), "rejected", reason) }
                            sent++; continue
                        }
                        throw failure
                    }
                    // Persist authoritative profile before removing ACKed events. Retrying is idempotent.
                    val official = result.getJSONObject("profile")
                    // An old outbox may still sync its former guest account after purchase recovery.
                    if (identity.profile()?.uuid == official.getString("uuid")) store.saveProfile(course, official)
                    val ack = result.getJSONArray("events")
                    for (index in 0 until ack.length()) {
                        val row = ack.getJSONObject(index)
                        if (rows.any { it.getString("uuid") == row.getString("uuid") })
                            store.acknowledge(row.getString("uuid"), row.getString("status"), row.optString("reason"))
                    }
                    mutable.value = store.profiles()
                    sent++
                }
            }
            store.pending().isNotEmpty()
        }
    }
    private fun schedule() {
        val scheduler = app.getSystemService(JobScheduler::class.java)
        if (scheduler.getPendingJob(JOB_ID) != null) return
        scheduler.schedule(JobInfo.Builder(JOB_ID, ComponentName(app, LearningSyncJob::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(30_000).setPersisted(true)
            .setBackoffCriteria(30_000, JobInfo.BACKOFF_POLICY_EXPONENTIAL).build())
    }
    companion object {
        const val JOB_ID = 7312
        @Volatile private var instance: LearningRepository? = null
        fun get(context: Context) = instance ?: synchronized(this) { instance ?: LearningRepository(context).also { instance = it } }
    }
}
