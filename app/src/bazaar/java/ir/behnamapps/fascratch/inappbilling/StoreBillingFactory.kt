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
    private val accountCheck = BazaarAccountCheck(activity)

    private suspend fun checkAccount() {
        if (StoreAccountPolicy.requiresLogin(accountCheck.loggedIn())) {
            throw CourseFailure("برای خرید یا بازیابی دوره، ابتدا وارد حساب کافه‌بازارت شو.", code = StoreAccountPolicy.LOGIN_REQUIRED)
        }
    }

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
                connectionFailed { error -> if (continuation.isActive) continuation.resumeWithException(bazaarBillingFailure(error, "اتصال به کافه‌بازار برقرار نشد؛ دوباره تلاش کنید.")) }
                disconnected { if (continuation.isActive) continuation.resumeWithException(CourseFailure("ارتباط با کافه‌بازار قطع شد.")) }
            }
        }
    }
    override suspend fun owned(sku: String): Receipt? {
        checkAccount()
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
                queryFailed { error -> if (continuation.isActive) continuation.resumeWithException(bazaarBillingFailure(error, "بررسی خریدهای بازار انجام نشد؛ دوباره تلاش کنید.")) }
            }
        }
    }
    override suspend fun purchase(sku: String): Receipt {
        checkAccount()
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
                purchaseFailed { error -> if (continuation.isActive) continuation.resumeWithException(bazaarBillingFailure(error, "خرید انجام نشد؛ برای خرید قبلی از بازیابی استفاده کنید.")) }
                failedToBeginFlow { error -> if (continuation.isActive) continuation.resumeWithException(bazaarBillingFailure(error, "صفحه پرداخت بازار باز نشد؛ دوباره تلاش کنید.")) }
            }
        }
    }
    override fun close() { connection?.disconnect(); connection = null }
}
