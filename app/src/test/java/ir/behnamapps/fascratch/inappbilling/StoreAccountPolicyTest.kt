package ir.behnamapps.fascratch.inappbilling

import ir.behnamapps.fascratch.inappbilling.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class StoreAccountPolicyTest {
    @Test fun onlyConfirmedSignedOutStatusRequiresLogin() {
        assertTrue(StoreAccountPolicy.requiresLogin(false))
        assertFalse(StoreAccountPolicy.requiresLogin(true))
        assertFalse(StoreAccountPolicy.requiresLogin(null))
        for (code in listOf("", "unauthorized", "network", "not_owned", "rejected")) assertFalse(StoreAccountPolicy.hasHelp(code))
    }

    @Test fun storeAccountErrorsNeverRevokeExistingPaidAccess() {
        for (code in listOf(StoreAccountPolicy.LOGIN_REQUIRED, StoreAccountPolicy.NOT_INSTALLED, StoreAccountPolicy.UPDATE_REQUIRED)) {
            assertTrue(StoreAccountPolicy.hasHelp(code))
            assertFalse(CoursePolicy.revokesAccess(code))
        }
    }

    @Test fun signedOutInventoryCannotStartPaymentOrSendAnOldReceipt() = runBlocking {
        val course = Course("course", "", "", "scratch_basic")
        val billing = object : BillingGateway {
            override val provider = "cafebazaar"
            override suspend fun owned(sku: String): Receipt? = throw CourseFailure("login", code = StoreAccountPolicy.LOGIN_REQUIRED)
            override suspend fun purchase(sku: String): Receipt = error("Must not start payment")
            override fun close() = Unit
        }
        val backend = object : PurchaseBackend {
            override suspend fun verify(course: Course, provider: String, receipt: Receipt, restore: Boolean): CourseAccess = error("Must not verify an old receipt")
        }
        val vault = object : ReceiptStore {
            override fun saveReceipt(courseId: String, receipt: Receipt) = error("Must not overwrite receipt")
            override fun receipt(courseId: String) = Receipt("scratch_basic", "old-receipt")
            override fun saveAccess(access: CourseAccess) = error("Must not overwrite access")
        }
        for (restoreOnly in listOf(false, true)) {
            try {
                BuyCourse(backend, vault).execute(course, billing, restoreOnly)
                fail("Signed-out inventory accepted")
            } catch (error: CourseFailure) {
                assertEquals(StoreAccountPolicy.LOGIN_REQUIRED, error.code)
            }
        }
    }
}
