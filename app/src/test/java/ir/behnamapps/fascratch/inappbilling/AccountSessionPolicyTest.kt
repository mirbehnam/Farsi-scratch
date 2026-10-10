package ir.behnamapps.fascratch.inappbilling

import ir.behnamapps.fascratch.inappbilling.learning.AccountSessionPolicy
import org.junit.Assert.*
import org.junit.Test

class AccountSessionPolicyTest {
    @Test fun onlyExplicitSessionEndedCanLogOutAnExistingAccount() {
        assertTrue(AccountSessionPolicy.ended(401, "account_session_ended", true))
        assertFalse(AccountSessionPolicy.ended(401, "", true))
        assertFalse(AccountSessionPolicy.ended(503, "account_session_ended", true))
        assertFalse(AccountSessionPolicy.ended(null, "", true))
        assertFalse(AccountSessionPolicy.ended(401, "account_session_ended", false))
    }
}
