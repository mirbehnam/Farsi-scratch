package ir.behnamapps.fascratch.inappbilling

import ir.behnamapps.fascratch.tour.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException
import java.net.SocketTimeoutException

class TourFeedbackTest {
    @Test fun rawNetworkMessagesNeverReachTheUserAndGuidanceExplainsResume() {
        for (error in listOf(UnknownHostException("Unable to resolve host api.example.com"), SocketTimeoutException("timeout SSL socket"))) {
            val notice = TourFeedback.download(error)
            assertTrue(notice.message.contains("اینترنت"))
            assertTrue(notice.message.contains("ادامه"))
            assertFalse((notice.title + notice.message).any { it in 'a'..'z' || it in 'A'..'Z' })
        }
        assertFalse(TourFeedback.download(IOException("EACCES /data/user/0/secret")).message.contains("/data"))
    }

    @Test fun diskSpaceAndRemovedVideoHaveTheirOwnHelpfulPersianMessages() {
        assertTrue(TourFeedback.download(TourDownloadException(TourFailure.SPACE)).message.contains("فضا"))
        assertTrue(TourFeedback.download(TourDownloadException(TourFailure.UNAVAILABLE)).message.contains("فهرست"))
        assertTrue(TourFeedback.download(TourDownloadException(TourFailure.INCOMPLETE)).message.contains("سالم"))
    }

    @Test fun anEmptyOrUninitializedCatalogIsNeverEquivalentToCompletingTheTour() {
        assertFalse(TourState().allWatched)
        assertFalse(TourState(initialized = true, hasManifest = true).allWatched)
        val media = TourMedia("https://example.com/video", "a".repeat(64), 100)
        val item = TourVideo("first", "شروع", "", 1000, media, null, "")
        assertFalse(TourState(items = listOf(item)).allWatched)
        assertTrue(TourState(items = listOf(item), completed = setOf(item.key)).allWatched)
        assertFalse(TourState(items = listOf(item), completed = setOf("first-${"b".repeat(64)}")).allWatched)
    }
}
