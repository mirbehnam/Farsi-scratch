package ir.behnamapps.fascratch.inappbilling.learning

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Tiny local coin-like chime: no network, decoder or retained Activity. Respects system volume. */
internal object MissionRewardSound {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val samples by lazy { CoinChime.samples() }
    // The confirmed reward's chime survives closing its dialog, but never retains an Activity.
    fun play() { scope.launch { mutex.withLock { playOnce() } } }
    private suspend fun playOnce() {
        var track: AudioTrack? = null
        try {
            val rate = 22050
            track = AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(samples.size * 2).build()
            if (track.state == AudioTrack.STATE_INITIALIZED) {
                if (track.write(samples, 0, samples.size) == samples.size) {
                    track.setVolume(.8f); track.play(); delay(500)
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { /* Audio failure must never affect the awarded XP. */ }
        finally { runCatching { track?.release() } }
    }
}
