package ir.behnamapps.fascratch.inappbilling

import ir.behnamapps.fascratch.inappbilling.domain.TrafficErrorMessage
import ir.behnamapps.fascratch.inappbilling.domain.CoursePolicy
import org.junit.Assert.*
import org.junit.Test

class TrafficErrorMessageTest {
    @Test fun networkBlockDoesNotMeanPurchaseRecoveryOrLogout() {
        val message = TrafficErrorMessage.message(403, "ip_blocked", null)!!
        assertFalse(message.contains("خرید"))
        assertFalse(message.contains("نشست"))
        assertFalse(CoursePolicy.revokesAccess("ip_blocked"))
        assertNull(TrafficErrorMessage.message(401, "unauthorized", null))
        assertNull(TrafficErrorMessage.message(403, "access_disabled", null))
    }

    @Test fun retrySecondsAreBoundedAndMissingDurationIsNotGuessed() {
        assertTrue(TrafficErrorMessage.message(429, "rate_limited", 2)!!.contains("2 ثانیه"))
        assertTrue(TrafficErrorMessage.message(429, "rate_limited", Long.MAX_VALUE)!!.contains("3600 ثانیه"))
        assertTrue(TrafficErrorMessage.message(429, "", -1)!!.contains("کمی بعد"))
        assertTrue(TrafficErrorMessage.message(429, "", null)!!.contains("کمی بعد"))
    }
}
