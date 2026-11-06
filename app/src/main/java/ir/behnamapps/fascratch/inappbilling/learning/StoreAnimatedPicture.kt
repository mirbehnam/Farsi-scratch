package ir.behnamapps.fascratch.inappbilling.learning

import android.content.Context
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Movie
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import android.view.View
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

internal data class StoreAnimation(val drawable: Drawable? = null, val movie: Movie? = null)
@Suppress("DEPRECATION")
internal fun decodeStoreAnimation(bytes: ByteArray): StoreAnimation? = runCatching {
    if (Build.VERSION.SDK_INT >= 28) {
        StoreAnimation(drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
            val scale = minOf(1f, 640f / info.size.width, 640f / info.size.height)
            decoder.setTargetSize(maxOf(1, (info.size.width * scale).toInt()), maxOf(1, (info.size.height * scale).toInt()))
        })
    } else Movie.decodeByteArray(bytes, 0, bytes.size)?.let { StoreAnimation(movie = it) }
}.getOrNull()

@Composable internal fun AnimatedStorePicture(bytes: ByteArray, modifier: Modifier = Modifier) {
    val animation by produceState<StoreAnimation?>(null, bytes) { value = withContext(Dispatchers.IO) { decodeStoreAnimation(bytes) } }
    AndroidView(factory = { StoreAnimationView(it) }, modifier = modifier, update = { it.show(animation) }, onRelease = { it.release() })
}

/** Native GIF view. Pauses when hidden/backgrounded and releases callbacks on disposal. */
@Suppress("DEPRECATION")
internal class StoreAnimationView(context: Context) : View(context) {
    private var frames: StoreAnimation? = null
    private var startedAt = 0L
    private var running = false
    private var lifecycleOwner: LifecycleOwner? = null
    private val lifecycleObserver = LifecycleEventObserver { _, _ -> updateRunning() }
    internal val animationRunning: Boolean get() = running
    fun show(animation: StoreAnimation?) {
        if (frames === animation) return
        (frames?.drawable as? Animatable)?.stop(); frames?.drawable?.callback = null
        frames = animation; animation?.drawable?.callback = this; startedAt = SystemClock.uptimeMillis()
        if (animation?.movie != null) setLayerType(LAYER_TYPE_SOFTWARE, null)
        updateRunning(); invalidate()
    }
    private fun updateRunning() {
        running = frames != null && isAttachedToWindow && visibility == VISIBLE && windowVisibility == VISIBLE && isShown &&
            (lifecycleOwner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) != false)
        (frames?.drawable as? Animatable)?.let { if (running) it.start() else it.stop() }
        if (running) invalidate()
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow(); lifecycleOwner = findViewTreeLifecycleOwner()
        lifecycleOwner?.lifecycle?.addObserver(lifecycleObserver); updateRunning()
    }
    override fun onDetachedFromWindow() {
        lifecycleOwner?.lifecycle?.removeObserver(lifecycleObserver); lifecycleOwner = null
        running = false; (frames?.drawable as? Animatable)?.stop(); super.onDetachedFromWindow()
    }
    override fun onVisibilityAggregated(isVisible: Boolean) { super.onVisibilityAggregated(isVisible); updateRunning() }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); updateRunning() }
    override fun verifyDrawable(who: Drawable) = who === frames?.drawable || super.verifyDrawable(who)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas); val current = frames ?: return
        val w = current.drawable?.intrinsicWidth ?: current.movie?.width() ?: return
        val h = current.drawable?.intrinsicHeight ?: current.movie?.height() ?: return
        if (w < 1 || h < 1) return
        val scale = minOf(width.toFloat() / w, height.toFloat() / h)
        canvas.save(); canvas.translate((width - w * scale) / 2, (height - h * scale) / 2); canvas.scale(scale, scale)
        if (current.drawable != null) { current.drawable.setBounds(0, 0, w, h); current.drawable.draw(canvas) }
        else current.movie?.let { movie -> movie.setTime(((SystemClock.uptimeMillis() - startedAt) % maxOf(100, movie.duration())).toInt()); movie.draw(canvas, 0f, 0f); if (running) postInvalidateDelayed(33) }
        canvas.restore()
    }
    fun release() {
        lifecycleOwner?.lifecycle?.removeObserver(lifecycleObserver); lifecycleOwner = null
        running = false; (frames?.drawable as? Animatable)?.stop(); frames?.drawable?.callback = null; frames = null
    }
}