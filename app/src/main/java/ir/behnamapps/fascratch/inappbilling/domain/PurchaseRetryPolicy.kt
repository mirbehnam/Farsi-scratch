package ir.behnamapps.fascratch.inappbilling.domain

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** No credentials, clocks or Android dependencies: shared by interactive confirmation and the job. */
object PurchaseRetryPolicy {
    /** Backoff controls automatic work; an explicit restore gets one store-verified server attempt. */
    fun waitsForConfirmation(explicitRestore: Boolean, now: Long, cooldownUntil: Long,
        pendingNext: Long, attempt: Int, blocked: Boolean): Boolean = !explicitRestore &&
        (cooldownUntil > now || (attempt > 0 && !blocked && pendingNext > now))

    fun retryable(failure: CourseFailure): Boolean = failure.code !in setOf(
        "refunded", "rejected", "not_owned", "access_disabled", "purchase_owner_mismatch", "purchase_account_required"
    ) && (failure.status == 0 || failure.status == 408 || failure.status == 425 || failure.status == 429 ||
        failure.status in 500..599 || failure.code == "ip_blocked")

    fun delayMillis(attempt: Int, serverSeconds: Long?): Long {
        val backoff = (30_000L * (1L shl attempt.coerceIn(0, 10))).coerceAtMost(6 * 60 * 60_000L)
        val server = serverSeconds?.coerceIn(0, Long.MAX_VALUE / 1000)?.times(1000) ?: 0L
        return maxOf(backoff, server)
    }

    fun retryAfter(header: String?, jsonSeconds: Long?, now: Long): Long? {
        val seconds = header?.trim()?.toLongOrNull()?.takeIf { it >= 0 }
            ?: header?.let { value -> runCatching {
                val date = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
                    isLenient = false; timeZone = TimeZone.getTimeZone("GMT")
                }.parse(value) ?: return@runCatching null
                ((date.time - now).coerceAtLeast(0) + 999) / 1000
            }.getOrNull() }
        return listOfNotNull(seconds, jsonSeconds?.takeIf { it >= 0 }).maxOrNull()
    }

    fun sameOwner(expected: String, actual: String?, recoveryRequired: Boolean): Boolean =
        expected.isNotBlank() && expected == actual && !recoveryRequired
}
