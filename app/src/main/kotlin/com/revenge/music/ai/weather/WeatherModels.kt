package com.revenge.music.ai.weather

data class WeatherInfo(
  val temperature: Double,
  val feelsLike: Double,
  val condition: String,
  val conditionCode: Int,
  val humidity: Int,
  val windSpeed: Double,
  val isDay: Boolean,
  val weatherEmoji: String,
) {
  val summaryBadgeText: String
    get() = "$weatherEmoji ${temperature.toInt()}°C • $condition • $humidity% Humidity"

  fun toSnapshotString(): String = buildString {
    append(SNAPSHOT_PREFIX)
    append(temperature.toInt()).append("°C • ").append(condition)
    append('|').append(weatherEmoji)
    append('|').append(condition)
    append('|').append(humidity)
    append('|').append(temperature.toInt())
  }

  companion object {
    const val SNAPSHOT_PREFIX = "WEATHER_VIBE:"

    fun parseWeatherSnapshot(raw: String?): WeatherInfo? {
      if (raw == null || !raw.startsWith(SNAPSHOT_PREFIX)) return null
      val body = raw.removePrefix(SNAPSHOT_PREFIX)
      val parts = body.split('|')
      if (parts.size < 5) return null
      val first = parts[0]
      val conditionFromFirst = first.substringAfter("• ", "").trim()
      val emoji = parts[1].trim().ifBlank { "☀️" }
      val condition = parts[2].trim().ifBlank { conditionFromFirst.ifBlank { "Clear" } }
      val humidity = parts[3].trim().toIntOrNull() ?: 50
      val temperature = parts[4].trim().toIntOrNull() ?: 20
      return WeatherInfo(
        temperature = temperature.toDouble(),
        feelsLike = temperature.toDouble(),
        condition = condition,
        conditionCode = 0,
        humidity = humidity.coerceIn(0, 100),
        windSpeed = 0.0,
        isDay = true,
        weatherEmoji = emoji,
      )
    }
  }
}

sealed class WeatherUiState {
  data object Idle : WeatherUiState()

  data object Loading : WeatherUiState()

  data class Success(val data: WeatherInfo) : WeatherUiState()

  data class Error(
    val message: String,
    val isPermissionDenied: Boolean = false,
    val isPermanentlyDenied: Boolean = false,
  ) : WeatherUiState()
}

data class WeatherCondition(val condition: String, val emoji: String)

fun mapWmoCodeToCondition(code: Int, isDay: Boolean): WeatherCondition {
  val sun = if (isDay) "☀️" else "🌙"
  val clear = if (isDay) "Sunny" else "Clear Sky"
  return when (code) {
    0 -> WeatherCondition(clear, sun)
    1 -> WeatherCondition("Mainly Clear", sun)
    2 -> WeatherCondition("Partly Cloudy", "⛅")
    3 -> WeatherCondition("Overcast", "☁️")
    45,
    48 -> WeatherCondition("Fog", "🌫️")
    51,
    53,
    55 -> WeatherCondition("Drizzle", "🌦️")
    56,
    57 -> WeatherCondition("Freezing Drizzle", "🌧️")
    61,
    63,
    65 -> WeatherCondition("Rain", "🌧️")
    66,
    67 -> WeatherCondition("Freezing Rain", "🌧️")
    71,
    73,
    75 -> WeatherCondition("Snow", "🌨️")
    77 -> WeatherCondition("Snow Grains", "🌨️")
    80,
    81,
    82 -> WeatherCondition("Showers", "🌦️")
    85,
    86 -> WeatherCondition("Snow Showers", "🌨️")
    95 -> WeatherCondition("Thunderstorm", "⛈️")
    96,
    99 -> WeatherCondition("Heavy Thunderstorm", "⛈️")
    else -> WeatherCondition("Clear", sun)
  }
}
