package ir.behnamapps.fascratch.tour

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Observed only while the tour dialog is visible and resumed. No polling/background service. */
internal fun tourConnections(context: Context) = callbackFlow {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    fun online(capabilities: NetworkCapabilities?) = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) { trySend(online(capabilities)) }
        override fun onLost(network: Network) { trySend(false) }
    }
    val registered = runCatching { manager?.registerDefaultNetworkCallback(callback); manager != null }.getOrDefault(false)
    trySend(runCatching { online(manager?.getNetworkCapabilities(manager.activeNetwork)) }.getOrDefault(false))
    awaitClose { if (registered) runCatching { manager?.unregisterNetworkCallback(callback) } }
}.distinctUntilChanged()
