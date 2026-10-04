package ir.behnamapps.fascratch.inappbilling

import androidx.activity.ComponentActivity
import ir.behnamapps.fascratch.BuildConfig
import ir.behnamapps.fascratch.inappbilling.domain.*
import ir.cafebazaar.poolakey.Connection
import ir.cafebazaar.poolakey.ConnectionState
import ir.cafebazaar.poolakey.Payment
import ir.cafebazaar.poolakey.config.PaymentConfiguration
import ir.cafebazaar.poolakey.config.SecurityCheck
import ir.cafebazaar.poolakey.entity.PurchaseState
import ir.cafebazaar.poolakey.request.PurchaseRequest
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object StoreBillingFactory {
    fun create(activity: ComponentActivity): BillingGateway = BazaarBilling(activity)
}

private class BazaarBilling(private val activity: ComponentActivity) : BillingGateway {
    override val provider = "cafebazaar"
    // With no public RSA configured, Poolakey delivers receipts but our server remains mandatory.
    private val payment = Payment(activity, PaymentConfiguration(
        if (BuildConfig.BILLING_PUBLIC_KEY.isBlank()) SecurityCheck.Disable else SecurityCheck.Enable(BuildConfig.BILLING_PUBLIC_KEY),
        shouldSupportSubscription = false))
    private var connection: Connection? = null

    private suspend fun connect() {
        if (connection?.getState() == ConnectionState.Connected) return
        connection?.disconnect()
        suspendCancellableCoroutine<Unit> { continuation ->
            connection = payment.connect {
                connectionSucceed { if (continuation.isActive) continuation.resume(Unit) }
                connectionFailed { if (continuation.isActive) continuation.resumeWithException(CourseFailure("اتصال به کافه‌بازار برقرار نشد؛ نصب و ورود به بازار را بررسی کنید.")) }
                disconnected { if (continuation.isActive) continuation.resumeWithException(CourseFailure("ارتباط با کافه‌بازار قطع شد.")) }
            }
        }
    }
    override suspend fun owned(sku: String): Receipt? {
        connect()
        return suspendCancellableCoroutine { continuation ->
            payment.getPurchasedProducts {
                querySucceed { rows ->
                    if (continuation.isActive) {
                        val row = rows.firstOrNull { it.productId == sku && it.packageName == activity.packageName }
                        if (row != null && row.purchaseState != PurchaseState.PURCHASED) continuation.resumeWithException(CourseFailure("خرید بازار فعال نیست.", code = "rejected"))
                        else continuation.resume(row?.let { Receipt(it.productId, it.purchaseToken) })
                    }
                }
                queryFailed { if (continuation.isActive) continuation.resumeWithException(CourseFailure("بررسی خریدهای بازار انجام نشد؛ دوباره تلاش کنید.")) }
            }
        }
    }
    override suspend fun price(sku: String): String? {
        connect()
        return suspendCancellableCoroutine { continuation ->
            payment.getInAppSkuDetails(listOf(sku)) {
                getSkuDetailsSucceed { rows -> if (continuation.isActive) continuation.resume(rows.firstOrNull { it.sku == sku && it.type == "inapp" }?.price?.takeIf { it.isNotBlank() }) }
                getSkuDetailsFailed { if (continuation.isActive) continuation.resume(null) }
            }
        }
    }
    override suspend fun purchase(sku: String): Receipt {
        connect()
        return suspendCancellableCoroutine { continuation ->
            payment.purchaseProduct(activity.activityResultRegistry, PurchaseRequest(sku)) {
                purchaseSucceed { row ->
                    if (continuation.isActive) {
                        if (row.productId == sku && row.packageName == activity.packageName && row.purchaseState == PurchaseState.PURCHASED) continuation.resume(Receipt(row.productId, row.purchaseToken))
                        else continuation.resumeWithException(CourseFailure("رسید بازار با برنامه مطابقت ندارد."))
                    }
                }
                purchaseCanceled { if (continuation.isActive) continuation.resumeWithException(CourseFailure("خرید لغو شد؛ اگر قبلاً خرید کرده‌اید، بازیابی را بزنید.")) }
                purchaseFailed { if (continuation.isActive) continuation.resumeWithException(CourseFailure("خرید انجام نشد؛ برای خرید قبلی از بازیابی استفاده کنید.")) }
                failedToBeginFlow { if (continuation.isActive) continuation.resumeWithException(CourseFailure("صفحه پرداخت بازار باز نشد.")) }
            }
        }
    }
    override fun close() { connection?.disconnect(); connection = null }
}
