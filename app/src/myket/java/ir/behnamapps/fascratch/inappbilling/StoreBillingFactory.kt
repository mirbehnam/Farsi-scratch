package ir.behnamapps.fascratch.inappbilling

import androidx.activity.ComponentActivity
import ir.behnamapps.fascratch.BuildConfig
import ir.behnamapps.fascratch.inappbilling.domain.*
import ir.myket.billingclient.IabHelper
import ir.myket.billingclient.util.Purchase
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object StoreBillingFactory {
    fun create(activity: ComponentActivity): BillingGateway = MyketBilling(activity)
}

private class MyketBilling(private val activity: ComponentActivity) : BillingGateway {
    override val provider = "myket"
    private var helper: IabHelper? = null
    private var connected = false
    private suspend fun connect(): IabHelper {
        if (BuildConfig.BILLING_PUBLIC_KEY.isBlank()) throw CourseFailure("کلید عمومی پرداخت مایکت در این نسخه تنظیم نشده است.")
        if (connected) return checkNotNull(helper)
        val client = helper ?: IabHelper(activity, BuildConfig.BILLING_PUBLIC_KEY).also { it.enableDebugLogging(false); helper = it }
        suspendCancellableCoroutine<Unit> { continuation ->
            client.startSetup { result ->
                if (continuation.isActive) {
                    connected = result.isSuccess
                    if (connected) continuation.resume(Unit)
                    else continuation.resumeWithException(CourseFailure("اتصال به مایکت برقرار نشد؛ نصب و ورود به مایکت را بررسی کنید."))
                }
            }
        }
        return client
    }
    private fun receipt(purchase: Purchase, sku: String): Receipt {
        if (purchase.sku != sku || purchase.packageName != activity.packageName || purchase.token.isBlank() || purchase.purchaseState != 0) {
            throw CourseFailure("رسید مایکت معتبر نیست.", code = "rejected")
        }
        return Receipt(purchase.sku, purchase.token)
    }
    override suspend fun owned(sku: String): Receipt? {
        val client = connect()
        return suspendCancellableCoroutine { continuation ->
            client.queryInventoryAsync(false, listOf(sku)) { result, inventory ->
                if (continuation.isActive) {
                    if (!result.isSuccess) continuation.resumeWithException(CourseFailure("بررسی خریدهای مایکت انجام نشد؛ دوباره تلاش کنید."))
                    else runCatching { inventory.getPurchase(sku)?.let { receipt(it, sku) } }
                        .fold({ continuation.resume(it) }, { continuation.resumeWithException(it) })
                }
            }
        }
    }
    override suspend fun price(sku: String): String? {
        val client = connect()
        return suspendCancellableCoroutine { continuation ->
            client.queryInventoryAsync(true, listOf(sku)) { result, inventory ->
                if (continuation.isActive) continuation.resume(if (result.isSuccess) inventory.getSkuDetails(sku)?.price else null)
            }
        }
    }
    override suspend fun purchase(sku: String): Receipt {
        val client = connect()
        return suspendCancellableCoroutine { continuation ->
            client.launchPurchaseFlow(activity, sku, { result, purchase ->
                if (continuation.isActive) {
                    if (!result.isSuccess || purchase == null) continuation.resumeWithException(CourseFailure("خرید مایکت انجام نشد یا لغو شد؛ خرید قبلی را بازیابی کنید."))
                    else runCatching { receipt(purchase, sku) }.fold({ continuation.resume(it) }, { continuation.resumeWithException(it) })
                }
            }, "")
        }
    }
    override fun close() { connected = false; helper?.dispose(); helper = null }
}
