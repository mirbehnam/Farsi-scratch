package ir.behnamapps.fascratch.inappbilling.presentation

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.Surface
import android.view.TextureView
import java.io.File

/** TextureView (not SurfaceView) supports an actual transformed video with untransformed controls. */
internal class ZoomVideoView(context: Context, private val file: File, private val initialPosition: Int,
    private val onReady: () -> Unit, private val onError: () -> Unit, private val onTap: () -> Unit,
    private val onZoom: (Float) -> Unit, private val onDoubleTapSeek: (Int) -> Unit,
    private val onCompleted: () -> Unit) : TextureView(context), TextureView.SurfaceTextureListener {
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var ready = false
    private var released = false
    private var foreground = true
    private var resumePosition = initialPosition
    private var videoWidth = 0f
    private var videoHeight = 0f
    private var scale = 1f
    private var panX = 0f
    private var panY = 0f
    private var playbackSpeed = 1f
    private var multiTouchSequence = false
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val focus = AudioManager.OnAudioFocusChangeListener { change -> if (change < 0) pause() }
    private val scaler = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val oldScale = scale
            scale = PlayerViewport.zoom(scale * detector.scaleFactor)
            // Keep the point between the two fingers stationary while zooming.
            val ratio = scale / oldScale
            panX = (panX - (detector.focusX - width / 2f)) * ratio + detector.focusX - width / 2f
            panY = (panY - (detector.focusY - height / 2f)) * ratio + detector.focusY - height / 2f
            transform(); onZoom(scale)
            return true
        }
    })
    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(event: MotionEvent) = true
        override fun onSingleTapConfirmed(event: MotionEvent): Boolean { performClick(); onTap(); return true }
        override fun onDoubleTap(event: MotionEvent): Boolean {
            if (!multiTouchSequence && event.pointerCount == 1 && !scaler.isInProgress)
                onDoubleTapSeek(LessonPlayerPolicy.doubleTapDelta(event.x, width))
            return true
        }
        override fun onScroll(first: MotionEvent?, current: MotionEvent, dx: Float, dy: Float): Boolean {
            if (!scaler.isInProgress && scale > 1f) { panX -= dx; panY -= dy; transform() }
            return true
        }
    })
    init { surfaceTextureListener = this; contentDescription = "ویدئوی درس؛ زوم با دو انگشت؛ دو ضربه سمت راست جلو و سمت چپ عقب" }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) multiTouchSequence = false
        if (event.pointerCount > 1) {
            if (!multiTouchSequence) {
                val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                gestures.onTouchEvent(cancel); cancel.recycle()
            }
            multiTouchSequence = true
        }
        scaler.onTouchEvent(event)
        if (!multiTouchSequence) gestures.onTouchEvent(event)
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        if (released) return
        try {
            surface = Surface(texture)
            player = MediaPlayer().also { media ->
                media.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
                media.setSurface(surface)
                media.setDataSource(file.absolutePath)
                media.setOnPreparedListener {
                    if (!released) {
                        ready = true; videoWidth = it.videoWidth.toFloat(); videoHeight = it.videoHeight.toFloat()
                        transform()
                        if (resumePosition > 0) it.seekTo(resumePosition.coerceAtMost(it.duration))
                        play(); onReady()
                    }
                }
                media.setOnVideoSizeChangedListener { _, w, h -> videoWidth = w.toFloat(); videoHeight = h.toFloat(); transform() }
                media.setOnCompletionListener { if (!released) onCompleted() }
                media.setOnErrorListener { _, _, _ -> ready = false; onError(); true }
                media.prepareAsync()
            }
        } catch (_: Exception) { onError(); releaseMedia() }
    }
    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) { transform() }
    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        resumePosition = position(); releaseMedia(); return true
    }
    private fun transform() {
        if (width == 0 || height == 0) return
        val fit = PlayerViewport.fit(width.toFloat(), height.toFloat(), videoWidth, videoHeight)
        if (fit.width == 0f) return
        panX = PlayerViewport.pan(panX, fit.width, width.toFloat(), scale)
        panY = PlayerViewport.pan(panY, fit.height, height.toFloat(), scale)
        val matrix = Matrix().apply {
            setScale(fit.width / width * scale, fit.height / height * scale, width / 2f, height / 2f)
            postTranslate(panX, panY)
        }
        setTransform(matrix)
    }
    fun resetZoom() { scale = 1f; panX = 0f; panY = 0f; transform(); onZoom(scale) }
    fun position() = if (ready) runCatching { player?.currentPosition ?: 0 }.getOrDefault(0) else resumePosition
    fun duration() = if (ready) runCatching { player?.duration ?: 0 }.getOrDefault(0) else 0
    fun isPlaying() = ready && runCatching { player?.isPlaying == true }.getOrDefault(false)
    @Suppress("DEPRECATION")
    fun play() {
        if (ready && foreground && audio.requestAudioFocus(focus, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            runCatching { player?.start(); applySpeed(playbackSpeed, keepPlaying = true) }
        }
    }
    fun pause() { if (ready) runCatching { player?.pause() } }
    fun setForeground(value: Boolean) { foreground = value; if (!value) pause() }
    fun seek(position: Int) { if (ready) runCatching { player?.seekTo(position.coerceIn(0, duration())) } }
    fun toggle() { if (isPlaying()) pause() else play() }
    fun setSpeed(value: Float): Boolean {
        if (!ready || value !in LessonPlayerPolicy.speeds) return false
        val success = applySpeed(value, isPlaying())
        if (success) playbackSpeed = value
        return success
    }
    private fun applySpeed(value: Float, keepPlaying: Boolean): Boolean = runCatching {
        val media = player ?: return false
        // A nonzero speed may start a paused MediaPlayer: restore the prior pause state.
        media.playbackParams = PlaybackParams().allowDefaults().setSpeed(value).setPitch(1f)
        if (!keepPlaying) media.pause()
        true
    }.getOrDefault(false)
    @Suppress("DEPRECATION")
    private fun releaseMedia() {
        ready = false
        player?.let { runCatching { it.setOnPreparedListener(null); it.setOnErrorListener(null); it.setOnCompletionListener(null); it.release() } }; player = null
        surface?.release(); surface = null
        audio.abandonAudioFocus(focus)
    }
    fun release() { released = true; releaseMedia() }
}
