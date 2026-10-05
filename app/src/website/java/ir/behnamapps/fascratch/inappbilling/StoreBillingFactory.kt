package ir.behnamapps.fascratch.inappbilling

import androidx.activity.ComponentActivity
import ir.behnamapps.fascratch.inappbilling.domain.*

object StoreBillingFactory {
    fun create(activity: ComponentActivity): BillingGateway = object : BillingGateway {
        override val provider = "website"
        override suspend fun owned(sku: String): Receipt? = throw CourseFailure("خرید دوره در نسخه بازار یا مایکت در دسترس است.")
        override suspend fun purchase(sku: String): Receipt = throw CourseFailure("برای خرید، نسخه کافه‌بازار یا مایکت را نصب کنید.")
        override fun close() = Unit
    }
}
