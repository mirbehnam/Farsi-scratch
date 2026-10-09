package ir.behnamapps.fascratch.inappbilling.learning

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import ir.behnamapps.fascratch.MainActivity

/** Stops on pause, screen exit, lock or another Activity; recreation never starts duplicate clocks. */
@Composable fun CodingTimeEffect(activity: MainActivity, editorVisible: Boolean) {
    DisposableEffect(activity, editorVisible) {
        val repo = LearningRepository.get(activity)
        val observer = LifecycleEventObserver { _, _ ->
            repo.codingActive(editorVisible && activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        }
        activity.lifecycle.addObserver(observer)
        repo.codingActive(editorVisible && activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose { activity.lifecycle.removeObserver(observer); repo.codingActive(false) }
    }
}
