package ir.behnamapps.fascratch.inappbilling.presentation

import kotlin.math.min

/** Pure geometry, shared by rendering/gesture clamping and JVM tests. */
internal object PlayerViewport {
    data class Fit(val width: Float, val height: Float)
    fun fit(viewWidth: Float, viewHeight: Float, videoWidth: Float, videoHeight: Float): Fit {
        if (viewWidth <= 0 || viewHeight <= 0 || videoWidth <= 0 || videoHeight <= 0) return Fit(0f, 0f)
        val ratio = min(viewWidth / videoWidth, viewHeight / videoHeight)
        return Fit(videoWidth * ratio, videoHeight * ratio)
    }
    fun zoom(value: Float) = if (value.isFinite()) value.coerceIn(1f, 4f) else 1f
    fun anchoredPan(pan: Float, previousFocus: Float, currentFocus: Float, center: Float, ratio: Float): Float =
        (pan - (previousFocus - center)) * ratio + currentFocus - center
    fun pan(value: Float, content: Float, viewport: Float, zoom: Float): Float {
        val limit = ((content * zoom - viewport) / 2f).coerceAtLeast(0f)
        return if (value.isFinite()) value.coerceIn(-limit, limit) else 0f
    }
}
