package ir.behnamapps.fascratch.inappbilling.learning

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.app.KeyguardManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import ir.behnamapps.fascratch.MainActivity

/** Stops on pause, screen exit, lock, lesson popup or three minutes without user interaction. */
@Composable internal fun CodingTimeEffect(activity: MainActivity, editorVisible: Boolean,
    idleTimeoutMs: Long = CodingIdleWindow.TIMEOUT_MS) {
    DisposableEffect(activity, editorVisible, idleTimeoutMs) {
        val repo = LearningRepository.get(activity)
        val idle = CodingIdleWindow(SystemClock.elapsedRealtime(), idleTimeoutMs)
        val handler = Handler(Looper.getMainLooper())
        var counting: Boolean? = null
        fun update() {
            val interactive = activity.getSystemService(PowerManager::class.java)?.isInteractive == true
            val locked = activity.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
            val active = editorVisible && idle.active(SystemClock.elapsedRealtime()) && interactive && !locked &&
                activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (active != counting) { counting = active; repo.codingActive(active) }
        }
        // One pending deadline, not a polling loop or a new timer/recomposition per touch.
        val deadline = object : Runnable {
            override fun run() {
                val remaining = idle.remainingMs(SystemClock.elapsedRealtime())
                if (remaining > 0) handler.postDelayed(this, remaining) else update()
            }
        }
        val interaction: () -> Unit = {
            val now = SystemClock.elapsedRealtime()
            val wasIdle = !idle.active(now)
            idle.interact(now)
            if (wasIdle) {
                update()
                handler.removeCallbacks(deadline)
                handler.postDelayed(deadline, idleTimeoutMs)
            }
        }
        if (editorVisible) {
            activity.editorInteractionListener = interaction
            handler.postDelayed(deadline, idleTimeoutMs)
        }
        val observer = LifecycleEventObserver { _, _ -> update() }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { update() }
        }
        val registered = runCatching { ContextCompat.registerReceiver(activity, receiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT)
        }, ContextCompat.RECEIVER_NOT_EXPORTED); true }.getOrDefault(false)
        activity.lifecycle.addObserver(observer)
        update()
        onDispose {
            handler.removeCallbacks(deadline)
            if (activity.editorInteractionListener === interaction) activity.editorInteractionListener = null
            activity.lifecycle.removeObserver(observer)
            if (registered) runCatching { activity.unregisterReceiver(receiver) }
            repo.codingActive(false)
        }
    }
}
