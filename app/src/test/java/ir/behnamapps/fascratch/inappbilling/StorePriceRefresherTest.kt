package ir.behnamapps.fascratch.inappbilling

import ir.behnamapps.fascratch.inappbilling.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import org.junit.Assert.*
import org.junit.Test

class StorePriceRefresherTest {
    private val course = Course("ae6b3b4c-1d11-43fe-bd17-1c80ad234666", "", "", "scratch_basic")
    private class Gateway(val query: suspend (String) -> String?) : BillingGateway {
        override val provider = "cafebazaar"
        var closes = 0
        override suspend fun price(sku: String) = query(sku)
        override suspend fun owned(sku: String): Receipt? = null
        override suspend fun purchase(sku: String): Receipt = error("Price queries must never purchase")
        override fun close() { closes++ }
    }

    @Test fun refreshDuringAnOlderQueryIsNotDroppedAndOldZeroDoesNotOverwriteFreshPrice() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val results = mutableListOf<String?>()
        var requests = 0
        var loadings = 0
        val gateway = Gateway {
            assertEquals(course.sku, it)
            requests++
            if (requests == 1) { started.complete(Unit); release.await(); "0 ریال" } else "750000 ریال"
        }
        val refresher = StorePriceRefresher(this, gateway, Mutex(), { loadings++ }, { _, price -> results += price })
        refresher.refresh(listOf(course))
        started.await()
        refresher.refresh(listOf(course))
        release.complete(Unit)
        refresher.awaitIdle()
        assertEquals(2, requests)
        assertEquals(2, gateway.closes)
        assertEquals(2, loadings)
        assertEquals(listOf("750000 ریال"), results)
    }

    @Test fun everyRefreshRequestsStoreAgainAndFailuresClearOldPrice() = runBlocking {
        var attempt = 0
        var displayed: String? = "old-price"
        val gateway = Gateway { attempt++; when (attempt) { 1 -> "750000 ریال"; 2 -> null; else -> throw CourseFailure("Store unavailable") } }
        val refresher = StorePriceRefresher(this, gateway, Mutex(), { displayed = null }, { _, price -> displayed = price })
        refresher.refresh(listOf(course)); assertNull(displayed); refresher.awaitIdle(); assertEquals("750000 ریال", displayed)
        refresher.refresh(listOf(course)); assertNull(displayed); refresher.awaitIdle(); assertNull(displayed)
        refresher.refresh(listOf(course)); refresher.awaitIdle(); assertNull(displayed)
        assertEquals(3, attempt)
    }

    @Test fun coursePricesAreSeparateAndBlankResponsesAreUnavailableButActualZeroIsValid() = runBlocking {
        val results = mutableMapOf<String, String?>()
        val second = course.copy(id = "7d6b3b4c-1d11-43fe-bd17-1c80ad234666", sku = "scratch_advanced")
        val gateway = Gateway { if (it == "scratch_basic") "0 ریال" else "  " }
        val refresher = StorePriceRefresher(this, gateway, Mutex(), {}, { c, price -> results[c.id] = price })
        refresher.refresh(listOf(course, second)); refresher.awaitIdle()
        assertEquals("0 ریال", results[course.id])
        assertNull(results[second.id])
        assertEquals(2, results.size)
    }
}
