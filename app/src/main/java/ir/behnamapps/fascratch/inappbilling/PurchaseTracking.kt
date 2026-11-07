package ir.behnamapps.fascratch.inappbilling

import android.content.Context
import ir.behnamapps.fascratch.inappbilling.data.CourseApi
import ir.behnamapps.fascratch.inappbilling.domain.CourseFailure
import ir.behnamapps.fascratch.inappbilling.learning.LearningIdentity
import java.util.UUID

/** Public support references only; never an account credential or proof of receipt ownership. */
internal object PurchaseTracking {
    fun payload(profileUuid: String, attemptUuid: String = UUID.randomUUID().toString()): String {
        val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        require(uuid.matches(profileUuid) && uuid.matches(attemptUuid))
        return "fs1:$profileUuid:$attemptUuid"
    }
    suspend fun prepare(context: Context): String {
        // Verify/register the account BEFORE opening payment, so failed post-payment sync is traceable.
        val profile = CourseApi(context).account()
        val identity = LearningIdentity.get(context)
        val uuid = profile.getString("uuid")
        if (identity.requiresRecovery() || identity.profile()?.uuid != uuid)
            throw CourseFailure("برای پیگیری امن خرید، ابتدا وارد حساب خودت شو و دوباره تلاش کن.")
        return payload(uuid)
    }
}
