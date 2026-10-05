package ir.behnamapps.fascratch.inappbilling

import ir.behnamapps.fascratch.inappbilling.domain.Course
import ir.behnamapps.fascratch.inappbilling.presentation.discountCountdownLabel
import org.junit.Assert.*
import org.junit.Test

class DiscountCountdownTest {
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
