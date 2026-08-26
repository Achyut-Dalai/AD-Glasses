package com.ad_glasses.ai.grounding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenMeteoWeatherClientTest {
    private val client = OpenMeteoWeatherClient()

    @Test
    fun parsesCurrentAndDailyForecastIntoBoundedFacts() {
        val snapshot = client.parse(
            """
            {
              "current": {
                "temperature_2m": 24.5,
                "apparent_temperature": 25.8,
                "relative_humidity_2m": 68,
                "weather_code": 2,
                "wind_speed_10m": 12.4,
                "precipitation": 0.0
              },
              "daily": {
                "time": ["2026-08-26", "2026-08-27"],
                "weather_code": [2, 61],
                "temperature_2m_max": [29.0, 27.0],
                "temperature_2m_min": [21.0, 20.0],
                "precipitation_probability_max": [20, 70]
              }
            }
            """.trimIndent(),
        )

        assertEquals(24.5, snapshot.currentTemperatureC!!, 0.001)
        assertEquals(68, snapshot.humidityPercent)
        assertEquals(2, snapshot.days.size)
        assertTrue(snapshot.fallbackAnswer(WeatherHorizon.CURRENT).contains("partly cloudy"))
        assertTrue(snapshot.fallbackAnswer(WeatherHorizon.TOMORROW).contains("precipitation chance 70%"))
        assertTrue(snapshot.contextText(WeatherHorizon.WEEK).length <= 1_500)
    }
}
