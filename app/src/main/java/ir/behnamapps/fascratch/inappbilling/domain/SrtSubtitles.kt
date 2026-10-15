package ir.behnamapps.fascratch.inappbilling.domain

import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

data class LessonSubtitle(val sha256: String, val bytes: Int)
data class SubtitleCue(val start: Int, val end: Int, val text: String)

class SubtitleTrack private constructor(private val cues: List<SubtitleCue>) {
    private val prefixEnd = IntArray(cues.size).apply { var maximum = 0; cues.forEachIndexed { i, c -> maximum = maxOf(maximum, c.end); this[i] = maximum } }
    fun textAt(position: Int): String {
        var low = 0; var high = cues.lastIndex; var found = -1
        while (low <= high) { val mid = (low + high) ushr 1; if (cues[mid].start <= position) { found = mid; low = mid + 1 } else high = mid - 1 }
        val active = ArrayList<String>(3)
        while (found >= 0 && prefixEnd[found] > position && active.size < 3) {
            val cue = cues[found--]
            if (position < cue.end) active.add(cue.text)
        }
        return active.asReversed().joinToString("\n")
    }

    companion object {
        const val MAX_BYTES = 524288
        fun parse(bytes: ByteArray): SubtitleTrack {
            require(bytes.isNotEmpty() && bytes.size <= MAX_BYTES)
            val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n').trim()
            require(text.none { it.isISOControl() && it != '\n' && it != '\t' })
            val blocks = text.split(Regex("\n[\t ]*\n+"))
            require(blocks.size in 1..10000)
            val timing = Regex("([0-9]{2}):([0-5][0-9]):([0-5][0-9]),([0-9]{3})\\s*-->\\s*([0-9]{2}):([0-5][0-9]):([0-5][0-9]),([0-9]{3})")
            val cues = blocks.map { block ->
                val lines = block.trim().split('\n').toMutableList()
                if (lines.firstOrNull()?.trim()?.matches(Regex("[0-9]+")) == true) lines.removeAt(0)
                val match = timing.matchEntire(lines.removeAt(0).trim()) ?: error("Invalid SRT timing")
                fun time(offset: Int) = ((match.groupValues[offset].toInt() * 3600 + match.groupValues[offset + 1].toInt() * 60 + match.groupValues[offset + 2].toInt()) * 1000 + match.groupValues[offset + 3].toInt())
                val start = time(1); val end = time(5); val body = lines.joinToString("\n").trim()
                require(end > start && body.isNotBlank() && body.codePointCount(0, body.length) <= 2000 && lines.size <= 6)
                SubtitleCue(start, end, body)
            }.sortedBy { it.start }
            return SubtitleTrack(cues)
        }
    }
}

object SubtitlePolicy {
    fun valid(value: LessonSubtitle): Boolean = value.sha256.matches(Regex("[a-f0-9]{64}")) && value.bytes in 1..SubtitleTrack.MAX_BYTES
    fun path(lesson: Lesson): String { val subtitle = requireNotNull(lesson.subtitle); require(valid(subtitle)); return "lessons/${CoursePolicy.uuid(lesson.id)}/subtitles/${subtitle.sha256}" }
    fun urlAllowed(base: String, lesson: Lesson, url: String): Boolean = runCatching {
        val uri = URI(url)
        CoursePolicy.sameOrigin(base, url) && uri.userInfo == null && uri.query == null && uri.fragment == null && url == base.trimEnd('/') + "/" + path(lesson)
    }.getOrDefault(false)
}
