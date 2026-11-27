package com.revenge.music.ai.weather

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class IpLocation(
  val latitude: Double,
  val longitude: Double,
  val city: String,
  val country: String,
)

@Singleton
class IpLocationRepository @Inject constructor() {
  private val transport: WeatherTransport = OkHttpWeatherTransport()
  private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
  }

  constructor(transport: WeatherTransport) : this() {
    this.explicitTransport = transport
  }

  private var explicitTransport: WeatherTransport? = null

  private fun activeTransport(): WeatherTransport = explicitTransport ?: transport

  suspend fun getLocation(): Result<IpLocation> {
    primary()?.let {
      return Result.success(it)
    }
    fallback()?.let {
      return Result.success(it)
    }
    return Result.failure(IllegalStateException("Could not determine location from IP."))
  }

  private suspend fun primary(): IpLocation? = runCatching {
    val body = activeTransport().get("https://ipwho.is/", emptyMap())
    if (body.isBlank()) return@runCatching null
    val root = json.parseToJsonElement(body).jsonObject
    val success = root["success"]?.jsonPrimitive?.booleanOrNull ?: false
    if (!success) return@runCatching null
    val latitude = root.number("latitude") ?: return@runCatching null
    val longitude = root.number("longitude") ?: return@runCatching null
    IpLocation(
      latitude = latitude,
      longitude = longitude,
      city = root.string("city"),
      country = root.string("country"),
    )
  }
    .getOrNull()

  private suspend fun fallback(): IpLocation? = runCatching {
    val body =
      activeTransport()
        .get(
          "https://ipapi.co/json/",
          mapOf("User-Agent" to "Mozilla/5.0"),
        )
    if (body.isBlank()) return@runCatching null
    val root = json.parseToJsonElement(body).jsonObject
    val latitude = root.number("latitude") ?: return@runCatching null
    val longitude = root.number("longitude") ?: return@runCatching null
    IpLocation(
      latitude = latitude,
      longitude = longitude,
      city = root.string("city"),
      country = root.string("country_name"),
    )
  }
    .getOrNull()

  private fun JsonObject.number(key: String): Double? = this[key]?.jsonPrimitive?.doubleOrNull

  private fun JsonObject.string(key: String): String =
    this[key]?.jsonPrimitive?.contentOrNull.orEmpty()
}
