package ir.behnamapps.fascratch.inappbilling.presentation

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import ir.behnamapps.fascratch.inappbilling.domain.CoursePolicy

/** Android system validation works independently of Play Services; Wi-Fi alone is not Internet. */
internal fun purchaseNetworkAvailable(context: Context): Boolean = runCatching {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return@runCatching false
    CoursePolicy.purchaseNetworkAvailable(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
}.getOrDefault(false)
