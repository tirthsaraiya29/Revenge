package com.revenge.music.listentogether

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

@Serializable
data class ListenTogetherServer(
  val name: String,
  val url: String,
  val location: String,
  val operator: String,
)

object ListenTogetherServers {
  private val _servers =
    MutableStateFlow(
      listOf(
        ListenTogetherServer(
          name = "Metrolist Server",
          url = "wss://metroserverx.meowery.eu/ws",
          location = "Poland",
          operator = "Metrolist",
        ),
      ),
    )

  val serversFlow: StateFlow<List<ListenTogetherServer>> = _servers

  val servers: List<ListenTogetherServer>
    get() = _servers.value

  val defaultServerUrl: String
    get() = servers.first().url

  fun findByUrl(url: String): ListenTogetherServer? = servers.firstOrNull { it.url == url }
}
