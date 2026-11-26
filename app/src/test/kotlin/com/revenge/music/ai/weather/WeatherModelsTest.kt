package com.revenge.music.ai.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeatherModelsTest {
    @Test
    fun wmoCodesMapToDocumentedConditions() {
        assertEquals("Sunny", mapWmoCodeToCondition(0, true).condition)
        assertEquals("Clear Sky", mapWmoCodeToCondition(0, false).condition)
        assertEquals("Mainly Clear", mapWmoCodeToCondition(1, true).condition)
        assertEquals("Partly Cloudy", mapWmoCodeToCondition(2, true).condition)
        assertEquals("Overcast", mapWmoCodeToCondition(3, true).condition)
        assertEquals("Fog", mapWmoCodeToCondition(45, true).condition)
        assertEquals("Fog", mapWmoCodeToCondition(48, true).condition)
        assertEquals("Drizzle", mapWmoCodeToCondition(51, true).condition)
        assertEquals("Drizzle", mapWmoCodeToCondition(53, true).condition)
        assertEquals("Drizzle", mapWmoCodeToCondition(55, true).condition)
        assertEquals("Freezing Drizzle", mapWmoCodeToCondition(56, true).condition)
        assertEquals("Freezing Drizzle", mapWmoCodeToCondition(57, true).condition)
        assertEquals("Rain", mapWmoCodeToCondition(61, true).condition)
        assertEquals("Rain", mapWmoCodeToCondition(63, true).condition)
        assertEquals("Rain", mapWmoCodeToCondition(65, true).condition)
        assertEquals("Freezing Rain", mapWmoCodeToCondition(66, true).condition)
        assertEquals("Freezing Rain", mapWmoCodeToCondition(67, true).condition)
        assertEquals("Snow", mapWmoCodeToCondition(71, true).condition)
        assertEquals("Snow", mapWmoCodeToCondition(73, true).condition)
        assertEquals("Snow", mapWmoCodeToCondition(75, true).condition)
        assertEquals("Snow Grains", mapWmoCodeToCondition(77, true).condition)
        assertEquals("Showers", mapWmoCodeToCondition(80, true).condition)
        assertEquals("Showers", mapWmoCodeToCondition(81, true).condition)
        assertEquals("Showers", mapWmoCodeToCondition(82, true).condition)
        assertEquals("Snow Showers", mapWmoCodeToCondition(85, true).condition)
        assertEquals("Snow Showers", mapWmoCodeToCondition(86, true).condition)
        assertEquals("Thunderstorm", mapWmoCodeToCondition(95, true).condition)
        assertEquals("Heavy Thunderstorm", mapWmoCodeToCondition(96, true).condition)
        assertEquals("Heavy Thunderstorm", mapWmoCodeToCondition(99, true).condition)
        assertEquals("Clear", mapWmoCodeToCondition(1234, true).condition)
    }

    @Test
    fun summaryBadgeFormatMatchesSpecification() {
        val info = WeatherInfo(
            temperature = 20.7,
            feelsLike = 19.0,
            condition = "Sunny",
            conditionCode = 0,
            humidity = 50,
            windSpeed = 3.0,
            isDay = true,
            weatherEmoji = "☀️",
        )
        assertEquals("☀️ 20°C • Sunny • 50% Humidity", info.summaryBadgeText)
    }

    @Test
    fun snapshotRoundTrips() {
        val info = WeatherInfo(
            temperature = 12.4,
            feelsLike = 10.0,
            condition = "Rain",
            conditionCode = 61,
            humidity = 80,
            windSpeed = 5.0,
            isDay = true,
            weatherEmoji = "🌧️",
        )
        val snapshot = info.toSnapshotString()
        assertEquals("WEATHER_VIBE:12°C • Rain|🌧️|Rain|80|12", snapshot)
        val restored = WeatherInfo.parseWeatherSnapshot(snapshot)!!
        assertEquals(12.0, restored.temperature, 0.001)
        assertEquals(80, restored.humidity)
        assertEquals("Rain", restored.condition)
        assertEquals("🌧️", restored.weatherEmoji)
    }

    @Test
    fun malformedSnapshotsReturnNull() {
        assertNull(WeatherInfo.parseWeatherSnapshot(null))
        assertNull(WeatherInfo.parseWeatherSnapshot("something else"))
        assertNull(WeatherInfo.parseWeatherSnapshot("WEATHER_VIBE:a|b"))
        assertNull(WeatherInfo.parseWeatherSnapshot("WEATHER_VIBE:12°C • Rain|🌧️"))
    }
}
