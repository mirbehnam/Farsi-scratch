package ir.behnamapps.fascratch.inappbilling.learning

/** A network/authentication failure alone is not evidence that an account was revoked. */
internal object AccountSessionPolicy {
    fun ended(status: Int?, code: String, hasProfile: Boolean): Boolean =
        hasProfile && status == 401 && code == "account_session_ended"
}
