package ir.behnamapps.fascratch.inappbilling.learning

import android.content.Context
import org.json.JSONObject
import java.security.SecureRandom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ir.behnamapps.fascratch.inappbilling.data.PurchaseVault

/** Private installation credential; never an IMEI/store-user identifier. Excluded from backup. */
class LearningIdentity private constructor(context: Context) {
    private val store = LearningStore(context.applicationContext)
    private val app = context.applicationContext
    @Volatile private var recoveryRequired = store.grant(KEY)?.optBoolean("requires_recovery") == true
    private val mutableChanges = MutableStateFlow(0L)
    val changes = mutableChanges.asStateFlow()
    fun requiresRecovery(): Boolean = recoveryRequired
    @Synchronized fun secret(): String {
        store.grant(KEY)?.optString("secret")?.takeIf { it.matches(Regex("[a-f0-9]{64}")) }?.let { return it }
        val secret = ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it.toInt() and 255) }
        // Persist before first request so a response lost in transit cannot duplicate registration.
        store.saveGrant(KEY, JSONObject().put("secret", secret))
        return secret
    }
    @Synchronized fun accept(account: JSONObject?, expectedSecret: String? = null): Boolean {
        // An accounts/me response started before sign-in must never restore the old guest.
        if (!AccountAuthPolicy.sameSession(expectedSecret, secret())) return false
        if (account == null) return false
        val profile = account.optJSONObject("profile") ?: return false
        val previous = store.profiles()[KEY]?.uuid
        // Profile-only refreshes may update a session, never switch it to another account.
        val suppliedSecret = account.optString("secret").takeIf { it.matches(Regex("[a-f0-9]{64}")) }
        if (previous != null && previous != profile.getString("uuid") && suppliedSecret == null) return false
        val nextSecret = suppliedSecret ?: secret()
        val restored = profile.optBoolean("registered") || profile.optJSONArray("courses")?.let { courses ->
            (0 until courses.length()).any { courses.getJSONObject(it).optBoolean("purchased") && courses.getJSONObject(it).optBoolean("access_enabled") }
        } == true
        recoveryRequired = requiresRecovery() && !restored
        store.saveGrant(KEY, JSONObject().put("secret", nextSecret).put("requires_recovery", recoveryRequired))
        if (previous != null && previous != profile.getString("uuid")) {
            store.clearCurrentGrants(); PurchaseVault(app).revokeAll()
        }
        store.saveProfile(KEY, profile)
        mutableChanges.value += 1
        return true
    }
    /** Called only for the server's explicit accounts/me session-ended response, not generic 401s. */
    @Synchronized fun invalidateEndedSession(expectedSecret: String): Boolean {
        if (secret() != expectedSecret) return false
        PurchaseVault(app).revokeAll()
        // Media and archived reports are intentionally left intact.
        store.clearCurrentGrants(); store.clearIdentityProfile()
        val fresh = ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it.toInt() and 255) }
        store.saveGrant(KEY, JSONObject().put("secret", fresh).put("requires_recovery", true))
        recoveryRequired = true
        mutableChanges.value += 1
        return true
    }
    fun profile() = store.profiles()[KEY]
    companion object {
        const val KEY = "@identity"
        @Volatile private var instance: LearningIdentity? = null
        fun get(context: Context): LearningIdentity = instance ?: synchronized(this) {
            instance ?: LearningIdentity(context).also { instance = it }
        }
    }
}
