package ir.behnamapps.fascratch.inappbilling

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.farsitel.bazaar.ILoginCheckService
import kotlinx.coroutines.*
import kotlin.coroutines.resume

/** No account identifier or credentials are read. Only an explicit false means signed out. */
internal class BazaarAccountCheck(context: Context) {
    private val app = context.applicationContext

    suspend fun loggedIn(): Boolean? = withContext(Dispatchers.Main.immediate) {
        val connected = CompletableDeferred<IBinder?>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) { connected.complete(binder) }
            override fun onServiceDisconnected(name: ComponentName?) { connected.complete(null) }
            override fun onNullBinding(name: ComponentName?) { connected.complete(null) }
            override fun onBindingDied(name: ComponentName?) { connected.complete(null) }
        }
        // Independent worker: a hung remote Binder must not hold up the purchase UI on timeout.
        val rpcScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var bound = false
        try {
            val intent = Intent("com.farsitel.bazaar.service.LoginCheckService.BIND").setPackage("com.farsitel.bazaar")
            bound = app.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            if (!bound) return@withContext null
            withTimeoutOrNull(1500) {
                val binder = connected.await() ?: return@withTimeoutOrNull null
                suspendCancellableCoroutine<Boolean?> { continuation ->
                    val job = rpcScope.launch {
                        val result = runCatching { ILoginCheckService.Stub.asInterface(binder)?.isLoggedIn }.getOrNull()
                        if (continuation.isActive) continuation.resume(result)
                    }
                    continuation.invokeOnCancellation { job.cancel() }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null // An unavailable/old service is not proof that the user is signed out.
        } finally {
            rpcScope.cancel()
            if (bound) runCatching { app.unbindService(connection) }
        }
    }
}
