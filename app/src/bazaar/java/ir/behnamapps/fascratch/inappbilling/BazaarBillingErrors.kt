package ir.behnamapps.fascratch.inappbilling

import ir.behnamapps.fascratch.inappbilling.domain.CourseFailure
import ir.behnamapps.fascratch.inappbilling.domain.StoreAccountPolicy
import ir.cafebazaar.poolakey.exception.BazaarNotFoundException
import ir.cafebazaar.poolakey.exception.BazaarNotSupportedException
import ir.cafebazaar.poolakey.exception.IAPNotSupportedException

/** SDK 2.2.0 drops response codes in ResultNotOkayException; never infer login from it. */
internal fun bazaarBillingFailure(error: Throwable, message: String): CourseFailure = when (error) {
    is BazaarNotFoundException -> CourseFailure("کافه‌بازار روی دستگاه پیدا نشد؛ آن را نصب یا فعال کن.", code = StoreAccountPolicy.NOT_INSTALLED)
    is BazaarNotSupportedException, is IAPNotSupportedException -> CourseFailure("برای ادامه، کافه‌بازار را بروزرسانی کن.", code = StoreAccountPolicy.UPDATE_REQUIRED)
    else -> CourseFailure(message)
}
