package ir.behnamapps.fascratch

import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ir.behnamapps.fascratch.inappbilling.learning.CodingTimeEffect
import ir.behnamapps.fascratch.inappbilling.learning.LearningRepository
import kotlinx.coroutines.Job
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Accelerated real Handler/lifecycle test; the production 180-second boundary has unit coverage. */
@RunWith(AndroidJUnit4::class)
class CodingActivityTest {
    @Test fun idleStopsTouchResumesAndLessonPopupSuppressesCoding() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val repository = LearningRepository.get(instrumentation.targetContext)
        val field = LearningRepository::class.java.getDeclaredField("codingJob").apply { isAccessible = true }
        fun running(): Boolean = (field.get(repository) as? Job)?.isActive == true
        fun await(expected: Boolean) {
            val until = SystemClock.elapsedRealtime() + 5_000
            while (running() != expected && SystemClock.elapsedRealtime() < until) Thread.sleep(30)
            assertEquals("Coding job active", expected, running())
        }
        val editorVisible = mutableStateOf(true)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                activity.setContent { CodingTimeEffect(activity, editorVisible.value, idleTimeoutMs = 1_500) }
            }
            await(true)
            await(false) // Real main-looper timeout, no touch required to trigger the stop.
            scenario.onActivity { it.onUserInteraction() }
            await(true)
            scenario.onActivity { editorVisible.value = false } // Same gate as an open lesson popup.
            await(false)
            scenario.onActivity { it.onUserInteraction() }
            Thread.sleep(150)
            assertFalse("Touches while watching or paused in a lesson cannot start coding", running())
            scenario.onActivity { editorVisible.value = true }
            await(true)
            scenario.onActivity { editorVisible.value = false }
            await(false)
        }
        assertFalse(running())
    }
}
