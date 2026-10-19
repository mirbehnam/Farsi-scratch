package ir.behnamapps.fascratch.inappbilling.presentation

import ir.behnamapps.fascratch.inappbilling.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

internal interface QuickLessonSource : AutoCloseable {
    suspend fun cachedCourses(): List<Course>
    suspend fun courses(): List<Course>
    suspend fun cachedLessons(course: Course): List<Lesson>?
    suspend fun lessons(course: Course): List<Lesson>
    suspend fun completed(lesson: Lesson): File?
    suspend fun download(course: Course, lesson: Lesson, progress: (Float) -> Unit): File
    suspend fun subtitles(course: Course, lesson: Lesson): SubtitleTrack?
}

internal data class QuickPlayingLesson(val lesson: Lesson, val file: File)
internal data class QuickLessonsState(
    val visible: Boolean = true, val loading: Boolean = false, val courses: List<Course> = emptyList(),
    val course: Course? = null, val lessons: List<Lesson> = emptyList(), val downloaded: Set<String> = emptySet(),
    val downloadingId: String? = null, val progress: Float = 0f, val message: String? = null,
    val playing: QuickPlayingLesson? = null, val checkpoint: LessonPlaybackCheckpoint = LessonPlaybackCheckpoint()
)

/** Constructed only by a book click. Owns neither billing SDKs nor the Scratch WebView. */
internal class QuickLessonsController(
    private val sourceFactory: () -> QuickLessonSource,
    private val owns: (String) -> Boolean,
    private val prepareWatch: (Course) -> Unit,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) : AutoCloseable {
    private val lifetime = SupervisorJob()
    private val scope = CoroutineScope(lifetime + dispatcher)
    private val mutable = MutableStateFlow(QuickLessonsState())
    val state = mutable.asStateFlow()
    private var task: Job? = null
    private var generation = 0L
    @Volatile private var closed = false
    private var source: QuickLessonSource? = null
    private val sourceLock = Any()
    private fun data(): QuickLessonSource = synchronized(sourceLock) {
        if (closed) throw CancellationException("Quick lessons closed")
        source ?: sourceFactory().also { source = it }
    }

    private fun stopWork() {
        generation++
        task?.cancel(); task = null
        synchronized(sourceLock) { source.also { source = null } }?.close()
    }

    fun load() = action {
        val cached = data().cachedCourses().filter { owns(it.id) }
        if (cached.isNotEmpty()) {
            mutable.update { it.copy(courses = cached) }
            val first = cached.firstOrNull { it.id == state.value.course?.id } ?: cached.first()
            data().cachedLessons(first)?.let { showCourse(first, it) }
        }
        val fresh = data().courses().filter { owns(it.id) }
        mutable.update { it.copy(courses = fresh) }
        val selected = fresh.firstOrNull { it.id == state.value.course?.id } ?: fresh.firstOrNull()
        if (selected == null) {
            mutable.update { it.copy(course = null, lessons = emptyList(), downloaded = emptySet(), message = "دورهٔ خریداری‌شده‌ای برای نمایش پیدا نشد.") }
        } else {
            if (state.value.course?.id != selected.id) data().cachedLessons(selected)?.let { showCourse(selected, it) }
            showCourse(selected, data().lessons(selected))
        }
    }

    fun select(course: Course) = action {
        check(owns(course.id))
        showCourse(course, data().cachedLessons(course).orEmpty())
        showCourse(course, data().lessons(course))
    }

    private suspend fun showCourse(course: Course, lessons: List<Lesson>) {
        currentCoroutineContext().ensureActive()
        if (!owns(course.id)) throw CourseFailure("دسترسی این دوره فعال نیست.")
        val downloaded = lessons.filter { data().completed(it) != null }.map { it.id }.toSet()
        mutable.update { it.copy(course = course, lessons = lessons, downloaded = downloaded, playing = null, checkpoint = LessonPlaybackCheckpoint()) }
    }

    fun play(lesson: Lesson) = action {
        val course = state.value.course ?: throw CourseFailure("دوره در دسترس نیست.")
        require(lesson.courseId == course.id && state.value.lessons.any { it.id == lesson.id })
        if (!owns(course.id)) throw CourseFailure("دسترسی این دوره فعال نیست.")
        val token = generation
        val file = data().completed(lesson) ?: run {
            mutable.update { it.copy(downloadingId = lesson.id, progress = 0f) }
            data().download(course, lesson) { progress ->
                if (!closed && generation == token) mutable.update { it.copy(progress = progress.coerceIn(0f, 1f)) }
            }
        }
        currentCoroutineContext().ensureActive()
        if (!owns(course.id)) throw CourseFailure("دسترسی این دوره فعال نیست.")
        prepareWatch(course)
        mutable.update { it.copy(playing = QuickPlayingLesson(lesson, file), checkpoint = LessonPlaybackCheckpoint(), downloaded = it.downloaded + lesson.id) }
    }

    suspend fun subtitles(lesson: Lesson): SubtitleTrack? {
        val course = state.value.course ?: return null
        if (closed || !owns(course.id) || state.value.playing?.lesson?.id != lesson.id) return null
        return data().subtitles(course, lesson)
    }

    fun saveCheckpoint(lessonId: String, checkpoint: LessonPlaybackCheckpoint) {
        if (!closed && state.value.playing?.lesson?.id == lessonId) mutable.update { it.copy(checkpoint = checkpoint) }
    }

    fun minimize() {
        if (closed) return
        stopWork()
        mutable.update { it.copy(visible = false, loading = false, downloadingId = null, progress = 0f) }
    }

    fun show() {
        if (closed) return
        mutable.update { it.copy(visible = true) }
        if (state.value.lessons.isEmpty() && state.value.playing == null) load()
    }

    fun backToLessons() { mutable.update { it.copy(playing = null, checkpoint = LessonPlaybackCheckpoint()) } }
    fun cancelDownload() {
        stopWork()
        mutable.update { it.copy(loading = false, downloadingId = null, progress = 0f, message = "دانلود متوقف شد؛ می‌توانی دوباره ادامه بدهی.") }
    }

    fun nextDownloaded(): Lesson? = state.value.playing?.lesson?.let { current ->
        LessonPlayerPolicy.next(state.value.lessons, current.id)?.takeIf { it.id in state.value.downloaded && owns(it.courseId) }
    }

    private fun action(block: suspend () -> Unit) {
        if (closed) return
        stopWork()
        val token = generation
        mutable.update { it.copy(loading = true, message = null) }
        task = scope.launch {
            try { block() }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (!closed && token == generation) mutable.update { it.copy(message = (error as? CourseFailure)?.userMessage ?: "دریافت آموزش کامل نشد؛ اتصال اینترنت و فضای خالی را بررسی کن.") }
            } finally {
                if (!closed && token == generation) mutable.update { it.copy(loading = false, downloadingId = null, progress = 0f) }
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        stopWork(); lifetime.cancel()
        mutable.value = QuickLessonsState(visible = false)
    }
}
