package ir.behnamapps.fascratch.inappbilling

import ir.behnamapps.fascratch.inappbilling.domain.Course
import ir.behnamapps.fascratch.inappbilling.presentation.discountCountdownLabel
import ir.behnamapps.fascratch.inappbilling.presentation.discountTimeParts
import ir.behnamapps.fascratch.inappbilling.presentation.discountTickMillis
import ir.behnamapps.fascratch.inappbilling.presentation.courseSavings
import ir.behnamapps.fascratch.inappbilling.presentation.DiscountTimeParts
import org.junit.Assert.*
import org.junit.Test

class DiscountCountdownTest {
    @Test fun timerUnitsPreserveDaysHoursMinutesAndSeconds() {
        assertEquals(DiscountTimeParts(2, 3, 4, 5), discountTimeParts(course(2 * 86400000L + 3 * 3600000L + 4 * 60000L + 5000L), now))
        assertEquals(DiscountTimeParts(0, 0, 0, 1), discountTimeParts(course(1), now))
        assertNull(discountTimeParts(course(0), now))
        assertNull(discountTimeParts(course(1000).copy(discountEndsAtMillis = null), now))
    }
    @Test fun timerUpdatesQuicklyOnlyInTheLastDayAndStopsExactlyAtExpiry() {
        assertEquals(60000L, discountTickMillis(course(2 * 86400000L), now))
        assertEquals(1000L, discountTickMillis(course(86400000L), now))
        assertEquals(13L, discountTickMillis(course(86400000L + 13L), now))
        assertEquals(13L, discountTickMillis(course(13), now))
        assertNull(discountTickMillis(course(0), now))
    }
    @Test fun savingsAreExactAndDoNotOverstatePercentOrSurviveExpiry() {
        val saving = courseSavings(course(1000), now)!!
        assertEquals(25000L, saving.amountToman)
        assertEquals(25, saving.percent)
        assertEquals(99, courseSavings(course(1000).copy(serverPriceToman = 1, compareAtToman = 1000), now)!!.percent)
        assertEquals(100, courseSavings(course(1000).copy(serverPriceToman = 0), now)!!.percent)
        assertNull(courseSavings(course(0), now))
        assertNull(courseSavings(course(1000).copy(compareAtToman = 75000), now))
        assertNull(courseSavings(course(1000).copy(serverPriceToman = null), now))
    }
    private val now = 1_000_000L
    private fun course(remaining: Long) = Course("course", "", "", "sku", serverPriceToman = 75000,
        compareAtToman = 100000, discountEndsAtMillis = now + remaining)

    @Test fun showsPersianDaysAndHours() {
        assertEquals("۲ روز و ۳ ساعت تا پایان تخفیف", discountCountdownLabel(course(2 * 86_400_000L + 3 * 3_600_000L), now))
        assertEquals("۱ روز تا پایان تخفیف", discountCountdownLabel(course(86_400_000L), now))
    }
    @Test fun handlesTheLastDayAndMinuteWithoutShowingZeroDays() {
        assertEquals("۵ ساعت تا پایان تخفیف", discountCountdownLabel(course(5 * 3_600_000L), now))
        assertEquals("۲ دقیقه تا پایان تخفیف", discountCountdownLabel(course(120_000L), now))
        assertEquals("کمتر از یک دقیقه تا پایان تخفیف", discountCountdownLabel(course(59_999L), now))
    }
    @Test fun hidesExpiredMissingOrNonDiscountedPrices() {
        assertNull(discountCountdownLabel(course(0), now))
        assertNull(discountCountdownLabel(course(-1), now))
        assertNull(discountCountdownLabel(course(1000).copy(discountEndsAtMillis = null), now))
        assertNull(discountCountdownLabel(course(1000).copy(compareAtToman = null), now))
        assertNull(discountCountdownLabel(course(1000).copy(compareAtToman = 75000), now))
        assertNull(discountCountdownLabel(course(1000).copy(serverPriceToman = null), now))
    }
    @Test fun displayDoesNotChangePriceAndUsesAbsoluteDeadline() {
        val discounted = course(86_400_000L)
        assertEquals("۱ روز تا پایان تخفیف", discountCountdownLabel(discounted, now))
        assertEquals("۲۳ ساعت تا پایان تخفیف", discountCountdownLabel(discounted, now + 3_600_000L))
        assertEquals(75000L, discounted.serverPriceToman)
    }
}
