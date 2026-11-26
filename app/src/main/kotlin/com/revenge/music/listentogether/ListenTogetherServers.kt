package com.revenge.music.listentogether

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ListenTogetherServer(
  val name: String,
  val url: String,
  val location: String,
  val operator: String,
)

object ListenTogetherServers {

  private val directory =
    listOf(
      ListenTogetherServer(
        name = "Metrolist Server",
        url = "wss://metroserverx.meowery.eu/ws",
        location = "Poland",
        operator = "Metrolist",
      ),
    )

  private val _serversFlow = MutableStateFlow(directory)
  val serversFlow: StateFlow<List<ListenTogetherServer>> = _serversFlow.asStateFlow()

  val servers: List<ListenTogetherServer>
    get() = directory

  val defaultServerUrl: String
    get() = directory.first().url

  fun findByUrl(url: String): ListenTogetherServer? = directory.firstOrNull { it.url == url }
}
