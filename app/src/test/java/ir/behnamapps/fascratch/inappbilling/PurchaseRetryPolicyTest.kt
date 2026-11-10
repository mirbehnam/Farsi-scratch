package ir.behnamapps.fascratch.inappbilling

import ir.behnamapps.fascratch.inappbilling.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PurchaseRetryPolicyTest {
    @Test fun manualRestoreIsNotBlockedByBackgroundBackoffAfterAnAccountSwitch() {
        assertTrue(PurchaseRetryPolicy.waitsForConfirmation(false, 1000, 9000, 10000, 2, false))
        assertTrue(PurchaseRetryPolicy.waitsForConfirmation(false, 1000, 0, 10000, 2, false))
        assertFalse(PurchaseRetryPolicy.waitsForConfirmation(true, 1000, 9000, 10000, 2, false))
        assertFalse(PurchaseRetryPolicy.waitsForConfirmation(true, 1000, 9000, 10000, 2, true))
        assertFalse(PurchaseRetryPolicy.waitsForConfirmation(false, 10000, 9000, 10000, 2, false))
    }
    @Test fun freshStoreReceiptUsesExplicitRecoveryOnTheFirstRestoreClickForBothStores() = runBlocking {
        for (provider in listOf("cafebazaar", "myket")) {
            val course = Course("course", "", "", "sku")
            var receipt: Receipt? = null
            var savedAccess: CourseAccess? = null
            var restores = 0
            val store = object : ReceiptStore {
                override fun saveReceipt(courseId: String, value: Receipt) { receipt = value }
                override fun receipt(courseId: String) = receipt
                override fun saveAccess(access: CourseAccess) { savedAccess = access }
            }
            val billing = object : BillingGateway {
                override val provider = provider
                override suspend fun owned(sku: String) = Receipt(sku, "sdk-receipt")
                override suspend fun purchase(sku: String): Receipt = error("Restore must never charge")
                override fun close() = Unit
            }
            val backend = object : PurchaseBackend {
                override suspend fun verify(course: Course, provider: String, receipt: Receipt, restore: Boolean): CourseAccess = error("Must explicitly recover")
                override suspend fun retryPending(course: Course, provider: String, value: Receipt, explicitRestore: Boolean): CourseAccess {
                    assertTrue(explicitRestore); assertEquals(receipt, value); restores++
                    return CourseAccess(course.id, "verified-access", 10000, "original-owner")
                }
            }
            BuyCourse(backend, store).execute(course, billing, true)
            assertEquals(1, restores)
            assertEquals("verified-access", savedAccess?.token)
        }
    }
    @Test fun switchedAccountRestorationRefreshesSdkReceiptInsteadOfUsingStalePendingReceipt() = runBlocking {
        val course = Course("course", "", "", "sku")
        var discovered = false
        val store = object : ReceiptStore {
            override fun hasPendingReceipt(courseId: String) = true
            override fun requiresStoreRediscovery(courseId: String) = true
            override fun receipt(courseId: String) = Receipt("sku", "stale-token", "old-owner")
            override fun saveReceipt(courseId: String, receipt: Receipt) = error("Use fresh discovery")
            override fun saveDiscoveredReceipt(course: Course, provider: String, receipt: Receipt) { assertEquals("current-token", receipt.token); discovered = true }
            override fun saveAccess(access: CourseAccess) = Unit
        }
        val billing = object : BillingGateway {
            override val provider = "cafebazaar"
            override suspend fun owned(sku: String) = Receipt(sku, "current-token")
            override suspend fun purchase(sku: String): Receipt = error("No payment")
            override fun close() = Unit
        }
        val backend = object : PurchaseBackend {
            override suspend fun verify(course: Course, provider: String, receipt: Receipt, restore: Boolean): CourseAccess = error("Use explicit recovery")
            override suspend fun retryPending(course: Course, provider: String, receipt: Receipt, explicitRestore: Boolean): CourseAccess {
                assertTrue(discovered); assertTrue(explicitRestore); assertEquals("current-token", receipt.token)
                return CourseAccess(course.id, "verified-access", 10000)
            }
        }
        assertNotNull(BuyCourse(backend, store).execute(course, billing, true))
    }
    @Test fun changedAccountRequiresFreshSdkDiscoveryBeforeSubmittingToCurrentAccount() = runBlocking {
        val course = Course("course", "", "", "sku")
        var queried = false
        var rediscovered = false
        val billing = object : BillingGateway {
            override val provider = "cafebazaar"
            override suspend fun owned(sku: String): Receipt { queried = true; return Receipt(sku, "fresh-sdk-proof") }
            override suspend fun purchase(sku: String): Receipt = error("No payment")
            override fun close() = Unit
        }
        val store = object : ReceiptStore {
            override fun hasPendingReceipt(courseId: String) = true
            override fun requiresStoreRediscovery(courseId: String) = true
            override fun receipt(courseId: String) = Receipt("sku", "old-account-receipt")
            override fun saveReceipt(courseId: String, receipt: Receipt) = error("Must use SDK discovery")
            override fun saveDiscoveredReceipt(course: Course, provider: String, receipt: Receipt) {
                assertTrue(queried); assertEquals("fresh-sdk-proof", receipt.token); rediscovered = true
            }
            override fun saveAccess(access: CourseAccess) = Unit
        }
        val backend = object : PurchaseBackend {
            override suspend fun verify(course: Course, provider: String, receipt: Receipt, restore: Boolean): CourseAccess = error("No implicit recovery")
            override suspend fun retryPending(course: Course, provider: String, receipt: Receipt, explicitRestore: Boolean): CourseAccess = error("Old receipt must not be rebound")
            override suspend fun reconcile(course: Course, provider: String, receipt: Receipt): CourseAccess {
                assertTrue(rediscovered); return CourseAccess(course.id, "access", 123)
            }
        }
        assertNotNull(BuyCourse(backend, store).reconcile(course, billing))
    }
    @Test fun sdkDiscoveryFindsAPurchaseWithoutAnyLocalReceiptAndOnlyServerCanGrantAccess() = runBlocking {
        val course = Course("course", "", "", "sku")
        var saved: Receipt? = null
        var granted = false
        var queued = false
        var paid = false
        val store = object : ReceiptStore {
            override fun saveReceipt(courseId: String, receipt: Receipt) { saved = receipt }
            override fun savePendingReceipt(course: Course, provider: String, receipt: Receipt) { saved = receipt; queued = true }
            override fun receipt(courseId: String) = saved
            override fun saveAccess(access: CourseAccess) { granted = true }
        }
        val billing = object : BillingGateway {
            override val provider = "myket"
            override suspend fun owned(sku: String) = Receipt(sku, "old-store-purchase")
            override suspend fun purchase(sku: String): Receipt { paid = true; error("No payment allowed") }
            override fun close() = Unit
        }
        var available = false
        val backend = object : PurchaseBackend {
            override suspend fun verify(course: Course, provider: String, receipt: Receipt, restore: Boolean): CourseAccess = error("Must use safe reconciliation")
            override suspend fun reconcile(course: Course, provider: String, receipt: Receipt): CourseAccess {
                assertTrue(queued); assertSame(saved, receipt)
                if (!available) throw CourseFailure("limit", 429)
                return CourseAccess(course.id, "server-access", 123)
            }
        }
        try { BuyCourse(backend, store).reconcile(course, billing); fail("Should remain unconfirmed") }
        catch (expected: CourseFailure) { assertEquals(429, expected.status) }
        assertNotNull(saved); assertFalse(granted); assertFalse(paid)
        available = true
        assertNotNull(BuyCourse(backend, store).reconcile(course, billing))
        assertTrue(granted); assertFalse(paid)
    }
    @Test fun emptyInventoryOrInventoryFailureNeverOpensPaymentOrRemovesExistingAccess() = runBlocking {
        var empty = true
        val billing = object : BillingGateway {
            override val provider = "cafebazaar"
            override suspend fun owned(sku: String): Receipt? { if (empty) return null else throw CourseFailure("SDK offline") }
            override suspend fun purchase(sku: String): Receipt = error("No payment allowed")
            override fun close() = Unit
        }
        val store = object : ReceiptStore {
            override fun saveReceipt(courseId: String, receipt: Receipt) = error("Nothing found")
            override fun receipt(courseId: String): Receipt? = null
            override fun saveAccess(access: CourseAccess) = error("No server verification")
        }
        val backend = object : PurchaseBackend {
            override suspend fun verify(course: Course, provider: String, receipt: Receipt, restore: Boolean): CourseAccess = error("No receipt")
        }
        val buy = BuyCourse(backend, store)
        val course = Course("course", "", "", "sku")
        assertNull(buy.reconcile(course, billing))
        empty = false
        try { buy.reconcile(course, billing); fail("SDK failure must remain a failure") }
        catch (expected: CourseFailure) { assertEquals("SDK offline", expected.userMessage) }
    }
    @Test fun securityAndNetworkDelaysAreRetryableWithoutWeakeningEntitlements() {
        for (failure in listOf(CourseFailure("offline"), CourseFailure("limit", 429), CourseFailure("busy", 503),
            CourseFailure("blocked", 403, "ip_blocked"), CourseFailure("timeout", 408))) {
            assertTrue(PurchaseRetryPolicy.retryable(failure))
            assertFalse(CoursePolicy.revokesAccess(failure.code))
        }
        for (failure in listOf(CourseFailure("disabled", 403, "access_disabled"), CourseFailure("refund", 422, "refunded"),
            CourseFailure("invalid", 422, "rejected"), CourseFailure("login", 401),
            CourseFailure("owner", 409, "purchase_owner_mismatch"), CourseFailure("unknown", 404))) {
            assertFalse(PurchaseRetryPolicy.retryable(failure))
        }
    }
    @Test fun serverCooldownAlwaysWinsEvenWhenLongerThanLocalMaximum() {
        assertEquals(30_000L, PurchaseRetryPolicy.delayMillis(0, null))
        assertEquals(120_000L, PurchaseRetryPolicy.delayMillis(2, null))
        assertEquals(86_400_000L, PurchaseRetryPolicy.delayMillis(2, 86400))
        assertTrue(PurchaseRetryPolicy.delayMillis(999, null) <= 6 * 60 * 60_000L)
        assertTrue(PurchaseRetryPolicy.delayMillis(1, Long.MAX_VALUE) > 0)
    }
    @Test fun retryAfterSupportsSecondsHttpDateAndJsonTakingTheLongest() {
        assertEquals(120L, PurchaseRetryPolicy.retryAfter("120", 10, 0))
        assertEquals(180L, PurchaseRetryPolicy.retryAfter("120", 180, 0))
        assertEquals(60L, PurchaseRetryPolicy.retryAfter("Thu, 01 Jan 1970 00:01:00 GMT", null, 0))
        assertEquals(0L, PurchaseRetryPolicy.retryAfter("Thu, 01 Jan 1970 00:01:00 GMT", null, 120_000))
        assertEquals(30L, PurchaseRetryPolicy.retryAfter("invalid", 30, 0))
        assertNull(PurchaseRetryPolicy.retryAfter("-1", null, 0))
    }
    @Test fun accountSwitchOrEndedSessionCannotAutomaticallyRecoverReceipts() {
        assertTrue(PurchaseRetryPolicy.sameOwner("owner", "owner", false))
        assertFalse(PurchaseRetryPolicy.sameOwner("owner", "other", false))
        assertFalse(PurchaseRetryPolicy.sameOwner("owner", "owner", true))
        assertFalse(PurchaseRetryPolicy.sameOwner("owner", null, false))
    }
    @Test fun pendingPaidReceiptNeverQueriesInventoryOrOpensAnotherPayment() = runBlocking {
        val course = Course("course", "", "", "sku")
        val receipt = Receipt("sku", "private-receipt")
        var grants = 0
        val store = object : ReceiptStore {
            override fun hasPendingReceipt(courseId: String) = true
            override fun receipt(courseId: String) = receipt
            override fun saveReceipt(courseId: String, receipt: Receipt) = error("Must reuse durable receipt")
            override fun saveAccess(access: CourseAccess) { grants++ }
        }
        val gateway = object : BillingGateway {
            override val provider = "cafebazaar"
            override suspend fun owned(sku: String): Receipt? = error("Inventory must not run")
            override suspend fun purchase(sku: String): Receipt = error("Must not charge twice")
            override fun close() = Unit
        }
        var fail = true
        val backend = object : PurchaseBackend {
            override suspend fun verify(course: Course, provider: String, receipt: Receipt, restore: Boolean): CourseAccess {
                assertTrue(restore)
                if (fail) throw CourseFailure("wait", 429, retryAfterSeconds = 120)
                return CourseAccess(course.id, "access", 1)
            }
        }
        try { BuyCourse(backend, store).execute(course, gateway, false); fail("Must remain pending") }
        catch (expected: CourseFailure) { assertEquals(429, expected.status) }
        assertEquals(0, grants)
        fail = false
        assertEquals(course.id, BuyCourse(backend, store).execute(course, gateway, false).courseId)
        assertEquals(1, grants)
    }
}
