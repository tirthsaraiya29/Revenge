package com.revenge.music.listentogether

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A relay endpoint offered to the user. The registry is static for the process lifetime. */
data class ListenTogetherServer(
    val name: String,
    val url: String,
    val location: String,
    val operator: String,
)

object ListenTogetherServers {
    private val registry = listOf(
        ListenTogetherServer(
            name = "Metrolist Server",
            url = "wss://metroserverx.meowery.eu/ws",
            location = "Poland",
            operator = "Metrolist",
        ),
    )

    private val _serversFlow = MutableStateFlow(registry)
    val serversFlow: StateFlow<List<ListenTogetherServer>> = _serversFlow.asStateFlow()

    val servers: List<ListenTogetherServer> get() = registry
    val defaultServerUrl: String get() = registry.first().url

    fun findByUrl(url: String): ListenTogetherServer? = registry.firstOrNull { it.url == url }
}
