package com.revenge.music.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/** Emits connectivity changes (conflated) so callers can react without polling. */
class NetworkConnectivityObserver(context: Context) {
  private val connectivityManager =
    context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

  private val _networkStatus = Channel<Boolean>(Channel.CONFLATED)
  val networkStatus = _networkStatus.receiveAsFlow()

  private val networkCallback =
    object : ConnectivityManager.NetworkCallback() {
      override fun onAvailable(network: Network) {
        _networkStatus.trySend(true)
      }

      override fun onLost(network: Network) {
        _networkStatus.trySend(false)
      }
    }

  init {
    val request =
      NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
        .build()

    try {
      connectivityManager.registerNetworkCallback(request, networkCallback)
    } catch (e: Exception) {
      // Registration can fail on restricted profiles; treat as connected until told otherwise.
      _networkStatus.trySend(true)
    }

    _networkStatus.trySend(isCurrentlyConnected())
  }

  fun unregister() {
    runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
  }

  fun isCurrentlyConnected(): Boolean {
    return try {
      val capabilities =
        connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
      capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    } catch (e: Exception) {
      false
    }
  }
}
