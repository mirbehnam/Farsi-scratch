package ir.behnamapps.fascratch.inappbilling

import ir.behnamapps.fascratch.inappbilling.presentation.giftFeedbackStyle
import org.junit.Assert.assertEquals
import org.junit.Test
import ir.behnamapps.fascratch.inappbilling.learning.LearnerCourse
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class GiftFeedbackTest {
    @Test fun giftAccessExpiresWithoutBecomingPermanentPurchase() {
        val gift = LearnerCourse("course", "Gift", 0L, false, true, "gift", 2000L)
        assertTrue(gift.canAccess(1999L))
        assertFalse(gift.canAccess(2000L))
        assertFalse(gift.copy(enabled = false).canAccess(1000L))
        assertFalse(gift.copy(accessUntilMs = null).canAccess(1000L))
        assertTrue(gift.copy(purchased = true, accessSource = "purchase", accessUntilMs = null).canAccess(Long.MAX_VALUE))
    }
    @Test fun giftErrorsHaveSpecificGraphics() {
        assertEquals("clock", giftFeedbackStyle("gift_expired").symbol)
        assertEquals("box", giftFeedbackStyle("gift_exhausted").symbol)
        assertEquals("removed", giftFeedbackStyle("gift_deleted").symbol)
        assertEquals("pause", giftFeedbackStyle("gift_disabled").symbol)
        assertEquals("search", giftFeedbackStyle("gift_not_found").symbol)
        assertEquals("gift", giftFeedbackStyle("success").symbol)
        assertEquals("retry", giftFeedbackStyle("unknown").symbol)
    }
}
