package com.revenge.music.ai.weather

import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

fun interface WeatherTransport {
    suspend fun get(url: String, headers: Map<String, String>): String
}

class OkHttpWeatherTransport : WeatherTransport {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun get(url: String, headers: Map<String, String>): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).apply {
                headers.forEach { (key, value) -> header(key, value) }
            }.build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                response.body?.string().orEmpty()
            }
        }
}

@Singleton
class WeatherRepository @Inject constructor() {
    private val transport: WeatherTransport = OkHttpWeatherTransport()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    constructor(transport: WeatherTransport) : this() {
        this.explicitTransport = transport
    }

    private var explicitTransport: WeatherTransport? = null

    private fun activeTransport(): WeatherTransport = explicitTransport ?: transport

    suspend fun getWeather(latitude: Double, longitude: Double): Result<WeatherInfo> =
        runCatching {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$latitude" +
                "&longitude=$longitude&current=temperature_2m,relative_humidity_2m," +
                "apparent_temperature,is_day,weather_code,wind_speed_10m"
            val body = activeTransport().get(url, emptyMap())
            if (body.isBlank()) error("Empty weather response")
            val root = json.parseToJsonElement(body).jsonObject
            val current = root["current"]?.jsonObject ?: error("Missing current weather")
            val temperature = current.number("temperature_2m") ?: error("Missing temperature")
            val humidity = current.int("relative_humidity_2m") ?: 0
            val feelsLike = current.number("apparent_temperature") ?: temperature
            val isDay = (current.int("is_day") ?: 1) == 1
            val code = current.int("weather_code") ?: 0
            val wind = current.number("wind_speed_10m") ?: 0.0
            val mapped = mapWmoCodeToCondition(code, isDay)
            WeatherInfo(
                temperature = temperature,
                feelsLike = feelsLike,
                condition = mapped.condition,
                conditionCode = code,
                humidity = humidity,
                windSpeed = wind,
                isDay = isDay,
                weatherEmoji = mapped.emoji,
            )
        }

    private fun kotlinx.serialization.json.JsonObject.number(key: String): Double? =
        this[key]?.jsonPrimitive?.doubleOrNull

    private fun kotlinx.serialization.json.JsonObject.int(key: String): Int? =
        this[key]?.jsonPrimitive?.intOrNull
}
