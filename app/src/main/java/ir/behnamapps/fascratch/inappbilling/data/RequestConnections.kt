package ir.behnamapps.fascratch.inappbilling.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.net.ssl.HttpsURLConnection

/** Optional owner for short-lived UI sessions. Never disconnect sockets on the UI thread. */
internal class RequestConnections : AutoCloseable {
    private val active = mutableSetOf<HttpsURLConnection>()
    private var closed = false

    @Synchronized fun register(connection: HttpsURLConnection) {
        if (closed) throw CancellationException("Request owner closed")
        active.add(connection)
    }

    @Synchronized fun finished(connection: HttpsURLConnection) { active.remove(connection) }

    override fun close() {
        val pending = synchronized(this) {
            closed = true
            active.toList().also { active.clear() }
        }
        if (pending.isNotEmpty()) cleanup.launch { pending.forEach { runCatching { it.disconnect() } } }
    }

    companion object { private val cleanup = CoroutineScope(SupervisorJob() + Dispatchers.IO) }
}
