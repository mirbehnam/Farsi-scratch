package ir.behnamapps.fascratch.inappbilling

import org.junit.Assert.*
import org.junit.Test

class PurchaseTrackingTest {
    @Test fun public_account_reference_is_stable_but_each_new_attempt_is_unique() {
        val profile = "25a2245e-4d74-4dfa-9b39-adb62dc16a2a"
        val first = PurchaseTracking.payload(profile)
        val second = PurchaseTracking.payload(profile)
        assertTrue(first.startsWith("fs1:$profile:"))
        assertNotEquals(first, second)
        assertEquals(77, first.length)
    }
    @Test(expected = IllegalArgumentException::class) fun credentials_cannot_be_used_as_account_reference() {
        PurchaseTracking.payload("a".repeat(64))
    }
}
