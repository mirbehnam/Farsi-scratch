package ir.behnamapps.fascratch.inappbilling.presentation

import ir.behnamapps.fascratch.inappbilling.data.*
import ir.behnamapps.fascratch.inappbilling.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

data class TrainingState(
    val course: Course? = null, val lessons: List<Lesson> = emptyList(), val busy: Boolean = false,
    val purchased: Boolean = false, val price: String? = null, val message: String? = null,
    val downloadingId: String? = null, val progress: Float = 0f, val downloaded: Set<String> = emptySet()
)

/** UI orchestration only; SDKs, server transport, encrypted storage and files stay outside UI. */
class TrainingController(
    private val scope: CoroutineScope, private val billing: BillingGateway, private val api: CourseApi,
    private val vault: PurchaseVault, private val cache: CourseCache, private val downloads: LessonDownloads
) {
    private val mutable = MutableStateFlow(TrainingState())
    val state = mutable.asStateFlow()
    private val buy = BuyCourse(api, vault)
    private var activeJob: Job? = null

    fun load() = action {
        val cached = withContext(Dispatchers.IO) { cache.load() }
        if (cached != null) show(cached.first, cached.second)
        val course = api.course(if (billing.provider == "website") "cafebazaar" else billing.provider)
        val lessons = api.lessons(course)
        withContext(Dispatchers.IO) { cache.save(course, lessons) }
        show(course, lessons)
        if (billing.provider != "website") {
            val price = withTimeout(20_000) { billing.price(course.sku) }
            mutable.update { it.copy(price = price) }
            try {
                withTimeout(60_000) { buy.execute(course, billing, restoreOnly = true) }
                mutable.update { it.copy(purchased = true, message = null) }
            } catch (error: CourseFailure) {
                if (error.code != "not_owned") throw error
                if (vault.wasVerified(course.id)) {
                    vault.revoke(course.id)
                    mutable.update { it.copy(purchased = false, message = "خرید قبلی در حساب فعلی استور وجود ندارد؛ حساب استور را بررسی کنید.") }
                }
            }
        }
    }

    private fun show(course: Course, lessons: List<Lesson>) {
        mutable.update { it.copy(course = course, lessons = lessons, purchased = vault.wasVerified(course.id),
            downloaded = lessons.filter { lesson -> downloads.completed(lesson) != null }.map { lesson -> lesson.id }.toSet()) }
    }

    fun purchase(restoreOnly: Boolean) = action {
        val course = state.value.course ?: throw CourseFailure("ابتدا اطلاعات دوره را دریافت کنید.")
        withTimeout(180_000) { buy.execute(course, billing, restoreOnly) }
        mutable.update { it.copy(purchased = true, message = null) }
    }

    private suspend fun access(course: Course, force: Boolean = false): CourseAccess {
        val saved = vault.access(course.id)
        if (!force && saved != null && saved.expiresAtMillis > System.currentTimeMillis() + 60_000) return saved
        return withTimeout(60_000) { buy.execute(course, billing, restoreOnly = true) }
    }

    fun download(lesson: Lesson) = action {
        val course = state.value.course ?: throw CourseFailure("دوره در دسترس نیست.")
        if (!vault.wasVerified(course.id)) throw CourseFailure("برای دانلود ابتدا دوره را خریداری یا بازیابی کنید.")
        mutable.update { it.copy(downloadingId = lesson.id, progress = 0f, message = null) }
        val progress: (Float) -> Unit = { value -> mutable.update { it.copy(progress = value) } }
        try {
            downloads.download(lesson, access(course), progress)
        } catch (error: CourseFailure) {
            if (error.status != 401 && error.status != 403) throw error
            downloads.download(lesson, access(course, force = true), progress)
        }
        mutable.update { it.copy(downloaded = it.downloaded + lesson.id, message = "درس با موفقیت دانلود و بررسی شد.") }
    }

    fun delete(lesson: Lesson) = action {
        withContext(Dispatchers.IO) { downloads.delete(lesson) }
        mutable.update { it.copy(downloaded = it.downloaded - lesson.id, message = "فایل دانلودشده حذف شد؛ امکان دانلود مجدد وجود دارد.") }
    }
    fun playable(lesson: Lesson): File? = if (state.value.purchased) downloads.completed(lesson) else null
    fun cancelDownload() { if (state.value.downloadingId != null) activeJob?.cancel() }

    private fun action(block: suspend () -> Unit) {
        if (state.value.busy) return
        mutable.update { it.copy(busy = true, message = null) }
        activeJob = scope.launch {
            try { block() }
            catch (_: TimeoutCancellationException) {
                runCatching { billing.close() }
                mutable.update { it.copy(message = "پاسخ به‌موقع دریافت نشد؛ دوباره تلاش یا خرید را بازیابی کنید.") }
            }
            catch (error: CancellationException) { throw error }
            catch (error: CourseFailure) {
                if (CoursePolicy.revokesAccess(error.code)) {
                    state.value.course?.let { vault.revoke(it.id) }
                    mutable.update { it.copy(purchased = false) }
                }
                mutable.update { it.copy(message = error.userMessage) }
            }
            catch (_: Exception) { mutable.update { it.copy(message = "عملیات کامل نشد؛ اینترنت، فضای ذخیره‌سازی و تنظیمات دوره را بررسی کنید.") } }
            finally { mutable.update { it.copy(busy = false, downloadingId = null, progress = 0f) } }
        }
    }
}
