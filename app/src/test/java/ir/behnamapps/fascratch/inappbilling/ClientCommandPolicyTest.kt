package ir.behnamapps.fascratch.inappbilling

import ir.behnamapps.fascratch.inappbilling.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ClientCommandPolicyTest {
    @Test fun exactVersionDoesNotDisableNeighbouringVersions() {
        val rule = TrainingVersionRule("all", 42, 42, true, "لطفاً بروزرسانی کنید")
        assertEquals(rule.message, ClientCommandPolicy.blockedMessage(false, "", listOf(rule), "bazaar", 42))
        assertNull(ClientCommandPolicy.blockedMessage(false, "", listOf(rule), "bazaar", 41))
        assertNull(ClientCommandPolicy.blockedMessage(false, "", listOf(rule), "myket", 43))
    }

    @Test fun rangesIncludeBothEndsAndOnlyMatchTheirStore() {
        val rule = TrainingVersionRule("myket", 10, 20, true, "بروزرسانی")
        for (version in listOf(10, 15, 20)) assertEquals("بروزرسانی", ClientCommandPolicy.blockedMessage(false, "تعمیر", listOf(rule), "myket", version))
        assertNull(ClientCommandPolicy.blockedMessage(false, "تعمیر", listOf(rule), "myket", 21))
        assertNull(ClientCommandPolicy.blockedMessage(false, "تعمیر", listOf(rule), "bazaar", 15))
        assertNull(ClientCommandPolicy.blockedMessage(false, "تعمیر", listOf(rule.copy(enabled = false)), "myket", 15))
        assertEquals("تعمیر", ClientCommandPolicy.blockedMessage(true, "تعمیر", listOf(rule), "bazaar", 50))
        assertEquals("بروزرسانی", ClientCommandPolicy.blockedMessage(false, "تعمیر", listOf(rule.copy(store = "all")), "website", 10))
    }

    @Test fun eachCacheGenerationIsAppliedOnceAndOldCommandsAreIgnored() {
        assertTrue(ClientCommandPolicy.needsCacheClear(2, 1))
        assertFalse(ClientCommandPolicy.needsCacheClear(2, 2))
        assertFalse(ClientCommandPolicy.needsCacheClear(1, 2))
        assertFalse(ClientCommandPolicy.needsCacheClear(0, 0))
    }

    @Test fun pausedSalesDoNotStartPaymentButExistingReceiptStillRestores() = runBlocking {
        val course = Course("course", "", "", "scratch_basic", salesPaused = true)
        var owned: Receipt? = null
        var verified = false
        val gateway = object : BillingGateway {
            override val provider = "myket"
            override suspend fun owned(sku: String) = owned
            override suspend fun purchase(sku: String): Receipt { fail("Payment must not start"); error("unreachable") }
            override fun close() = Unit
        }
        val backend = object : PurchaseBackend {
            override suspend fun verify(course: Course, provider: String, receipt: Receipt, restore: Boolean): CourseAccess {
                assertTrue(restore); verified = true; return CourseAccess(course.id, "token", 999999)
            }
        }
        val vault = object : ReceiptStore {
            override fun saveReceipt(courseId: String, receipt: Receipt) = Unit
            override fun receipt(courseId: String): Receipt? = null
            override fun saveAccess(access: CourseAccess) = Unit
        }
        val buy = BuyCourse(backend, vault)
        try { buy.execute(course, gateway, false); fail("Paused sale accepted") }
        catch (e: CourseFailure) { assertEquals("sales_paused", e.code) }
        assertFalse(verified)
        owned = Receipt(course.sku, "real-receipt")
        assertEquals(course.id, buy.execute(course, gateway, false).courseId)
        assertTrue(verified)
        verified = false
        buy.execute(course, gateway, true)
        assertTrue(verified)
    }
}
