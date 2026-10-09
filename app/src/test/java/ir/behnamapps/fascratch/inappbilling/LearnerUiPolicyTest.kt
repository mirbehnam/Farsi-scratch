package ir.behnamapps.fascratch.inappbilling

import ir.behnamapps.fascratch.inappbilling.learning.learningDuration
import org.junit.Assert.assertEquals
import org.junit.Test

class LearnerUiPolicyTest {
    @Test fun durationsDoNotShowZeroHoursAndUseMinuteRemainders() {
        assertEquals("0 دقیقه", learningDuration(-1))
        assertEquals("59 دقیقه", learningDuration(3599999))
        assertEquals("1 ساعت و 0 دقیقه", learningDuration(3600000))
        assertEquals("2 ساعت و 20 دقیقه", learningDuration(8400000))
    }
}
