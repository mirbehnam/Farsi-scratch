package ir.behnamapps.fascratch.inappbilling

import ir.behnamapps.fascratch.inappbilling.learning.AccountAuthPolicy
import org.junit.Assert.*
import org.junit.Test

class AccountAuthPolicyTest {
    @Test fun usernameIsUnambiguousAndPasswordAllowsPassphrases() {
        assertTrue(AccountAuthPolicy.username(" Scratch_Star "))
        assertFalse(AccountAuthPolicy.username("ab"))
        assertFalse(AccountAuthPolicy.username("نام فارسی"))
        assertTrue(AccountAuthPolicy.password("a long secret phrase", true))
        assertFalse(AccountAuthPolicy.password("short", true))
        assertFalse(AccountAuthPolicy.password("ب".repeat(40), true))
        assertTrue(AccountAuthPolicy.password("short", false))
        assertFalse(AccountAuthPolicy.password("", false))
    }
    @Test fun sessionSecretsAreRandomAndNotDeviceIdentifiers() {
        val first = AccountAuthPolicy.secret()
        assertTrue(first.matches(Regex("[a-f0-9]{64}")))
        assertNotEquals(first, AccountAuthPolicy.secret())
    }
}
