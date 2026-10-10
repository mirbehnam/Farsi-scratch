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

data class LearnerCourse(val uuid: String, val title: String, val watchMs: Long, val purchased: Boolean, val enabled: Boolean)
data class LearnerProfile(val uuid: String, val name: String, val level: Int, val watchMs: Long,
    val xpIntoLevel: Double, val xpForNext: Double, val rank: Int?, val enabled: Boolean,
    val nameSet: Boolean = false, val nameChangesLeft: Int = 3, val courses: List<LearnerCourse> = emptyList(), val codingMs: Long = 0, val fullName: String? = null,
    val nameBlocked: Boolean = false, val nameBlockedDays: Int = 0, val nameBlockReason: String? = null,
    val registered: Boolean = false, val username: String? = null, val googleLinked: Boolean = false) {
    companion object {
        fun parse(json: JSONObject) = LearnerProfile(json.getString("uuid"), json.getString("display_name"),
            json.getInt("level"), json.getLong("watch_ms"), json.getDouble("xp_into_level"), json.getDouble("xp_for_next_level"),
            if (json.isNull("rank")) null else json.optInt("rank"), json.optBoolean("is_enabled", true),
            json.optJSONObject("name_policy")?.optBoolean("is_set") ?: (json.optString("display_name") != "هنرجو"),
            json.optJSONObject("name_policy")?.optInt("remaining_changes", 3) ?: 3,
            json.optJSONArray("courses")?.let { rows -> (0 until rows.length()).map { i -> rows.getJSONObject(i).let {
                LearnerCourse(it.getString("uuid"), it.getString("title"), it.optLong("watch_ms"), it.optBoolean("purchased"), it.optBoolean("access_enabled"))
            } } } ?: emptyList(), json.optLong("coding_ms"), json.optString("full_name").takeUnless { it.isBlank() || it == "null" },
            json.optJSONObject("name_moderation")?.optBoolean("is_blocked") ?: false,
            (json.optJSONObject("name_moderation")?.optInt("remaining_days") ?: 0).coerceAtLeast(0),
            json.optJSONObject("name_moderation")?.optString("reason")?.takeUnless { it.isBlank() || it == "null" },
            json.optBoolean("registered"), json.optJSONObject("sign_in")?.optString("username")?.takeUnless { it.isBlank() || it == "null" },
            json.optJSONObject("sign_in")?.optBoolean("google") ?: false)
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
    private val knownCourses = java.util.concurrent.ConcurrentHashMap<String, Course>()
    private val lastPrepare = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private var foregroundSync: Job? = null
    private var lastSyncAttempt = -10_000L
    private var lastProfileAttempt = -10_000L
    private var codingJob: Job? = null
    private var codingStop: java.util.concurrent.atomic.AtomicLong? = null
    private val mutableStatus = MutableStateFlow<String?>(null)
    val status = mutableStatus.asStateFlow()
    private fun failure(error: Exception) {
        mutableStatus.value = if (error is CourseFailure) "ثبت پیشرفت: ${error.message} (HTTP ${error.status ?: 0})"
            else "ثبت پیشرفت انجام نشد؛ اتصال را بررسی کنید. اطلاعات ذخیره‌شده برای ارسال مجدد حفظ می‌شود."
    }
    init { scope.launch { mutex.withLock { mutable.value = store.profiles() }; if (store.pending().isNotEmpty()) schedule() } }
    fun visit() { scope.launch {
        mutex.withLock {
            if (SystemClock.elapsedRealtime() - lastProfileAttempt >= 10_000) {
                lastProfileAttempt = SystemClock.elapsedRealtime()
                try { api.account(); mutableStatus.value = null } catch (e: CancellationException) { throw e } catch (e: Exception) { failure(e) }
            }
            mutable.value = store.profiles()
        }
        if (store.pending().isNotEmpty()) flushNow()
    } }
    fun refreshProfile() { visit(); flushNow() }

    private suspend fun access(course: Course, force: Boolean = false): String {
        if (identity.profile()?.registered == true) return api.accountAccess(course, force).token
        val vault = PurchaseVault(app)
        require(vault.wasVerified(course.id))
        val current = vault.access(course.id)
        if (!force && current != null && current.expiresAtMillis > System.currentTimeMillis() + 60_000) return current.token
        val receipt = vault.receipt(course.id) ?: throw CourseFailure("خرید نیاز به بازیابی دارد.")
        // Server-only receipt restoration; no competing SDK inventory request and no stale vault write.
        return api.verify(course, BuildConfig.BILLING_PROVIDER, receipt, true).token
    }
    private suspend fun authorized(course: Course, path: String, body: JSONObject? = null): JSONObject {
        if (!ownsCourse(course.id)) return api.learning(path, body = body)
        return try { api.learning(path, access(course), body) }
        catch (failure: CourseFailure) {
            if (failure.status != 401) throw failure
            api.learning(path, access(course, true), body)
        }
    }
    fun prepare(course: Course, forceGrant: Boolean = false) {
        knownCourses[course.id] = course
        synchronized(preparing) { if (!preparing.add(course.id)) return }
        lastPrepare[course.id] = SystemClock.elapsedRealtime()
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
                    mutableStatus.value = null
                }
                sync()
            } catch (error: CancellationException) { throw error }
            catch (e: Exception) { failure(e) /* Never block legal playback or erase pending time. */ }
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
        if (!ownsCourse(course.id) && !lesson.isPreview) return null
        if (mutable.value[course.id]?.enabled == false) return null
        val current = grant(course.id)
        if (current?.optJSONObject("lesson_versions")?.optInt(lesson.id, -1) != lesson.version) prepare(course, true)
        return WatchTracker(this, course.id, lesson)
    }
    private fun ownsCourse(id: String): Boolean {
        val profile = identity.profile()
        return if (profile?.registered == true) profile.courses.any { it.uuid == id && it.purchased && it.enabled }
            else PurchaseVault(app).wasVerified(id)
    }
    internal fun retryPreparation(course: String, lesson: Lesson) {
        val grant = grant(course)
        if (grant?.optJSONObject("lesson_versions")?.optInt(lesson.id, -1) == lesson.version && serverNow(grant) <= grant.getLong("valid_until_ms")) return
        if (SystemClock.elapsedRealtime() - (lastPrepare[course] ?: 0L) >= 30_000)
            knownCourses[course]?.let { prepare(it, true) }
    }
    internal fun recorded() {
        schedule()
        synchronized(this) {
            if (foregroundSync?.isActive != true) foregroundSync = scope.launch { delay(120_000); sendPending() }
        }
    }
    private suspend fun sendPending() {
        if (store.pending().isEmpty()) return
        try { sync(); mutableStatus.value = null }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { failure(e) }
        if (store.pending().isNotEmpty()) schedule()
    }
    internal fun flushNow() { scope.launch { sendPending() } }
    fun counts(course: String) = store.counts(course)
    fun allCounts() = store.allCounts()
    fun celebration(course: String) = store.celebration(course)
    fun clearCelebration(course: String) = store.clearCelebration(course)
    suspend fun name(course: Course?, value: String, fullName: String? = null) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val result = api.learning("profile", body = JSONObject().put("display_name", value.trim())
                .put("full_name", fullName?.trim()?.takeIf { it.isNotEmpty() } ?: JSONObject.NULL))
            store.saveProfile(LearningIdentity.KEY, result); mutable.value = store.profiles()
        }
    }
    suspend fun authOptions(): JSONObject = api.auth("options")
    suspend fun googleChallenge(secret: String, expectedUuid: String): JSONObject = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(identity.profile()?.uuid == expectedUuid) { "حساب تغییر کرده؛ فرم را دوباره باز کن." }
            api.auth("google/native/challenge", JSONObject().put("session_secret", secret).put("expected_profile_uuid", expectedUuid)).also {
                require(!it.has("source_profile_uuid") || it.getString("source_profile_uuid") == expectedUuid) { "حساب تغییر کرده؛ فرم را دوباره باز کن." }
            }
        }
    }
    suspend fun googleComplete(flowUuid: String, secret: String, token: String, expectedUuid: String, confirmSwitch: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(identity.profile()?.uuid == expectedUuid) { "حساب تغییر کرده؛ فرم را دوباره باز کن." }
            val result = api.auth("google/native/complete", JSONObject().put("flow_uuid", flowUuid)
                .put("session_secret", secret).put("id_token", token).put("confirm_account_switch", confirmSwitch))
            val account = result.getJSONObject("account")
            if (account.optBoolean("confirmation_required")) return@withLock false
            identity.accept(account); mutable.value = store.profiles(); mutableStatus.value = null
            true
        }
    }
    suspend fun usernameAvailable(username: String): Boolean = api.auth("username-availability", JSONObject().put("username", username.trim())).getBoolean("available")
    suspend fun authenticate(register: Boolean, username: String, password: String, confirmation: String, secret: String, expectedUuid: String, displayName: String? = null) = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(identity.profile()?.uuid == expectedUuid) { "حساب تغییر کرده؛ فرم را دوباره باز کن." }
            val body = JSONObject().put("username", username.trim()).put("password", password)
                .put("password_confirmation", confirmation).put("session_secret", secret)
            if (register && displayName != null) body.put("display_name", displayName.trim())
            val result = api.auth(if (register) "register" else "login", body)
            identity.accept(result.getJSONObject("account")); mutable.value = store.profiles(); mutableStatus.value = null
        }
    }
    suspend fun sync(): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            // Lifecycle actions sync immediately, but near-simultaneous callbacks share a cooldown.
            if (store.pending().isEmpty()) return@withLock false
            val elapsed = SystemClock.elapsedRealtime()
            if (elapsed - lastSyncAttempt < 10_000) return@withLock true
            lastSyncAttempt = elapsed
            var sent = 0
            for ((course, grantId) in store.pending()) {
                while (sent < 1) {
                    val grant = store.grant("grant:$grantId:$course") ?: break
                    val rows = store.batch(course, grantId)
                    if (rows.isEmpty()) break
                    val body = JSONObject().put("grant_uuid", grantId).put("secret", grant.getString("secret")).put("events", JSONArray(rows))
                    val result = try { api.learning(if (course == CODING) "coding/events/batch" else "events/batch", body = body) }
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
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(120_000).setPersisted(true)
            .setBackoffCriteria(120_000, JobInfo.BACKOFF_POLICY_EXPONENTIAL).build())
    }
    /** The editor lifecycle owns this lightweight IO coroutine; never hooks WebView execution. */
    @Synchronized fun codingActive(active: Boolean) {
        if (!active) {
            codingStop?.compareAndSet(0L, SystemClock.elapsedRealtime())
            codingJob?.cancel(); codingJob = null; return
        }
        if (codingJob?.isActive == true) return
        val stoppedAt = java.util.concurrent.atomic.AtomicLong(0L)
        codingStop = stoppedAt
        codingJob = scope.launch {
            var last = SystemClock.elapsedRealtime()
            var preparedAt = -30_000L
            var recordingGrant: JSONObject? = null
            try {
                while (isActive) {
                    var grant = store.grant(CODING)
                    if ((grant == null || serverNow(grant) > grant.getLong("valid_until_ms") ||
                            serverNow(grant) - grant.getLong("issued_ms") > 86400000) &&
                        SystemClock.elapsedRealtime() - preparedAt >= 30_000) {
                        preparedAt = SystemClock.elapsedRealtime()
                        recordingGrant = null
                        try {
                            mutex.withLock {
                                api.account()
                                val result = api.learning("coding/grants", body = JSONObject())
                                grant = result.getJSONObject("grant").put("anchor_elapsed", SystemClock.elapsedRealtime())
                                    .put("anchor_wall", System.currentTimeMillis()).put("boot_count", bootCount())
                                saveCapability(CODING, grant!!); store.saveGrant(CODING, grant!!)
                                store.saveProfile(LearningIdentity.KEY, result.getJSONObject("profile")); mutable.value = store.profiles()
                            }
                        } catch (e: CancellationException) { throw e } catch (e: Exception) { failure(e) }
                        last = SystemClock.elapsedRealtime() // Do not guess time during preparation.
                    }
                    recordingGrant = grant
                    delay(5_000)
                    val end = SystemClock.elapsedRealtime()
                    val start = last
                    last = end
                    recordCoding(grant, start, end)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { failure(e) }
            finally {
                runCatching { recordCoding(recordingGrant, last, stoppedAt.get().takeIf { it > 0 } ?: SystemClock.elapsedRealtime()) }
                flushNow()
            }
        }
    }
    private fun recordCoding(grant: JSONObject?, start: Long, end: Long) {
        val ms = end - start
        grant?.takeIf { serverAt(it, end) <= it.getLong("valid_until_ms") && ms in 1..15_000 }?.let {
            store.add(CODING, it.getString("uuid"), JSONObject().put("uuid", java.util.UUID.randomUUID().toString())
                .put("lesson_uuid", CODING).put("version", 1).put("started_ms", serverAt(it, start))
                .put("ended_ms", serverAt(it, end)).put("watch_ms", ms).put("from_ms", start).put("to_ms", end))
            recorded()
        }
    }
    companion object {
        private const val CODING = "@coding"
        const val JOB_ID = 7312
        @Volatile private var instance: LearningRepository? = null
        fun get(context: Context) = instance ?: synchronized(this) { instance ?: LearningRepository(context).also { instance = it } }
    }
}
