package ir.behnamapps.fascratch.inappbilling

import ir.behnamapps.fascratch.tour.TourCoverage
import ir.behnamapps.fascratch.tour.TourModels
import org.junit.Assert.*
import org.junit.Test

class TourCoverageTest {
    @Test fun completePlaybackRequiresCoverageAndAnEndEvent() {
        val coverage = TourCoverage(10000)
        for (second in 0..10) coverage.sample(second * 1000, true, second * 1000L)
        assertFalse(coverage.complete)
        coverage.sample(10000, false, 10000, true)
        assertTrue(coverage.complete)
        assertEquals(listOf(0 to 10000), coverage.ranges())
    }

    @Test fun seekingToEndCannotCompleteAndMissingPartsCanBeWatchedLater() {
        val coverage = TourCoverage(10000)
        for (second in 0..3) coverage.sample(second * 1000, true, second * 1000L)
        coverage.discontinuity()
        coverage.sample(9000, true, 4000)
        coverage.sample(10000, false, 5000, true)
        assertFalse(coverage.complete)
        val restored = TourCoverage(10000, coverage.ranges(), coverage.ended)
        for (second in 3..9) restored.sample(second * 1000, true, 6000 + second * 1000L)
        assertTrue(restored.complete)
    }

    @Test fun PauseBufferingBackgroundAndUnexpectedJumpsNeverAddCoverage() {
        val coverage = TourCoverage(10000)
        coverage.sample(0, true, 0)
        coverage.sample(1000, true, 1000)
        coverage.sample(1000, false, 2000)
        coverage.sample(8000, false, 3000)
        coverage.sample(9000, true, 9000)
        coverage.sample(10000, true, 9100)
        assertEquals(listOf(0 to 1000), coverage.ranges())
        assertFalse(coverage.complete)
    }

    @Test fun replayDoesNotCountCoverageTwiceAndVersionsHaveIndependentState() {
        val old = TourCoverage(1000, listOf(0 to 1000), true)
        old.sample(0, true, 0); old.sample(1000, false, 1000, true)
        assertEquals(listOf(0 to 1000), old.ranges())
        assertTrue(old.complete)
        assertFalse(TourCoverage(1000).complete)
    }

    @Test fun shortVideoStartsFromThePreparedPausedFrameWithoutLosingItsBeginning() {
        val coverage = TourCoverage(900)
        coverage.sample(0, false, 0)
        coverage.sample(300, true, 300)
        coverage.sample(600, true, 600)
        coverage.sample(900, false, 900, true)
        assertTrue(coverage.complete)
    }

    @Test fun publicMediaAllowsHttpsCdnButNoClearTextOrEmbeddedCredentials() {
        assertTrue(TourModels.mediaUrl("https://cdn.example.com/media/video.mp4"))
        assertFalse(TourModels.mediaUrl("http://example.com/video"))
        assertFalse(TourModels.mediaUrl("https://secret@example.com/video"))
        assertFalse(TourModels.mediaUrl("file:///etc/passwd"))
    }
}
