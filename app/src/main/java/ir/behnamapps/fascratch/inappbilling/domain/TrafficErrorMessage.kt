package ir.behnamapps.fascratch.inappbilling.domain

object TrafficErrorMessage {
    fun message(status: Int, code: String, retryAfterSeconds: Long?): String? = when {
        code == "ip_blocked" -> "دسترسی این شبکه توسط سرور محدود شده است؛ با پشتیبانی تماس بگیر."
        status == 429 -> retryAfterSeconds?.takeIf { it > 0 }?.coerceAtMost(3600)?.let {
            "درخواست‌های زیادی ارسال شده؛ $it ثانیه بعد دوباره تلاش کن."
        } ?: "درخواست‌های زیادی ارسال شده؛ کمی بعد دوباره تلاش کن."
        else -> null
    }
}
