package ir.behnamapps.fascratch.inappbilling.learning

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin

/** Tiny local coin-like chime: no network, decoder or retained Activity. Respects system volume. */
internal object MissionRewardSound {
    suspend fun play() = withContext(Dispatchers.IO) {
        var track: AudioTrack? = null
        try {
            val rate = 22050
            val samples = ShortArray(rate / 4) { index ->
                val t = index.toDouble() / rate
                val frequency = if (t < .085) 1318.5 else 1975.5
                val envelope = (1.0 - t / .25).coerceAtLeast(0.0) * minOf(t / .006, 1.0)
                (sin(2 * PI * frequency * t) * envelope * 4200).toInt().toShort()
            }
            track = AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(samples.size * 2).build()
            if (track.state == AudioTrack.STATE_INITIALIZED) {
                track.write(samples, 0, samples.size); track.play(); delay(300)
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { /* Audio failure must never affect the awarded XP. */ }
        finally { runCatching { track?.release() } }
    }
}
