package ir.behnamapps.fascratch.tour

import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

internal data class TourNotice(val title: String, val message: String)
internal enum class TourFailure { SPACE, INCOMPLETE, UNAVAILABLE }
internal class TourDownloadException(val reason: TourFailure) : IOException()

/** Never expose platform/network exception messages in the youth-facing UI. */
internal object TourFeedback {
    fun network(error: Exception) = error is UnknownHostException || error is SocketException ||
        error is InterruptedIOException || error is SSLException

    fun download(error: Exception): TourNotice = when {
        error is TourDownloadException && error.reason == TourFailure.SPACE -> TourNotice("یک کم جا باز کن!", "فضای خالی گوشی کافی نیست. کمی فضا آزاد کن و دوباره دریافت ویدیو را بزن.")
        error is TourDownloadException && error.reason == TourFailure.INCOMPLETE -> TourNotice("دریافت کامل نشد", "فایل ویدیو کامل یا سالم دریافت نشد. اینترنتت را بررسی کن و دوباره تلاش کن.")
        error is TourDownloadException && error.reason == TourFailure.UNAVAILABLE -> TourNotice("این ویدیو تازه شده", "فهرست راهنما را تازه کن و دوباره ویدیو را انتخاب کن.")
        network(error) -> TourNotice("ارتباطمان قطع شد", "برای دریافت این ویدیو به اینترنت وصل شو و «دوباره دریافت کن» را بزن. بخش دریافت‌شده محفوظ است؛ از همان‌جا ادامه می‌دهیم.")
        else -> TourNotice("یک بار دیگر امتحان کنیم؟", "دریافت ویدیو انجام نشد. اینترنتت را بررسی کن و دوباره تلاش کن؛ اگر وصل هستی، کمی بعد امتحان کن.")
    }
}
