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
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

data class LearnerCourse(val uuid: String, val title: String, val watchMs: Long, val purchased: Boolean, val enabled: Boolean,
    val accessSource: String? = null, val accessUntilMs: Long? = null) {
    fun canAccess(now: Long = System.currentTimeMillis()): Boolean = enabled &&
        (if (accessSource == "gift") accessUntilMs != null && now < accessUntilMs else purchased)
}
data class LearnerProfile(val uuid: String, val name: String, val level: Int, val watchMs: Long,
    val xpIntoLevel: Double, val xpForNext: Double, val rank: Int?, val enabled: Boolean,
    val nameSet: Boolean = false, val nameChangesLeft: Int = 3, val courses: List<LearnerCourse> = emptyList(), val codingMs: Long = 0, val fullName: String? = null,
    val nameBlocked: Boolean = false, val nameBlockedDays: Int = 0, val nameBlockReason: String? = null,
    val registered: Boolean = false, val username: String? = null, val googleLinked: Boolean = false, val googleLabel: String? = null,
    val supplementary: SupplementaryDetails = SupplementaryDetails(), val revision: Long = 0, val coins: Double? = null,
    val avatarId: String = "default", val avatarImageUrl: String? = null, val avatarImageHash: String? = null, val avatarImageRevision: Long = 0,
    val publicProfile: PublicProfileDetails = PublicProfileDetails()) {
    companion object {
        fun parse(json: JSONObject) = LearnerProfile(json.getString("uuid"), json.getString("display_name"),
            json.getInt("level"), json.getLong("watch_ms"), json.getDouble("xp_into_level"), json.getDouble("xp_for_next_level"),
            if (json.isNull("rank")) null else json.optInt("rank"), json.optBoolean("is_enabled", true),
            json.optJSONObject("name_policy")?.optBoolean("is_set") ?: (json.optString("display_name") != "هنرجو"),
            json.optJSONObject("name_policy")?.optInt("remaining_changes", 3) ?: 3,
            json.optJSONArray("courses")?.let { rows -> (0 until rows.length()).map { i -> rows.getJSONObject(i).let {
                LearnerCourse(it.getString("uuid"), it.getString("title"), it.optLong("watch_ms"), it.optBoolean("purchased"), it.optBoolean("access_enabled"),
                    it.optString("access_source").takeUnless { source -> source.isBlank() || source == "null" },
                    runCatching { java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US).parse(it.getString("access_until"))?.time }.getOrNull())
            } } } ?: emptyList(), json.optLong("coding_ms"), json.optString("full_name").takeUnless { it.isBlank() || it == "null" },
            json.optJSONObject("name_moderation")?.optBoolean("is_blocked") ?: false,
            (json.optJSONObject("name_moderation")?.optInt("remaining_days") ?: 0).coerceAtLeast(0),
            json.optJSONObject("name_moderation")?.optString("reason")?.takeUnless { it.isBlank() || it == "null" },
            json.optBoolean("registered"), json.optJSONObject("sign_in")?.optString("username")?.takeUnless { it.isBlank() || it == "null" },
            json.optJSONObject("sign_in")?.optBoolean("google") ?: false,
            json.optJSONObject("sign_in")?.optString("google_label")?.takeUnless { it.isBlank() || it == "null" },
            json.optJSONObject("supplementary")?.let { extra -> SupplementaryDetails(extra.optBoolean("available"), extra.optInt("version").coerceAtLeast(0),
                extra.optInt("age").takeIf { it in 1..120 }, extra.optString("province").takeIf { it in SupplementaryPolicy.provinces },
                extra.optString("gender").takeIf { it in SupplementaryPolicy.genders },
                extra.optJSONArray("declined_fields")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it in setOf("age", "province", "gender") }.toSet() } ?: emptySet()) } ?: SupplementaryDetails(), json.optLong("revision"),
            json.optDouble("coins").takeIf { json.optBoolean("wallet_available") && it.isFinite() && it >= 0 }, json.optString("avatar_id", "default"),
            json.optString("avatar_image_url").takeIf { it.isNotBlank() && it != "null" }, json.optString("avatar_image_sha256").takeIf { AvatarImages.validHash(it) }, json.optLong("avatar_image_revision"),
            PublicProfileDetails.parse(json.optJSONObject("public_profile")))
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
    val identityChanges = identity.changes
    fun requiresRecovery(): Boolean = identity.requiresRecovery()
    private val preparing = mutableSetOf<String>()
    private val knownCourses = java.util.concurrent.ConcurrentHashMap<String, Course>()
    private val lastPrepare = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private var foregroundSync: Job? = null
    private var lastSyncAttempt = -10_000L
    private var lastProfileAttempt = -10_000L
    private var missionVisitOwner: String? = null
    private var nextMissionVisit = 0L
    private var avatarCatalogOwner: String? = null
    private var nextAvatarCatalog = 0L
    private var avatarPrefetch: Job? = null
    private val mutableMissions = MutableStateFlow<MissionBoard?>(null)
    val missions = mutableMissions.asStateFlow()
    private val mutableMissionReward = kotlinx.coroutines.flow.MutableSharedFlow<Pair<String, Int>>(extraBufferCapacity = 1)
    val missionRewards = mutableMissionReward.asSharedFlow()
    private var reviewRewardJob: Job? = null
    private var reviewRewardOwner: String? = null
    private var codingJob: Job? = null
    private var sessionMonitor: Job? = null
    @Synchronized private fun startSessionMonitor() {
        if (sessionMonitor?.isActive == true) return
        sessionMonitor = scope.launch {
            while (isActive) {
                val process = android.app.ActivityManager.RunningAppProcessInfo()
                android.app.ActivityManager.getMyMemoryState(process)
                if (process.importance > android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) break
                visit(); delay(15_000)
            }
        }
    }
    private var codingStop: java.util.concurrent.atomic.AtomicLong? = null
    private val mutableStatus = MutableStateFlow<String?>(null)
    val status = mutableStatus.asStateFlow()
    private fun failure(error: Exception) {
        mutableStatus.value = if (requiresRecovery()) "نشست حساب پایان یافته؛ دوباره وارد حساب شو یا خرید را بازیابی کن."
            else if (error is CourseFailure && error.status == 401)
                "مجوز ثبت پیشرفت معتبر نیست؛ حساب را بررسی کن. (HTTP 401${error.operation.takeIf { it.isNotBlank() }?.let { " — $it" }.orEmpty()})"
            else if (error is CourseFailure) "ثبت پیشرفت: ${error.message} (HTTP ${error.status})"
            else "ثبت پیشرفت انجام نشد؛ اتصال را بررسی کنید. اطلاعات ذخیره‌شده برای ارسال مجدد حفظ می‌شود."
    }
    init {
        // One local timer per nearest gift expiry, not a network poll or a per-second loop.
        scope.launch {
            mutable.collectLatest { snapshot ->
                val expiry = snapshot.values.flatMap { it.courses }.filter { it.enabled && it.accessSource == "gift" }
                    .mapNotNull { it.accessUntilMs }.minOrNull() ?: return@collectLatest
                delay((expiry - System.currentTimeMillis()).coerceAtLeast(1L))
                mutex.withLock {
                    mutable.value = mutable.value.mapValues { (_, profile) -> profile.copy(courses = profile.courses.map { course ->
                        if (course.accessSource == "gift" && !course.canAccess()) course.copy(enabled = false) else course
                    }) }
                }
            }
        }
        scope.launch { identity.changes.collect { mutex.withLock { mutable.value = store.profiles() } } }
        scope.launch { mutex.withLock { mutable.value = store.profiles() }; if (store.pending().isNotEmpty()) schedule() }
        // Only monitor while an Activity is resumed. Offline/network failures retain cached access.
        (app as? android.app.Application)?.registerActivityLifecycleCallbacks(object : android.app.Application.ActivityLifecycleCallbacks {
            private var resumed = 0
            override fun onActivityResumed(activity: android.app.Activity) {
                if (++resumed == 1) startSessionMonitor()
            }
            override fun onActivityPaused(activity: android.app.Activity) { if (--resumed <= 0) { resumed = 0; sessionMonitor?.cancel(); sessionMonitor = null } }
            override fun onActivityCreated(activity: android.app.Activity, state: android.os.Bundle?) {}
            override fun onActivityStarted(activity: android.app.Activity) {}
            override fun onActivityStopped(activity: android.app.Activity) {}
            override fun onActivitySaveInstanceState(activity: android.app.Activity, state: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {}
        })
    }
    fun visit() { startSessionMonitor(); scope.launch {
        mutex.withLock {
            if (SystemClock.elapsedRealtime() - lastProfileAttempt >= 10_000) {
                lastProfileAttempt = SystemClock.elapsedRealtime()
                try {
                    api.account(); mutableStatus.value = null
                    val uuid = identity.profile()?.uuid
                    if (uuid != null && (avatarCatalogOwner != uuid || SystemClock.elapsedRealtime() >= nextAvatarCatalog)) {
                        avatarCatalogOwner = uuid; nextAvatarCatalog = SystemClock.elapsedRealtime() + 300_000
                        scope.launch {
                            try { refreshMarketplace(uuid) }
                            catch (e: CancellationException) { throw e }
                            catch (_: Exception) { /* Cached artwork and courses remain usable offline. */ }
                        }
                    }
                    if (uuid != null && (uuid != missionVisitOwner || SystemClock.elapsedRealtime() >= nextMissionVisit)) {
                        val secret = identity.secret()
                        // A failed/older server does not cause a request every fifteen seconds.
                        nextMissionVisit = SystemClock.elapsedRealtime() + 300_000
                        missionVisitOwner = uuid
                        try {
                            val result = api.missions("visit", secret, JSONObject())
                            if (identity.secret() == secret && identity.profile()?.uuid == uuid) {
                                acceptMissions(result, uuid)
                                val wait = (result.optLong("reset_at_ms") - result.optLong("server_time_ms")).coerceIn(1_000, 86400000)
                                nextMissionVisit = SystemClock.elapsedRealtime() + wait
                            }
                        } catch (e: CancellationException) { throw e } catch (_: Exception) { /* Never block opening Home. */ }
                    }
                } catch (e: CancellationException) { throw e } catch (e: Exception) { failure(e) }
            }
            mutable.value = store.profiles()
        }
        identity.profile()?.uuid?.let { uuid ->
            try { retryRewardedAd(uuid) } catch (e: CancellationException) { throw e } catch (_: Exception) { }
        }
        if (store.pending().isNotEmpty()) flushNow()
    } }
    fun refreshProfile() { visit(); flushNow() }

    private suspend fun access(course: Course, force: Boolean = false): String {
        if (requiresRecovery()) throw CourseFailure("نشست قبلی پایان یافته؛ وارد حساب شو یا خرید را بازیابی کن.")
        if (identity.profile()?.registered == true || identity.profile()?.courses?.any { it.uuid == course.id && it.accessSource == "gift" && it.canAccess() } == true) return api.accountAccess(course, force).token
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
        if (requiresRecovery()) return false
        val profile = identity.profile()
        if (profile?.courses?.any { it.uuid == id && it.canAccess() } == true) return true
        return if (profile?.registered == true) false
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
    suspend fun redeemGift(code: String): JSONObject = withContext(Dispatchers.IO) {
        mutex.withLock {
            api.account()
            val session = identity.secret()
            val result = api.redeemGift(code, session)
            require(identity.accept(JSONObject().put("profile", result.getJSONObject("profile")), session)) { "حساب تغییر کرده؛ دوباره بررسی کن." }
            mutable.value = store.profiles()
            result.optString("course_uuid").takeIf { it.isNotBlank() && it != "null" }?.let { courseId ->
                knownCourses[courseId]?.let { prepare(it, forceGrant = true) }
            }
            result
        }
    }
    suspend fun authOptions(): JSONObject = api.auth("options")
    internal suspend fun leaderboard(expectedUuid: String): LeaderboardBoard = withContext(Dispatchers.IO) {
        require(identity.profile()?.uuid == expectedUuid)
        val secret = identity.secret()
        val json = api.leaderboard("", secret)
        require(identity.secret() == secret && identity.profile()?.uuid == expectedUuid && json.optString("viewer_uuid") == expectedUuid)
        LeaderboardBoard.parse(json)
    }
    internal suspend fun showcase(expectedUuid: String, targetUuid: String): LearnerShowcase = withContext(Dispatchers.IO) {
        require(identity.profile()?.uuid == expectedUuid && targetUuid.matches(Regex("[a-fA-F0-9-]{36}")))
        val secret = identity.secret()
        val json = api.leaderboard(targetUuid, secret)
        require(identity.secret() == secret && identity.profile()?.uuid == expectedUuid)
        LearnerShowcase.parse(json).also { require(it.learner.uuid == targetUuid) }
    }
    private val mutableMarketplace = MutableStateFlow<AvatarCatalog?>(null)
    internal val marketplace = mutableMarketplace.asStateFlow()

    private fun acceptMarketplace(json: JSONObject, uuid: String, secret: String) {
        require(identity.secret() == secret && identity.profile()?.uuid == uuid && json.optString("uuid") == uuid) {
            "حساب تغییر کرده؛ بازارچه را دوباره باز کن."
        }
        json.optJSONObject("profile")?.let { official ->
            require(official.getString("uuid") == uuid)
            require(identity.accept(JSONObject().put("profile", official), secret))
            mutable.value = store.profiles()
        }
        store.saveGrant("@avatar-market", json)
        mutableMarketplace.value = AvatarCatalog.parse(json)
        val items = mutableMarketplace.value?.items.orEmpty()
        avatarPrefetch?.cancel()
        avatarPrefetch = scope.launch {
            // Sequential prefetch limits radio/CPU pressure; individual previews share per-hash locks.
            for (item in items) { ensureActive(); AvatarImages.load(app, item.imageUrl, item.imageHash) }
        }
    }

    suspend fun refreshMarketplace(uuid: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(identity.profile()?.uuid == uuid)
            store.grant("@avatar-market")?.takeIf { it.optString("uuid") == uuid }?.let { mutableMarketplace.value = AvatarCatalog.parse(it) }
            val secret = identity.secret()
            acceptMarketplace(api.marketplace("", secret), uuid, secret)
        }
    }

    suspend fun buyAvatar(uuid: String, avatar: String, operation: String, expectedPrice: Int): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(identity.profile()?.uuid == uuid)
            val secret = identity.secret()
            val result = api.marketplace("$avatar/buy", secret, JSONObject().put("operation_uuid", operation).put("expected_price_coins", expectedPrice))
            acceptMarketplace(result.getJSONObject("catalog"), uuid, secret)
            result.optBoolean("purchased")
        }
    }

    suspend fun equipAvatar(uuid: String, avatar: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(identity.profile()?.uuid == uuid)
            val secret = identity.secret()
            acceptMarketplace(api.marketplace("$avatar/equip", secret, JSONObject()), uuid, secret)
        }
    }

    private fun acceptMissions(json: JSONObject, uuid: String) {
        require(json.optString("uuid") == uuid)
        store.saveGrant("@missions", json)
        mutableMissions.value = MissionBoard.parse(json)
        scheduleReviewReward(uuid, mutableMissions.value!!)
    }
    suspend fun refreshMissions(uuid: String) = withContext(Dispatchers.IO) {
        try { retryRewardedAd(uuid) } catch (e: CancellationException) { throw e } catch (_: Exception) { }
        mutex.withLock {
            require(identity.profile()?.uuid == uuid) { "حساب تغییر کرده؛ صفحه را دوباره باز کن." }
            store.grant("@missions")?.takeIf { it.optString("uuid") == uuid }?.let { mutableMissions.value = MissionBoard.parse(it) }
        }
        // Flush the existing bounded outbox; never manufacture time to complete a mission.
        try { sync() } catch (e: CancellationException) { throw e } catch (_: Exception) { }
        mutex.withLock {
            require(identity.profile()?.uuid == uuid) { "حساب تغییر کرده؛ صفحه را دوباره باز کن." }
            val secret = identity.secret()
            var result = api.missions("", secret)
            require(identity.secret() == secret && identity.profile()?.uuid == uuid)
            val project = MissionBoard.parse(result).items.firstOrNull { it.id == "saved_project" }
            if (project?.status == "active" && MissionActions.hasSavedProject(app.filesDir)) {
                result = api.missions("actions/project-saved", secret, JSONObject().put("has_project", true))
                require(identity.secret() == secret && identity.profile()?.uuid == uuid)
            }
            acceptMissions(result, uuid)
        }
    }
    fun savedProjectChanged() { scope.launch {
        mutex.withLock {
            val uuid = identity.profile()?.uuid ?: return@withLock
            val known = mutableMissions.value?.takeIf { it.uuid == uuid }
            if (known?.items?.any { it.id == "saved_project" && it.status != "active" } == true) return@withLock
            val secret = identity.secret()
            if (!MissionActions.hasSavedProject(app.filesDir)) return@withLock
            try {
                val result = api.missions("actions/project-saved", secret, JSONObject().put("has_project", true))
                if (identity.secret() == secret && identity.profile()?.uuid == uuid) acceptMissions(result, uuid)
            } catch (e: CancellationException) { throw e } catch (_: Exception) { /* Rechecked on next mission visit. */ }
        }
    } }
    suspend fun startStoreReview(uuid: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(identity.profile()?.uuid == uuid)
            val secret = identity.secret()
            val result = api.missions("actions/review-start", secret, JSONObject().put("flavor", BuildConfig.FLAVOR))
            require(identity.secret() == secret && identity.profile()?.uuid == uuid)
            acceptMissions(result, uuid)
        }
    }
    private fun scheduleReviewReward(uuid: String, board: MissionBoard) {
        val item = board.items.firstOrNull { it.id == "store_review" && it.status != "claimed" && it.action != "review" } ?: return
        if (reviewRewardJob?.isActive == true && reviewRewardOwner == uuid) return
        reviewRewardJob?.cancel()
        reviewRewardOwner = uuid
        reviewRewardJob = scope.launch {
            delay(item.retryAfterMs + 150)
            if (identity.profile()?.uuid != uuid) return@launch
            try {
                if (claimMission(uuid, item)) mutableMissionReward.emit(uuid to item.rewardCoins)
            } catch (e: CancellationException) { throw e } catch (_: Exception) { /* Recover/retry from server state next time. */ }
        }
    }
    suspend fun claimMission(uuid: String, mission: MissionItem): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(identity.profile()?.uuid == uuid) { "حساب تغییر کرده؛ صفحه را دوباره باز کن." }
            val secret = identity.secret()
            val result = api.missions("${mission.id}/claim", secret, JSONObject().put("period", mission.period))
            require(identity.secret() == secret && identity.profile()?.uuid == uuid)
            val official = result.getJSONObject("profile")
            require(official.getString("uuid") == uuid)
            require(identity.accept(JSONObject().put("profile", official), secret)) { "حساب تغییر کرده؛ صفحه را دوباره باز کن." }
            mutable.value = store.profiles()
            acceptMissions(result.getJSONObject("missions"), uuid)
            result.optBoolean("awarded")
        }
    }

    internal suspend fun reserveRewardedAd(uuid: String, operation: String): RewardedAdTicket = withContext(Dispatchers.IO) {
        retryRewardedAd(uuid) // A previous pending receipt must be settled before another can replace it.
        mutex.withLock {
            require(identity.profile()?.uuid == uuid)
            val secret = identity.secret()
            val result = api.missions("ads/reserve", secret, JSONObject().put("uuid", operation))
            require(identity.secret() == secret && identity.profile()?.uuid == uuid)
            RewardedAdTicket.parse(result)
        }
    }

    internal suspend fun startRewardedAd(uuid: String, ticket: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(identity.profile()?.uuid == uuid)
            val secret = identity.secret()
            api.missions("ads/start", secret, JSONObject().put("uuid", ticket))
            require(identity.secret() == secret && identity.profile()?.uuid == uuid)
        }
    }

    internal suspend fun finishRewardedAd(uuid: String, ticket: String, rewarded: Boolean) = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(identity.profile()?.uuid == uuid)
            // Persist BEFORE networking, encrypted in the existing private learning store.
            store.saveGrant("@ad-result:$uuid", JSONObject().put("uuid", ticket).put("rewarded", rewarded).put("owner", uuid))
        }
        retryRewardedAd(uuid)
    }

    private suspend fun retryRewardedAd(uuid: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (identity.profile()?.uuid != uuid) return@withLock
            val pending = store.grant("@ad-result:$uuid")?.takeIf { it.optString("owner") == uuid && it.has("uuid") } ?: return@withLock
            val secret = identity.secret()
            try {
                val result = api.missions("ads/" + (if (pending.getBoolean("rewarded")) "complete" else "cancel"), secret,
                    JSONObject().put("uuid", pending.getString("uuid")).put("is_rewarded", true))
                require(identity.secret() == secret && identity.profile()?.uuid == uuid)
                val official = result.getJSONObject("profile")
                require(identity.accept(JSONObject().put("profile", official), secret))
                mutable.value = store.profiles()
                acceptMissions(result.getJSONObject("missions"), uuid)
                // Keep the durable receipt until the server confirms callback or timeout fallback.
                if (result.optBoolean("pending")) throw RewardedAdPending()
                store.saveGrant("@ad-result:$uuid", JSONObject())
                if (result.optBoolean("confirmed") || result.optBoolean("awarded")) mutableMissionReward.emit(uuid to result.optInt("reward_coins"))
            } catch (e: CancellationException) { throw e }
            catch (e: CourseFailure) {
                if (e.code != "ip_blocked" && e.status in setOf(403, 404, 410, 422)) store.saveGrant("@ad-result:$uuid", JSONObject())
                throw e
            }
        }
    }
    internal suspend fun savePublicProfile(expectedUuid: String, version: Int, bio: String, links: Map<String, String>, confirmedCost: Int) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (identity.profile()?.uuid != expectedUuid) throw CourseFailure("حساب تغییر کرده؛ پروفایل را دوباره باز کن.", 409)
            val secret = identity.secret()
            val body = JSONObject().put("version", version).put("bio", bio).put("social_links", JSONObject(links)).put("confirmed_cost", confirmedCost)
            val result = api.savePublicProfile(body, secret)
            if (identity.secret() != secret || result.optString("uuid") != expectedUuid) throw CourseFailure("حساب تغییر کرده؛ پروفایل را دوباره باز کن.", 409)
            store.saveProfile(LearningIdentity.KEY, result); mutable.value = store.profiles()
        }
    }

    suspend fun saveSupplementary(expectedUuid: String, details: SupplementaryDetails) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (identity.profile()?.uuid != expectedUuid) throw CourseFailure("حساب تغییر کرده؛ پروفایل را دوباره باز کن.", 409)
            val secret = identity.secret()
            val body = JSONObject().put("version", details.version).put("age", details.age ?: JSONObject.NULL)
                .put("province", details.province ?: JSONObject.NULL).put("gender", details.gender ?: JSONObject.NULL)
                .put("declined_fields", JSONArray(details.declinedFields.toList()))
            val result = api.saveSupplementary(body, secret)
            if (identity.secret() != secret || result.optString("uuid") != expectedUuid) throw CourseFailure("حساب تغییر کرده؛ پروفایل را دوباره باز کن.", 409)
            store.saveProfile(LearningIdentity.KEY, result); mutable.value = store.profiles()
        }
    }
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
    suspend fun logout(expectedUuid: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(identity.profile()?.uuid == expectedUuid) { "حساب تغییر کرده؛ صفحه را دوباره باز کن." }
            val session = identity.secret()
            // Logout remains usable offline. Server revocation is best-effort; local access always closes.
            try { withTimeoutOrNull(5_000) { api.logout(session) } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Existing server session expires or is superseded at the next login. */ }
            if (identity.invalidateEndedSession(session)) {
                mutable.value = store.profiles(); mutableStatus.value = null
            }
        }
        visit()
    }
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
                    val interactive = app.getSystemService(android.os.PowerManager::class.java)?.isInteractive == true
                    val locked = app.getSystemService(android.app.KeyguardManager::class.java)?.isKeyguardLocked == true
                    if (interactive && !locked) recordCoding(grant, start, end)
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
