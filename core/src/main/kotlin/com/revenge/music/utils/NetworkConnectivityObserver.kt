package com.revenge.music.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

class NetworkConnectivityObserver(context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val updates = Channel<Boolean>(Channel.CONFLATED)

    val networkStatus: Flow<Boolean> = updates.receiveAsFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            updates.trySend(true)
        }

        override fun onLost(network: Network) {
            updates.trySend(false)
        }
    }

    init {
        updates.trySend(isCurrentlyConnected())
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            .build()
        try {
            connectivityManager?.registerNetworkCallback(request, callback)
        } catch (_: Exception) {
            updates.trySend(true)
        }
    }

    fun isCurrentlyConnected(): Boolean {
        return try {
            val manager = connectivityManager ?: return false
            val network = manager.activeNetwork ?: return false
            val capabilities = manager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (_: Exception) {
            false
        }
    }

    fun unregister() {
        try {
            connectivityManager?.unregisterNetworkCallback(callback)
        } catch (_: Exception) {
            // callback was never registered or is already gone
        }
    }
}
