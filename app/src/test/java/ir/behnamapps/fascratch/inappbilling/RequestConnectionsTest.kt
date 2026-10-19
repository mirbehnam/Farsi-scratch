package ir.behnamapps.fascratch.inappbilling

import ir.behnamapps.fascratch.inappbilling.data.RequestConnections
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test
import java.net.URL
import java.security.cert.Certificate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

class RequestConnectionsTest {
    private class Connection : HttpsURLConnection(URL("https://example.invalid")) {
        val disconnected = CountDownLatch(1)
        override fun connect() = Unit
        override fun disconnect() { disconnected.countDown() }
        override fun usingProxy() = false
        override fun getCipherSuite() = "test"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
    }
    @Test fun closeDisconnectsOutstandingSocketsAndRejectsLateRequests() {
        val scope = RequestConnections()
        val connection = Connection()
        scope.register(connection)
        scope.close()
        assertTrue(connection.disconnected.await(3, TimeUnit.SECONDS))
        try { scope.register(Connection()); fail("Closed owner accepted another request") }
        catch (_: CancellationException) { }
    }
    @Test fun finishedRequestsAreNotRetainedUntilClose() {
        val scope = RequestConnections()
        val connection = Connection()
        scope.register(connection); scope.finished(connection); scope.close()
        assertEquals(1L, connection.disconnected.count)
    }
}
