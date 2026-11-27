package com.revenge.music.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Connectivity probe that never lets a platform exception escape. */
fun isInternetAvailable(context: Context): Boolean = try {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    val network = manager?.activeNetwork
    val capabilities = network?.let(manager::getNetworkCapabilities)
    capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
} catch (_: Exception) {
    false
}
