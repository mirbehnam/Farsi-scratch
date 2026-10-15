package ir.behnamapps.fascratch.inappbilling

import ir.behnamapps.fascratch.inappbilling.domain.*
import org.junit.Assert.*
import org.junit.Test

class SrtSubtitlesTest {
    private val sample = "1\n00:00:01,000 --> 00:00:03,000\nسلام\n\n2\n00:00:05,000 --> 00:00:07,000\nدرس اول\n"
    @Test fun timeLookupHandlesPauseGapsSeekingBackwardsAndEndBoundary() {
        val track = SubtitleTrack.parse(("\uFEFF" + sample.replace("\n", "\r\n")).toByteArray())
        assertEquals("", track.textAt(999)); assertEquals("سلام", track.textAt(1000))
        assertEquals("سلام", track.textAt(2999)); assertEquals("", track.textAt(3000))
        assertEquals("درس اول", track.textAt(6500)); assertEquals("سلام", track.textAt(1500))
        assertEquals("", track.textAt(7000))
    }
    @Test fun overlappingAndUnsortedCuesAreResolvedWithoutLosingAnEarlierLongCue() {
        val track = SubtitleTrack.parse("2\n00:00:02,000 --> 00:00:03,000\nکوتاه\n\n1\n00:00:01,000 --> 00:00:06,000\nبلند\n".toByteArray())
        assertEquals("بلند\nکوتاه", track.textAt(2500)); assertEquals("بلند", track.textAt(4000))
    }
    @Test fun malformedOrOversizedDataIsRejected() {
        for (bad in listOf(byteArrayOf(0xff.toByte()), "".toByteArray(), "bad".toByteArray(), sample.replace("00:00:03,000", "00:00:00,000").toByteArray(), ByteArray(524289))) {
            try { SubtitleTrack.parse(bad); fail("Invalid SRT accepted") } catch (_: Exception) { }
        }
    }
    @Test fun subtitleKeysAreBoundedAndOnlyOurExactRouteIsAccepted() {
        val lesson = Lesson("ae6b3b4c-1d11-43fe-bd17-1c80ad234666", "course", "", "", 1, 10, "a".repeat(64), subtitle = LessonSubtitle("b".repeat(64), 100))
        val base = "https://api.behnamapp.ir/scratch/v1"
        val url = base + "/" + SubtitlePolicy.path(lesson)
        assertTrue(SubtitlePolicy.urlAllowed(base, lesson, url))
        assertFalse(SubtitlePolicy.urlAllowed(base, lesson, url + "?token=x"))
        assertFalse(SubtitlePolicy.urlAllowed(base, lesson, url.replace("api.behnamapp.ir", "evil.example")))
        assertFalse(SubtitlePolicy.valid(LessonSubtitle("bad", 1))); assertFalse(SubtitlePolicy.valid(LessonSubtitle("a".repeat(64), 524289)))
    }
}
