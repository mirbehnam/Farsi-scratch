package ir.behnamapps.fascratch.inappbilling

import ir.behnamapps.fascratch.inappbilling.data.PurchaseTiming
import org.junit.Assert.*
import org.junit.Test

class PurchaseTimingTest {
    @Test fun parsesAllServerTimingValuesIncludingSeparateHeadersAndProxyTotals() {
        val headers = listOf("account;dur=13.2, store_verify;dur=564.8", "profile;dur=11.4, storage;dur=130.5, total;dur=736.1", "total;dur=946")
        assertEquals(listOf("account" to 13L, "store_verify" to 564L, "profile" to 11L, "storage" to 130L,
            "total" to 736L, "total" to 946L), PurchaseTiming.parseServer(headers.joinToString(", ")))
    }
    @Test fun onlyRecognizedNumericMetricsCanReachDebugLogging() {
        assertEquals(listOf("total" to 42L), PurchaseTiming.parseServer("receipt_secret;dur=99, store_verify;dur=secret, storage;dur=-1, total;dur=42"))
    }
}
