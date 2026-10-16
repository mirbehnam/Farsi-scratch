package ir.behnamapps.fascratch.inappbilling.learning

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.app.KeyguardManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import ir.behnamapps.fascratch.MainActivity

/** Stops on pause, screen exit, lock or another Activity; recreation never starts duplicate clocks. */
@Composable fun CodingTimeEffect(activity: MainActivity, editorVisible: Boolean) {
    DisposableEffect(activity, editorVisible) {
        val repo = LearningRepository.get(activity)
        fun update() {
            val interactive = activity.getSystemService(PowerManager::class.java)?.isInteractive == true
            val locked = activity.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
            repo.codingActive(editorVisible && interactive && !locked && activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
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
        onDispose { activity.lifecycle.removeObserver(observer); if (registered) runCatching { activity.unregisterReceiver(receiver) }; repo.codingActive(false) }
    }
}
