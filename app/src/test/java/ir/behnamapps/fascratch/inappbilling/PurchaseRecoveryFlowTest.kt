package ir.behnamapps.fascratch.inappbilling

import ir.behnamapps.fascratch.inappbilling.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PurchaseRecoveryFlowTest {
    @Test fun restorationIsVisibleOnlyAfterAValidStoreReceiptAndBeforeServerConfirmation() = runBlocking {
        val course = Course("course", "Course", "", "sku")
        for (token in listOf<String?>(null, "", "store-receipt")) {
            var shown = false
            val vault = object : ReceiptStore {
                override fun receipt(courseId: String): Receipt? = null
                override fun saveReceipt(courseId: String, receipt: Receipt) = Unit
                override fun saveAccess(access: CourseAccess) = Unit
            }
            val billing = object : BillingGateway {
                override val provider = "cafebazaar"
                override suspend fun owned(sku: String) = token?.let { Receipt(sku, it) }
                override suspend fun purchase(sku: String): Receipt = error("Restore must not charge")
                override fun close() = Unit
            }
            val backend = object : PurchaseBackend {
                override suspend fun verify(course: Course, provider: String, receipt: Receipt, restore: Boolean): CourseAccess {
                    assertTrue("Show progress before starting server verification", shown)
                    throw CourseFailure("Server temporarily unavailable", 503)
                }
            }
            try { BuyCourse(backend, vault, onReceiptFound = { shown = true }).execute(course, billing, true) }
            catch (_: CourseFailure) { }
            assertEquals(token == "store-receipt", shown)
        }
    }
    @Test fun automaticDiscoveryAlsoShowsTheAlreadyOwnedReceiptBeforeVerification() = runBlocking {
        var shown = false
        val course = Course("course", "Course", "", "sku")
        val vault = object : ReceiptStore {
            override fun receipt(courseId: String): Receipt? = null
            override fun saveReceipt(courseId: String, receipt: Receipt) = Unit
            override fun saveAccess(access: CourseAccess) = Unit
        }
        val billing = object : BillingGateway {
            override val provider = "myket"
            override suspend fun owned(sku: String) = Receipt(sku, "owned-receipt")
            override suspend fun purchase(sku: String): Receipt = error("No payment")
            override fun close() = Unit
        }
        val backend = object : PurchaseBackend {
            override suspend fun verify(course: Course, provider: String, receipt: Receipt, restore: Boolean): CourseAccess {
                assertTrue(shown)
                return CourseAccess(course.id, "verified", 10000)
            }
        }
        assertNotNull(BuyCourse(backend, vault, onReceiptFound = { shown = true }).reconcile(course, billing))
    }
}
