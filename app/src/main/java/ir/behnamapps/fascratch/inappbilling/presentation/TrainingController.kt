package ir.behnamapps.fascratch.inappbilling.presentation

import ir.behnamapps.fascratch.inappbilling.data.*
import ir.behnamapps.fascratch.inappbilling.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.text.NumberFormat
import java.util.Locale

data class TrainingState(
    val course: Course? = null, val lessons: List<Lesson> = emptyList(), val busy: Boolean = false,
    val purchased: Boolean = false, val price: String? = null, val message: String? = null,
    val downloadingId: String? = null, val progress: Float = 0f, val downloaded: Set<String> = emptySet(),
    val courses: List<Course> = emptyList(), val purchasedIds: Set<String> = emptySet(), val prices: Map<String, String> = emptyMap(),
    val loadingPrices: Set<String> = emptySet(), val priceErrors: Set<String> = emptySet(),
    val catalogLayout: CatalogLayout? = null, val catalogCacheLoaded: Boolean = true
)

/** Catalog and per-course access stay separate. Store outages never block free previews. */
class TrainingController(
    private val scope: CoroutineScope, private val billing: BillingGateway, private val api: CourseApi,
    private val vault: PurchaseVault, private val cache: CourseCache, private val downloads: LessonDownloads,
    val learning: ir.behnamapps.fascratch.inappbilling.learning.LearningRepository? = null
) {
    private val mutable = MutableStateFlow(TrainingState(catalogCacheLoaded = false))
    val state = mutable.asStateFlow()
    private val buy = BuyCourse(api, vault)
    private val storeMutex = Mutex()
    private fun provider() = if (billing.provider == "website") "cafebazaar" else billing.provider
    private fun priceLabel(amount: Long?) = amount?.let { NumberFormat.getIntegerInstance(Locale.US).format(it) + " تومان" }
    private val priceRefresher = CoursePriceRefresher(scope, { course ->
        val fresh = api.coursePricing(course, provider())
        mutable.update { state -> state.copy(courses = state.courses.map { if (it.id == fresh.id && it.sku == fresh.sku) fresh else it },
            course = if (state.course?.id == fresh.id && state.course.sku == fresh.sku) fresh else state.course) }
        priceLabel(fresh.serverPriceToman)
    },
        onLoading = { course -> mutable.update { it.copy(prices = it.prices - course.id, loadingPrices = it.loadingPrices + course.id,
            priceErrors = it.priceErrors - course.id, price = if (it.course?.id == course.id) null else it.price) } },
        onResult = { course, price -> mutable.update {
            if (it.courses.none { c -> c.id == course.id && c.sku == course.sku }) it
            else it.copy(prices = if (price != null) it.prices + (course.id to price) else it.prices - course.id,
                loadingPrices = it.loadingPrices - course.id, priceErrors = if (price == null) it.priceErrors + course.id else it.priceErrors - course.id,
                price = if (it.course?.id == course.id) price else it.price)
        } })
    private var activeJob: Job? = null
    private var storeJob: Job? = null
    private var pauseStoreSync = false
    private var layoutJob: Job? = null
    private val layoutCacheMutex = Mutex()
    private var layoutGeneration = 0

    fun load() = action {
        layoutJob?.cancel()
        ++layoutGeneration
        // Load both local snapshots before starting either network request. Refreshes keep
        // the current layout visible; a timeout is not an instruction to disable HTML.
        val cached = withContext(Dispatchers.IO) {
            layoutCacheMutex.withLock { cache.loadCatalog() to cache.loadCatalogLayout() }
        }
        mutable.update { it.copy(catalogLayout = it.catalogLayout ?: cached.second) }
        if (cached.first.isNotEmpty()) showCatalog(cached.first)
        mutable.update { it.copy(catalogCacheLoaded = true) }
        refreshCatalogLayout()
        val courses = api.courses(if (billing.provider == "website") "cafebazaar" else billing.provider)
        withContext(Dispatchers.IO) { cache.saveCatalog(courses) }
        showCatalog(courses)
        state.value.course?.let { selected ->
            courses.firstOrNull { it.id == selected.id }?.let { open(it) }
                ?: mutable.update { it.copy(course = null, lessons = emptyList(), downloaded = emptySet()) }
        }
        syncStores(courses)
    }

    private fun refreshCatalogLayout() {
        layoutJob?.cancel()
        val generation = ++layoutGeneration
        layoutJob = scope.launch {
            val update = try { withTimeout(1800) { api.catalogLayout() } }
                catch (_: TimeoutCancellationException) { CatalogLayoutUpdate.Unavailable }
            withContext(Dispatchers.IO) {
                layoutCacheMutex.withLock {
                    if (generation != layoutGeneration) return@withLock
                    mutable.update { it.copy(catalogLayout = CatalogLayoutPolicy.resolve(it.catalogLayout, update)) }
                    if (update == CatalogLayoutUpdate.Native) runCatching { cache.clearCatalogLayout() }
                }
            }
        }
    }

    fun catalogReady(layout: CatalogLayout) {
        scope.launch(Dispatchers.IO) {
            layoutCacheMutex.withLock {
                // Serialize with server-native removal: a late readiness callback must not
                // write a revoked/stale template back into persistent storage.
                if (mutable.value.catalogLayout?.sha256 == layout.sha256) runCatching {
                    if (cache.loadCatalogLayout()?.sha256 != layout.sha256) cache.saveCatalogLayout(layout)
                }
            }
        }
    }

    fun catalogFailed(layout: CatalogLayout) {
        scope.launch(Dispatchers.IO) {
            layoutCacheMutex.withLock {
                if (mutable.value.catalogLayout?.sha256 == layout.sha256) mutable.update { it.copy(catalogLayout = null) }
                runCatching {
                    if (cache.loadCatalogLayout()?.sha256 == layout.sha256) cache.clearCatalogLayout()
                }
            }
        }
    }

    private fun showCatalog(courses: List<Course>) {
        mutable.update { it.copy(courses = courses, purchasedIds = courses.filter { c -> vault.wasVerified(c.id) }.map { c -> c.id }.toSet(),
            prices = courses.mapNotNull { c -> priceLabel(c.serverPriceToman)?.let { label -> c.id to label } }.toMap(),
            priceErrors = courses.filter { c -> c.serverPriceToman == null }.map { c -> c.id }.toSet(), loadingPrices = emptySet()) }
    }

    private fun updateAccess(course: Course, enabled: Boolean) {
        if (enabled) learning?.prepare(course)
        mutable.update { it.copy(purchasedIds = if (enabled) it.purchasedIds + course.id else it.purchasedIds - course.id,
            purchased = if (it.course?.id == course.id) enabled else it.purchased) }
    }

    // Serial SDK access prevents competing inventory/payment callbacks. No global UI/download lock.
    private fun syncStores(courses: List<Course>) {
        // The catalog already contains fresh server prices; do not request every course twice.
        if (billing.provider == "website") return
        // Re-render/refresh must not interrupt a pending Myket SDK inventory operation.
        if (storeJob?.isActive == true) return
        pauseStoreSync = false
        storeJob = scope.launch {
            for (course in courses) {
                if (pauseStoreSync) break
                try {
                    storeMutex.withLock {
                        withTimeout(60_000) { buy.execute(course, billing, restoreOnly = true) }
                        updateAccess(course, true)
                    }
                } catch (_: TimeoutCancellationException) { runCatching { billing.close() } }
                catch (error: CancellationException) { throw error }
                catch (error: CourseFailure) {
                    if (CoursePolicy.revokesAccess(error.code)) {
                        vault.revoke(course.id); updateAccess(course, false)
                        if (error.code != "not_owned" && state.value.course?.id == course.id) mutable.update { it.copy(message = error.userMessage) }
                    }
                } catch (_: Exception) { /* Retain prior verified offline access on transient failure. */ }
            }
        }
    }

    fun select(course: Course) = action(course.id) {
        priceRefresher.refresh(listOf(course))
        open(course)
    }

    fun refreshPrices() {
        learning?.refreshProfile()
        priceRefresher.refresh(state.value.course?.let { listOf(it) } ?: state.value.courses)
    }

    private suspend fun open(course: Course) {
        val cached = withContext(Dispatchers.IO) { cache.load(course.id) }
        show(course, cached?.second ?: emptyList())
        val lessons = api.lessons(course)
        withContext(Dispatchers.IO) { cache.save(course, lessons) }
        show(course, lessons)
    }

    private fun show(course: Course, lessons: List<Lesson>) {
        learning?.prepare(course)
        mutable.update { it.copy(course = course, lessons = lessons, purchased = vault.wasVerified(course.id), price = it.prices[course.id],
            downloaded = lessons.filter { lesson -> downloads.completed(lesson) != null }.map { lesson -> lesson.id }.toSet()) }
    }

    fun backToCatalog(): Boolean {
        if (state.value.course == null) return false
        if (state.value.busy) return true
        mutable.update { it.copy(course = null, lessons = emptyList(), downloaded = emptySet(), message = null) }
        refreshCatalogLayout()
        learning?.refreshProfile()
        return true
    }

    fun purchase(restoreOnly: Boolean, target: Course? = state.value.course) {
        val course = target ?: return
        action(course.id) {
            if (!restoreOnly) {
                val fresh = priceLabel(api.coursePrice(course, provider()))
                    ?: throw CourseFailure("قیمت این دوره در سرور تنظیم نشده است.")
                mutable.update { it.copy(prices = it.prices + (course.id to fresh), price = if (it.course?.id == course.id) fresh else it.price) }
            }
            // Let the current SDK inventory callback finish; cancelling it can leave Myket busy.
            pauseStoreSync = true
            storeMutex.withLock { withTimeout(180_000) { buy.execute(course, billing, restoreOnly) } }
            updateAccess(course, true)
            open(course)
        }
    }

    private suspend fun access(course: Course, force: Boolean = false): CourseAccess {
        val saved = vault.access(course.id)
        if (!force && saved != null && saved.expiresAtMillis > System.currentTimeMillis() + 60_000) return saved
        return storeMutex.withLock { withTimeout(60_000) { buy.execute(course, billing, restoreOnly = true) } }
    }

    fun download(lesson: Lesson) = action(lesson.courseId) {
        val course = state.value.course ?: throw CourseFailure("دوره در دسترس نیست.")
        require(lesson.courseId == course.id && state.value.lessons.any { it.id == lesson.id })
        if (!CoursePolicy.canLearn(lesson, vault.wasVerified(course.id))) throw CourseFailure("برای دانلود ابتدا دوره را خریداری یا بازیابی کنید.")
        mutable.update { it.copy(downloadingId = lesson.id, progress = 0f, message = null) }
        val progress: (Float) -> Unit = { value -> mutable.update { it.copy(progress = value) } }
        if (lesson.isPreview) {
            val fresh = api.lessons(course).firstOrNull { it.id == lesson.id && it.isPreview }
                ?: throw CourseFailure("این پیش‌نمایش دیگر رایگان یا در دسترس نیست.")
            if (CoursePolicy.downloadKey(fresh) != CoursePolicy.downloadKey(lesson)) throw CourseFailure("نسخه درس تغییر کرده؛ فهرست را به‌روز کنید.")
            downloads.download(fresh, null, progress)
        } else try {
            downloads.download(lesson, access(course), progress)
        } catch (error: CourseFailure) {
            if (error.status != 401 && error.status != 403) throw error
            downloads.download(lesson, access(course, force = true), progress)
        }
        mutable.update { it.copy(downloaded = it.downloaded + lesson.id, message = null) }
    }

    fun delete(lesson: Lesson) = action(lesson.courseId) {
        withContext(Dispatchers.IO) { downloads.delete(lesson) }
        mutable.update { it.copy(downloaded = it.downloaded - lesson.id, message = null) }
    }
    fun playable(lesson: Lesson): File? = if (lesson.courseId == state.value.course?.id &&
        CoursePolicy.canLearn(lesson, state.value.purchased)) downloads.completed(lesson) else null
    fun watchTracker(lesson: Lesson) = state.value.course?.let { learning?.tracker(it, lesson) }
    fun nextDownloadedLesson(currentId: String): Lesson? = LessonPlayerPolicy.next(state.value.lessons, currentId)
        ?.takeIf { playable(it) != null }
    fun cancelDownload() { if (state.value.downloadingId != null) activeJob?.cancel() }

    private fun action(courseId: String? = state.value.course?.id, block: suspend () -> Unit) {
        if (state.value.busy) return
        mutable.update { it.copy(busy = true, message = null) }
        activeJob = scope.launch {
            try { block() }
            catch (_: TimeoutCancellationException) {
                runCatching { billing.close() }
                mutable.update { it.copy(message = "پاسخ به‌موقع دریافت نشد؛ دوباره تلاش کنید.") }
            }
            catch (error: CancellationException) { throw error }
            catch (error: CourseFailure) {
                if (CoursePolicy.revokesAccess(error.code) && courseId != null) {
                    vault.revoke(courseId)
                    mutable.update { it.copy(purchasedIds = it.purchasedIds - courseId, purchased = if (it.course?.id == courseId) false else it.purchased) }
                }
                mutable.update { it.copy(message = error.userMessage) }
            }
            catch (_: Exception) { mutable.update { it.copy(message = "عملیات کامل نشد؛ اینترنت، فضای ذخیره‌سازی و تنظیمات دوره را بررسی کنید.") } }
            finally {
                mutable.update { it.copy(busy = false, downloadingId = null, progress = 0f) }
                learning?.refreshProfile()
            }
        }
    }
}
