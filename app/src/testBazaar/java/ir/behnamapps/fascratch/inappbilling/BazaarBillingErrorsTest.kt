package ir.behnamapps.fascratch.inappbilling

import ir.behnamapps.fascratch.inappbilling.domain.StoreAccountPolicy
import ir.cafebazaar.poolakey.exception.*
import org.junit.Assert.*
import org.junit.Test

class BazaarBillingErrorsTest {
    @Test fun knownInstallationAndCompatibilityErrorsHaveDedicatedHelp() {
        assertEquals(StoreAccountPolicy.NOT_INSTALLED, bazaarBillingFailure(BazaarNotFoundException(), "fallback").code)
        assertEquals(StoreAccountPolicy.UPDATE_REQUIRED, bazaarBillingFailure(BazaarNotSupportedException(), "fallback").code)
        assertEquals(StoreAccountPolicy.UPDATE_REQUIRED, bazaarBillingFailure(IAPNotSupportedException(), "fallback").code)
    }

    @Test fun ambiguousSdkResultsAndSignatureErrorsAreNeverCalledLoggedOut() {
        for (error in listOf(ResultNotOkayException(), IllegalStateException("not logged in"), PurchaseHijackedException())) {
            val failure = bazaarBillingFailure(error, "fallback")
            assertEquals("fallback", failure.userMessage)
            assertFalse(StoreAccountPolicy.hasHelp(failure.code))
        }
    }
}
