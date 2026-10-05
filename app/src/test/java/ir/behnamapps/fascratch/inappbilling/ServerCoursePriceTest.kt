package ir.behnamapps.fascratch.inappbilling

import ir.behnamapps.fascratch.inappbilling.domain.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ServerCoursePriceTest {
    @Test fun displayDiscountExpiresWithoutChangingMainPrice() {
        val discounted = Course("id", "", "", "sku", serverPriceToman = 75000, compareAtToman = 100000, discountEndsAtMillis = 2000)
        assertTrue(CoursePolicy.discountVisible(discounted, 1999))
        assertFalse(CoursePolicy.discountVisible(discounted, 2000))
        assertEquals(75000L, discounted.serverPriceToman)
        assertTrue(CoursePolicy.discountVisible(discounted.copy(discountEndsAtMillis = null), 99999))
        assertFalse(CoursePolicy.discountVisible(discounted.copy(compareAtToman = 75000), 1000))
        assertFalse(CoursePolicy.discountVisible(discounted.copy(compareAtToman = null), 1000))
    }
    private val course = Course("ae6b3b4c-1d11-43fe-bd17-1c80ad234666", "", "", "scratch_basic")

    @Test fun serverPriceKeepsZeroAndMissingDistinctAndRejectsMismatchedSkuOrInvalidAmount() {
        assertEquals(75000L, CoursePolicy.serverPrice(course.sku, course.sku, 75000))
        assertEquals(0L, CoursePolicy.serverPrice(course.sku, course.sku, 0L))
        assertNull(CoursePolicy.serverPrice(course.sku, null, null))
        assertNull(CoursePolicy.serverPrice(course.sku, course.sku, null))
        for (invalid in listOf(-1L, 1_000_000_000_001L, 1.5, "75000")) {
            try { CoursePolicy.serverPrice(course.sku, course.sku, invalid); fail("Invalid price accepted") }
            catch (_: IllegalArgumentException) { }
        }
        try { CoursePolicy.serverPrice(course.sku, "other_sku", 75000L); fail("Wrong SKU accepted") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun newRefreshCannotBeOverwrittenByOlderZeroResponse() = runBlocking {
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val results = mutableListOf<String?>(); var calls = 0
        val refresh = CoursePriceRefresher(this, {
            calls++
            if (calls == 1) { started.complete(Unit); release.await(); "0 تومان" } else "75,000 تومان"
        }, {}, { _, price -> results += price })
        refresh.refresh(listOf(course)); started.await()
        refresh.refresh(listOf(course)); release.complete(Unit); refresh.awaitIdle()
        assertEquals(2, calls); assertEquals(listOf("75,000 تومان"), results)
    }

    @Test fun failedServerRefreshDoesNotKeepOutdatedPrice() = runBlocking {
        var calls = 0; var displayed: String? = "old"
        val refresh = CoursePriceRefresher(this, { calls++; if (calls == 1) "75,000 تومان" else throw CourseFailure("Offline") },
            { displayed = null }, { _, price -> displayed = price })
        refresh.refresh(listOf(course)); assertNull(displayed); refresh.awaitIdle(); assertEquals("75,000 تومان", displayed)
        refresh.refresh(listOf(course)); refresh.awaitIdle(); assertNull(displayed)
    }

    @Test fun unvalidatedOrDisconnectedNetworksCannotStartPurchases() {
        assertFalse(CoursePolicy.purchaseNetworkAvailable(false, false))
        assertFalse(CoursePolicy.purchaseNetworkAvailable(true, false))
        assertFalse(CoursePolicy.purchaseNetworkAvailable(false, true))
        assertTrue(CoursePolicy.purchaseNetworkAvailable(true, true))
    }
}
