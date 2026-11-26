package com.revenge.music.extensions

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.revenge.innertube.utils.parseCookieString
import com.revenge.music.constants.InnerTubeCookieKey
import com.revenge.music.constants.YtmSyncKey
import com.revenge.music.utils.dataStore
import com.revenge.music.utils.get
import kotlinx.coroutines.runBlocking

/** YouTube Music sync needs both the user toggle and a valid logged-in session. */
fun Context.isSyncEnabled(): Boolean = dataStore.get(YtmSyncKey, true) && isUserLoggedIn()

fun Context.isUserLoggedIn(): Boolean {
  val cookie = runBlocking { dataStore[InnerTubeCookieKey] } ?: ""
  return "SAPISID" in parseCookieString(cookie) && isInternetConnected()
}

fun Context.isInternetConnected(): Boolean {
  val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
  val capabilities =
    connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
  return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
}
