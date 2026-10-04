package ir.behnamapps.fascratch.inappbilling

import ir.behnamapps.fascratch.inappbilling.domain.*
import ir.behnamapps.fascratch.BuildConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PurchaseFlowTest {
    @Test fun configuredStorePublicKeyIsValidRsa() {
        if (BuildConfig.BILLING_PROVIDER == "website") return
        val bytes = java.util.Base64.getDecoder().decode(BuildConfig.BILLING_PUBLIC_KEY)
        val key = java.security.KeyFactory.getInstance("RSA").generatePublic(java.security.spec.X509EncodedKeySpec(bytes))
        assertTrue((key as java.security.interfaces.RSAPublicKey).modulus.bitLength() >= 1024)
    }
    private val course = Course("ae6b3b4c-1d11-43fe-bd17-1c80ad234666", "Scratch", "", "scratch_basic")
    private class Store : ReceiptStore {
        var saved: Receipt? = null
        var granted: CourseAccess? = null
        override fun saveReceipt(courseId: String, receipt: Receipt) { saved = receipt }
        override fun receipt(courseId: String) = saved
        override fun saveAccess(access: CourseAccess) { granted = access }
    }
    private class Gateway(var existing: Receipt? = null, var queryFails: Boolean = false) : BillingGateway {
        override val provider = "cafebazaar"
        var purchaseCalls = 0
        override suspend fun owned(sku: String): Receipt? {
            if (queryFails) throw CourseFailure("Store unavailable")
            return existing
        }
        override suspend fun price(sku: String) = "100"
        override suspend fun purchase(sku: String): Receipt { purchaseCalls++; return Receipt(sku, "new-receipt") }
        override fun close() = Unit
    }

    @Test fun existingPurchaseRestoresWithoutChargingAgain() = runBlocking {
        val store = Store()
        val gateway = Gateway(Receipt(course.sku, "original"))
        var restored = false
        val backend = object : PurchaseBackend {
            override suspend fun verify(course: Course, provider: String, receipt: Receipt, restore: Boolean): CourseAccess {
                assertSame(store.saved, receipt); restored = restore
                return CourseAccess(course.id, "access", 10)
            }
        }
        BuyCourse(backend, store).execute(course, gateway, false)
        assertTrue(restored); assertEquals(0, gateway.purchaseCalls); assertNotNull(store.granted)
    }

    @Test fun receiptSurvivesServerOutageButAccessIsNotGranted() = runBlocking {
        val store = Store(); val gateway = Gateway()
        val backend = object : PurchaseBackend {
            override suspend fun verify(course: Course, provider: String, receipt: Receipt, restore: Boolean): CourseAccess = throw CourseFailure("Server unavailable", 503)
        }
        try { BuyCourse(backend, store).execute(course, gateway, false); fail("Expected failure") } catch (_: CourseFailure) { }
        assertEquals(1, gateway.purchaseCalls); assertEquals("new-receipt", store.saved?.token); assertNull(store.granted)
    }

    @Test fun failedInventoryNeverStartsNewPurchase() = runBlocking {
        val gateway = Gateway(queryFails = true)
        val backend = object : PurchaseBackend {
            override suspend fun verify(course: Course, provider: String, receipt: Receipt, restore: Boolean): CourseAccess { error("Must not verify") }
        }
        try { BuyCourse(backend, Store()).execute(course, gateway, false); fail("Expected failure") } catch (_: CourseFailure) { }
        assertEquals(0, gateway.purchaseCalls)
    }

    @Test fun restoreOnlyNeverBuysWhenNothingIsOwned() = runBlocking {
        val gateway = Gateway()
        val backend = object : PurchaseBackend {
            override suspend fun verify(course: Course, provider: String, receipt: Receipt, restore: Boolean): CourseAccess { error("Must not verify") }
        }
        try { BuyCourse(backend, Store()).execute(course, gateway, true); fail("Expected failure") } catch (error: CourseFailure) { assertEquals("not_owned", error.code) }
        assertEquals(0, gateway.purchaseCalls)
    }

    @Test fun mismatchedReceiptCannotGrantAccess() = runBlocking {
        val store = Store(); val gateway = Gateway(Receipt("other", "receipt"))
        val backend = object : PurchaseBackend {
            override suspend fun verify(course: Course, provider: String, receipt: Receipt, restore: Boolean): CourseAccess { error("Must not verify") }
        }
        try { BuyCourse(backend, store).execute(course, gateway, false); fail("Expected failure") } catch (_: CourseFailure) { }
        assertNull(store.saved); assertNull(store.granted)
    }

    @Test fun wrongCourseResponseIsNotPersistedAsAccess() = runBlocking {
        val store = Store()
        val backend = object : PurchaseBackend {
            override suspend fun verify(course: Course, provider: String, receipt: Receipt, restore: Boolean) = CourseAccess("another-course", "secret", 1)
        }
        try { BuyCourse(backend, store).execute(course, Gateway(), false); fail("Expected failure") } catch (_: CourseFailure) { }
        assertNotNull(store.saved); assertNull(store.granted)
    }

    @Test fun credentialsAreNotPrintedByToString() {
        assertFalse(Receipt("sku", "receipt-secret").toString().contains("receipt-secret"))
        assertFalse(CourseAccess(course.id, "access-secret", 1).toString().contains("access-secret"))
    }

    @Test fun safeDownloadNamesAndSameOriginAreEnforced() {
        val lesson = Lesson(course.id, course.id, "", "", 1, 10, "a".repeat(64))
        assertTrue(CoursePolicy.downloadKey(lesson).endsWith(".mp4"))
        assertNotEquals(CoursePolicy.downloadKey(lesson), CoursePolicy.downloadKey(lesson.copy(version = 2)))
        for (invalid in listOf(lesson.copy(id = "../escape"), lesson.copy(sha256 = "bad"), lesson.copy(bytes = 0), lesson.copy(version = -1))) {
            try { CoursePolicy.downloadKey(invalid); fail("Expected invalid metadata") } catch (_: IllegalArgumentException) { }
        }
        assertTrue(CoursePolicy.sameOrigin("https://api.behnamapp.ir/scratch/v1", "https://api.behnamapp.ir/scratch/v1/media"))
        assertFalse(CoursePolicy.sameOrigin("https://api.behnamapp.ir", "http://api.behnamapp.ir"))
        assertFalse(CoursePolicy.sameOrigin("https://api.behnamapp.ir", "https://evil.test"))
        assertFalse(CoursePolicy.sameOrigin("https://api.behnamapp.ir", "https://user@api.behnamapp.ir"))
    }
}
