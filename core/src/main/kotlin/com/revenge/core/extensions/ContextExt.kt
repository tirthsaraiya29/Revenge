package com.revenge.core.extensions

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.revenge.innertube.utils.parseCookieString
import com.revenge.core.constants.InnerTubeCookieKey
import com.revenge.core.constants.YtmSyncKey
import com.revenge.core.utils.dataStore
import com.revenge.core.utils.get

fun Context.isInternetConnected(): Boolean {
    val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

fun Context.isUserLoggedIn(): Boolean {
    val cookie = dataStore[InnerTubeCookieKey] ?: return false
    return parseCookieString(cookie).containsKey("SAPISID") && isInternetConnected()
}

fun Context.isSyncEnabled(): Boolean = dataStore.get(YtmSyncKey, true) && isUserLoggedIn()
