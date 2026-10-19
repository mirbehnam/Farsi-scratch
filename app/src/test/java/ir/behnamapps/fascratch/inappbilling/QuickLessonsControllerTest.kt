package ir.behnamapps.fascratch.inappbilling

import ir.behnamapps.fascratch.inappbilling.domain.*
import ir.behnamapps.fascratch.inappbilling.presentation.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class QuickLessonsControllerTest {
    private val course = Course("11111111-1111-4111-8111-111111111111", "دوره", "", "scratch_basic")
    private val lesson = Lesson("22222222-2222-4222-8222-222222222222", course.id, "", "قسمت اول", 1, 100, "a".repeat(64))
    private val video = File("test-only-video.mp4")

    private inner class Shelf {
        var creates = 0
        var closes = 0
        var downloads = 0
        var prepares = 0
        var owned = true
        var saved = false
        var result: CompletableDeferred<File>? = null
        var progress: ((Float) -> Unit)? = null
        fun source() = object : QuickLessonSource {
            init { creates++ }
            override suspend fun cachedCourses() = emptyList<Course>()
            override suspend fun courses() = listOf(course)
            override suspend fun cachedLessons(course: Course): List<Lesson>? = null
            override suspend fun lessons(course: Course) = listOf(lesson)
            override suspend fun completed(lesson: Lesson) = video.takeIf { saved }
            override suspend fun download(course: Course, lesson: Lesson, progress: (Float) -> Unit): File {
                downloads++; this@Shelf.progress = progress
                val file = result?.await() ?: video
                saved = true
                return file
            }
            override suspend fun subtitles(course: Course, lesson: Lesson): SubtitleTrack? = null
            override fun close() { closes++ }
        }
        fun controller() = QuickLessonsController(::source, { owned }, { prepares++ }, Dispatchers.Unconfined)
    }

    @Test fun constructionAndClosingUnusedSessionNeverTouchStorageNetworkOrPlayer() {
        val shelf = Shelf()
        val controller = shelf.controller()
        assertEquals(0, shelf.creates)
        assertEquals(0, shelf.downloads)
        assertEquals(0, shelf.prepares)
        controller.close()
        controller.load()
        assertEquals(0, shelf.creates)
        assertFalse(controller.state.value.visible)
    }

    @Test fun minimizeRetainsOnlyPlaybackValuesAndResumeDoesNotReloadTheCatalog() {
        val shelf = Shelf().apply { saved = true }
        val controller = shelf.controller()
        controller.load()
        assertEquals(0, shelf.prepares)
        controller.play(lesson)
        assertEquals(video, controller.state.value.playing?.file)
        assertEquals(0, shelf.downloads)
        assertEquals(1, shelf.prepares)
        controller.saveCheckpoint(lesson.id, LessonPlaybackCheckpoint(12500, 1.5f, true))
        val created = shelf.creates
        controller.minimize()
        assertFalse(controller.state.value.visible)
        assertEquals(created, shelf.closes)
        controller.show()
        assertTrue(controller.state.value.visible)
        assertEquals(created, shelf.creates)
        assertEquals(12500, controller.state.value.checkpoint.positionMs)
        assertEquals(1.5f, controller.state.value.checkpoint.speed, .0001f)
        controller.close()
        assertNull(controller.state.value.playing)
        assertTrue(controller.state.value.lessons.isEmpty())
        assertTrue(controller.state.value.courses.isEmpty())
    }

    @Test fun missingVideoDownloadsAndReportsProgressBeforeAnyPlaybackOrWatchPreparation() {
        val shelf = Shelf().apply { result = CompletableDeferred() }
        val controller = shelf.controller()
        controller.load(); controller.play(lesson)
        assertNull(controller.state.value.playing)
        assertEquals(lesson.id, controller.state.value.downloadingId)
        assertEquals(0, shelf.prepares)
        shelf.progress?.invoke(.37f)
        assertEquals(.37f, controller.state.value.progress, .0001f)
        shelf.result!!.complete(video)
        assertEquals(video, controller.state.value.playing?.file)
        assertTrue(lesson.id in controller.state.value.downloaded)
        assertEquals(1, shelf.prepares)
        controller.close()
    }

    @Test fun closeCancelsPendingDownloadAndLateCompletionCannotResurrectThePopup() {
        val shelf = Shelf().apply { result = CompletableDeferred() }
        val controller = shelf.controller()
        controller.load(); controller.play(lesson)
        controller.close()
        shelf.progress?.invoke(.9f)
        shelf.result!!.complete(video)
        controller.saveCheckpoint(lesson.id, LessonPlaybackCheckpoint(5000))
        controller.show()
        assertFalse(controller.state.value.visible)
        assertNull(controller.state.value.playing)
        assertEquals(0, shelf.prepares)
        assertTrue(controller.state.value.lessons.isEmpty())
    }

    @Test fun ownershipRevocationDuringDownloadNeverStartsPlayback() {
        val shelf = Shelf().apply { result = CompletableDeferred() }
        val controller = shelf.controller()
        controller.load(); controller.play(lesson)
        shelf.owned = false
        shelf.result!!.complete(video)
        assertNull(controller.state.value.playing)
        assertEquals(0, shelf.prepares)
        assertNotNull(controller.state.value.message)
        controller.close()
    }

    @Test fun minimizeAlsoCancelsDownloadsAndDoesNotCountTheWaitingTime() {
        val shelf = Shelf().apply { result = CompletableDeferred() }
        val controller = shelf.controller()
        controller.load(); controller.play(lesson)
        controller.minimize()
        assertFalse(controller.state.value.visible)
        assertNull(controller.state.value.downloadingId)
        shelf.result!!.complete(video)
        assertNull(controller.state.value.playing)
        assertEquals(0, shelf.prepares)
        controller.close()
    }

    @Test fun shortcutAndCheckpointPoliciesKeepInvalidAccessAndSpeedOut() {
        assertFalse(QuickLessonPolicy.shortcutVisible(true, true, setOf(course.id)))
        assertFalse(QuickLessonPolicy.shortcutVisible(false, false, setOf(course.id)))
        assertFalse(QuickLessonPolicy.shortcutVisible(true, false, emptySet()))
        assertTrue(QuickLessonPolicy.shortcutVisible(true, false, setOf(course.id)))
        val snapshot = QuickLessonPolicy.checkpoint(-30, Float.NaN, false)
        assertEquals(0, snapshot.positionMs)
        assertEquals(1f, snapshot.speed, .0001f)
        assertFalse(snapshot.autoPlay)
    }
}
