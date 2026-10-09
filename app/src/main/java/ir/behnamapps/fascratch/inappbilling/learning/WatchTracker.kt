package ir.behnamapps.fascratch.inappbilling.learning

import android.os.SystemClock
import ir.behnamapps.fascratch.inappbilling.domain.Lesson
import org.json.JSONObject
import java.util.UUID

/** Only advancing, foreground playback counts. Seek jumps and scheduler stalls do not. */
class WatchTracker internal constructor(private val repo: LearningRepository, private val course: String, private val lesson: Lesson) {
    private var lastTime = 0L
    private var lastPosition = 0L
    private var wasPlaying = false
    private var from = 0L
    private var to = 0L
    private var watch = 0L
    private var started = 0L
    private var ended = 0L
    private var activeGrant: JSONObject? = null

    fun sample(position: Int, playing: Boolean) {
        val now = SystemClock.elapsedRealtime()
        val delta = now - lastTime
        val distance = position.toLong() - lastPosition
        if (WatchSamplePolicy.advancing(wasPlaying, playing, delta, distance)) {
            val grant = activeGrant ?: repo.grant(course)?.takeIf {
                it.optJSONObject("lesson_versions")?.optInt(lesson.id, -1) == lesson.version && repo.serverNow(it) <= it.getLong("valid_until_ms")
            }
            if (grant != null) {
                activeGrant = grant
                val wall = repo.serverAt(grant, now)
                if (watch == 0L) { from = lastPosition; started = wall - delta }
                to = position.toLong(); watch += delta; ended = wall
                if (watch >= 5000) flush()
            }
        } else flush()
        lastTime = now; lastPosition = position.toLong(); wasPlaying = playing
    }
    fun discontinuity() { flush(); wasPlaying = false }
    fun finish() { flush(); wasPlaying = false; repo.flushNow() }
    private fun flush() {
        val grant = activeGrant
        if (watch > 0 && to > from && grant != null) {
            runCatching {
                repo.store.add(course, grant.getString("uuid"), JSONObject().put("uuid", UUID.randomUUID().toString())
                    .put("lesson_uuid", lesson.id).put("version", lesson.version).put("started_ms", started).put("ended_ms", ended)
                    .put("watch_ms", watch).put("from_ms", from).put("to_ms", to))
                repo.recorded()
            }
        }
        watch = 0; activeGrant = null
    }
}
