package ir.behnamapps.fascratch.inappbilling.domain

/** Keep store failures separate from backend HTTP 401 and application-account logout. */
object StoreAccountPolicy {
    const val LOGIN_REQUIRED = "bazaar_login_required"
    const val NOT_INSTALLED = "bazaar_not_installed"
    const val UPDATE_REQUIRED = "bazaar_update_required"

    fun requiresLogin(confirmedLoggedIn: Boolean?) = confirmedLoggedIn == false
    fun hasHelp(code: String) = code in setOf(LOGIN_REQUIRED, NOT_INSTALLED, UPDATE_REQUIRED)
}
