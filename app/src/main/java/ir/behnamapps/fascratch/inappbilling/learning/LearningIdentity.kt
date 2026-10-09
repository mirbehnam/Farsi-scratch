package ir.behnamapps.fascratch.inappbilling.learning

import android.content.Context
import org.json.JSONObject
import java.security.SecureRandom

/** Private installation credential; never an IMEI/store-user identifier. Excluded from backup. */
class LearningIdentity private constructor(context: Context) {
    private val store = LearningStore(context.applicationContext)
    @Synchronized fun secret(): String {
        store.grant(KEY)?.optString("secret")?.takeIf { it.matches(Regex("[a-f0-9]{64}")) }?.let { return it }
        val secret = ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it.toInt() and 255) }
        // Persist before first request so a response lost in transit cannot duplicate registration.
        store.saveGrant(KEY, JSONObject().put("secret", secret))
        return secret
    }
    @Synchronized fun accept(account: JSONObject?) {
        if (account == null) return
        val profile = account.optJSONObject("profile") ?: return
        val previous = store.profiles()[KEY]?.uuid
        account.optString("secret").takeIf { it.matches(Regex("[a-f0-9]{64}")) }?.let {
            store.saveGrant(KEY, JSONObject().put("secret", it))
        }
        if (previous != null && previous != profile.getString("uuid")) store.clearCurrentGrants()
        store.saveProfile(KEY, profile)
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
