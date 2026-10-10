package ir.behnamapps.fascratch.inappbilling

import ir.behnamapps.fascratch.inappbilling.learning.AccountAuthPolicy
import org.junit.Assert.*
import org.junit.Test

class AccountAuthPolicyTest {
    @Test fun oldGuestResponsesCannotReplaceANewerSignedInSession() {
        assertTrue(AccountAuthPolicy.sameSession(null, "new-session"))
        assertTrue(AccountAuthPolicy.sameSession("same-session", "same-session"))
        assertFalse(AccountAuthPolicy.sameSession("guest-session", "signed-in-session"))
    }
    @Test fun googleStatusDistinguishesOldServerFromMissingConfiguration() {
        assertNull(AccountAuthPolicy.googleUnavailableMessage(true, true))
        val oldServer = AccountAuthPolicy.googleUnavailableMessage(false, false)
        val unconfigured = AccountAuthPolicy.googleUnavailableMessage(true, false)
        assertTrue(oldServer!!.contains("به‌روز"))
        assertTrue(unconfigured!!.contains("تنظیم"))
        assertNotEquals(oldServer, unconfigured)
    }
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
    @Test fun registrationRequiresFourValidFieldsAndTheLatestAvailabilityResult() {
        val password = "a long secret phrase"
        assertTrue(AccountAuthPolicy.registration("scratch_star", "ستاره", password, password, true, "scratch_star"))
        assertFalse(AccountAuthPolicy.registration("scratch_star", "", password, password, true, "scratch_star"))
        assertFalse(AccountAuthPolicy.registration("scratch_star", "<bad>", password, password, true, "scratch_star"))
        assertFalse(AccountAuthPolicy.registration("scratch_star", "ستاره", password, "different", true, "scratch_star"))
        assertFalse(AccountAuthPolicy.registration("scratch_star", "ستاره", password, password, false, "scratch_star"))
        assertFalse(AccountAuthPolicy.registration("scratch_star", "ستاره", password, password, null, null))
        assertFalse(AccountAuthPolicy.registration("new_name", "ستاره", password, password, true, "old_name"))
    }
}
