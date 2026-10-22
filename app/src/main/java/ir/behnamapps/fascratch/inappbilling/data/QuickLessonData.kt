package ir.behnamapps.fascratch.inappbilling.data

import android.content.Context
import ir.behnamapps.fascratch.BuildConfig
import ir.behnamapps.fascratch.inappbilling.domain.*
import ir.behnamapps.fascratch.inappbilling.learning.LearningIdentity
import ir.behnamapps.fascratch.inappbilling.presentation.QuickLessonSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** No store SDK, prices, HTML templates, artwork prefetch or independent polling. */
internal class QuickLessonData(context: Context) : QuickLessonSource {
    private val app = context.applicationContext
    private val requests = RequestConnections()
    private val api = CourseApi(app).apply { requestOwner = requests }
    private val cache = CourseCache(app)
    private val downloads by lazy { LessonDownloads(app, api) }
    private val vault by lazy { PurchaseVault(app) }

    override suspend fun cachedCourses() = withContext(Dispatchers.IO) { cache.loadCatalog() }
    override suspend fun courses(): List<Course> {
        val rows = api.courses(if (BuildConfig.BILLING_PROVIDER == "website") "cafebazaar" else BuildConfig.BILLING_PROVIDER)
        withContext(Dispatchers.IO) { cache.saveCatalog(rows) }
        return rows
    }
    override suspend fun cachedLessons(course: Course) = withContext(Dispatchers.IO) { cache.load(course.id)?.second }
    override suspend fun lessons(course: Course): List<Lesson> {
        val rows = api.lessons(course)
        withContext(Dispatchers.IO) { cache.save(course, rows) }
        return rows
    }
    override suspend fun completed(lesson: Lesson) = withContext(Dispatchers.IO) { downloads.completed(lesson) }

    private suspend fun access(course: Course, force: Boolean = false): CourseAccess {
        val profile = LearningIdentity.get(app).profile()
        if (profile?.registered != true && !force) withContext(Dispatchers.IO) { vault.access(course.id) }
            ?.takeIf { it.expiresAtMillis > System.currentTimeMillis() + 60_000 }?.let { return it }
        // Works for a valid guest account too; never launches store inventory or rotates accounts by receipt.
        return api.accountAccess(course, force).also { access ->
            if (profile?.registered != true && profile?.courses?.none { it.uuid == course.id && it.accessSource == "gift" } != false) withContext(Dispatchers.IO) { vault.saveAccess(access) }
        }
    }

    override suspend fun download(course: Course, lesson: Lesson, progress: (Float) -> Unit): java.io.File {
        if (lesson.isPreview) {
            val fresh = api.lessons(course).firstOrNull { it.id == lesson.id && it.isPreview }
                ?: throw CourseFailure("این پیش‌نمایش دیگر در دسترس نیست.")
            if (CoursePolicy.downloadKey(fresh) != CoursePolicy.downloadKey(lesson)) throw CourseFailure("نسخه درس تغییر کرده؛ آموزش را دوباره باز کن.")
            return downloads.download(fresh, null, progress)
        }
        return try { downloads.download(lesson, access(course), progress) }
        catch (failure: CourseFailure) {
            if (failure.status != 401) throw failure
            downloads.download(lesson, access(course, force = true), progress)
        }
    }
    override suspend fun subtitles(course: Course, lesson: Lesson): SubtitleTrack? {
        if (lesson.subtitle == null) return null
        return downloads.subtitles.cached(lesson)
            ?: downloads.subtitles.download(lesson, if (lesson.isPreview) null else access(course))
    }
    override fun close() { requests.close() }
}
