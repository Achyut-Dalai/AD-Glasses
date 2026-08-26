package com.ad_glasses.ai.grounding

import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

data class WeatherDay(
    val date: String,
    val weatherCode: Int?,
    val minTemperatureC: Double?,
    val maxTemperatureC: Double?,
    val precipitationProbabilityPercent: Int?,
)

data class WeatherSnapshot(
    val currentTemperatureC: Double?,
    val apparentTemperatureC: Double?,
    val humidityPercent: Int?,
    val currentWeatherCode: Int?,
    val windSpeedKmh: Double?,
    val precipitationMm: Double?,
    val days: List<WeatherDay>,
) {
    fun fallbackAnswer(horizon: WeatherHorizon): String = when (horizon) {
        WeatherHorizon.CURRENT -> currentAnswer()
        WeatherHorizon.TODAY -> dayAnswer(days.getOrNull(0), "Today") ?: currentAnswer()
        WeatherHorizon.TOMORROW -> dayAnswer(days.getOrNull(1), "Tomorrow") ?: currentAnswer()
        WeatherHorizon.WEEK -> weekAnswer() ?: currentAnswer()
    }

    fun contextText(horizon: WeatherHorizon): String = buildString {
        append("Open-Meteo weather data. Requested horizon: ${horizon.name.lowercase(Locale.US)}.")
        currentTemperatureC?.let { append(" Current temperature ${formatNumber(it)} C.") }
        apparentTemperatureC?.let { append(" Feels like ${formatNumber(it)} C.") }
        humidityPercent?.let { append(" Humidity $it percent.") }
        currentWeatherCode?.let { append(" Current conditions ${weatherDescription(it)} (WMO code $it).") }
        windSpeedKmh?.let { append(" Wind ${formatNumber(it)} km/h.") }
        precipitationMm?.let { append(" Current precipitation ${formatNumber(it)} mm.") }
        days.take(7).forEach { day ->
            append(" ${day.date}:")
            day.weatherCode?.let { append(" ${weatherDescription(it)};") }
            day.minTemperatureC?.let { append(" low ${formatNumber(it)} C;") }
            day.maxTemperatureC?.let { append(" high ${formatNumber(it)} C;") }
            day.precipitationProbabilityPercent?.let { append(" precipitation chance $it percent;") }
        }
    }.take(MAX_CONTEXT_CHARS)

    private fun currentAnswer(): String = buildString {
        val temperature = currentTemperatureC
        if (temperature != null) {
            append("It's ${formatNumber(temperature)}°C")
        } else {
            append("Current weather is available")
        }
        currentWeatherCode?.let { append(" and ${weatherDescription(it)}") }
        apparentTemperatureC?.takeIf { currentTemperatureC == null || kotlin.math.abs(it - currentTemperatureC) >= 1.0 }
            ?.let { append(", feels like ${formatNumber(it)}°C") }
        humidityPercent?.let { append(", humidity $it%") }
        windSpeedKmh?.let { append(", wind ${formatNumber(it)} km/h") }
        append('.')
    }

    private fun dayAnswer(day: WeatherDay?, label: String): String? {
        day ?: return null
        return buildString {
            append(label)
            day.weatherCode?.let { append(" will be ${weatherDescription(it)}") }
            val low = day.minTemperatureC
            val high = day.maxTemperatureC
            if (low != null && high != null) {
                append(", around ${formatNumber(low)}–${formatNumber(high)}°C")
            } else if (high != null) {
                append(", with a high near ${formatNumber(high)}°C")
            }
            day.precipitationProbabilityPercent?.let { append(", precipitation chance $it%") }
            append('.')
        }
    }

    private fun weekAnswer(): String? {
        if (days.isEmpty()) return null
        val lows = days.mapNotNull(WeatherDay::minTemperatureC)
        val highs = days.mapNotNull(WeatherDay::maxTemperatureC)
        val precip = days.mapNotNull(WeatherDay::precipitationProbabilityPercent)
        return buildString {
            append("Over the next week")
            if (lows.isNotEmpty() && highs.isNotEmpty()) {
                append(", temperatures are roughly ${formatNumber(lows.minOrNull()!!)}–${formatNumber(highs.maxOrNull()!!)}°C")
            }
            if (precip.isNotEmpty()) append(", with peak precipitation chance around ${precip.maxOrNull()}%")
            append('.')
        }
    }

    private companion object {
        const val MAX_CONTEXT_CHARS = 1_500
    }
}

/** Keyless Open-Meteo forecast client. The public endpoint is suitable only under its own usage terms. */
class OpenMeteoWeatherClient(
    private val client: OkHttpClient = defaultClient(),
) {
    suspend fun forecast(point: GeoPoint): Result<WeatherSnapshot> = try {
        val url = FORECAST_URL.toHttpUrl().newBuilder()
            .addQueryParameter("latitude", point.latitude.toString())
            .addQueryParameter("longitude", point.longitude.toString())
            .addQueryParameter(
                "current",
                "temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,precipitation",
            )
            .addQueryParameter(
                "daily",
                "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max",
            )
            .addQueryParameter("forecast_days", "7")
            .addQueryParameter("timezone", "auto")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()
        val call = client.newCall(request)
        call.timeout().timeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        val snapshot = call.awaitResponse().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Open-Meteo HTTP ${response.code}")
            parse(payload)
        }
        Result.success(snapshot)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }

    internal fun parse(payload: String): WeatherSnapshot {
        val root = JSONObject(payload)
        val current = root.optJSONObject("current") ?: JSONObject()
        val daily = root.optJSONObject("daily") ?: JSONObject()
        val dates = daily.optJSONArray("time")
        val codes = daily.optJSONArray("weather_code")
        val maxTemps = daily.optJSONArray("temperature_2m_max")
        val minTemps = daily.optJSONArray("temperature_2m_min")
        val precip = daily.optJSONArray("precipitation_probability_max")
        val dayCount = listOfNotNull(dates, codes, maxTemps, minTemps, precip)
            .maxOfOrNull(JSONArray::length)
            ?.coerceAtMost(7)
            ?: 0
        val days = buildList {
            for (index in 0 until dayCount) {
                val date = dates.optStringOrNull(index) ?: continue
                add(
                    WeatherDay(
                        date = date.take(20),
                        weatherCode = codes.optIntOrNull(index),
                        minTemperatureC = minTemps.optDoubleOrNull(index),
                        maxTemperatureC = maxTemps.optDoubleOrNull(index),
                        precipitationProbabilityPercent = precip.optIntOrNull(index),
                    ),
                )
            }
        }
        return WeatherSnapshot(
            currentTemperatureC = current.optDoubleOrNull("temperature_2m"),
            apparentTemperatureC = current.optDoubleOrNull("apparent_temperature"),
            humidityPercent = current.optIntOrNull("relative_humidity_2m"),
            currentWeatherCode = current.optIntOrNull("weather_code"),
            windSpeedKmh = current.optDoubleOrNull("wind_speed_10m"),
            precipitationMm = current.optDoubleOrNull("precipitation"),
            days = days,
        )
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (!has(key) || isNull(key)) null else optDouble(key, Double.NaN).takeIf { !it.isNaN() }

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (!has(key) || isNull(key)) null else optInt(key)

    private fun JSONArray?.optStringOrNull(index: Int): String? {
        if (this == null || index >= length() || isNull(index)) return null
        return optString(index).trim().takeIf(String::isNotBlank)
    }

    private fun JSONArray?.optDoubleOrNull(index: Int): Double? {
        if (this == null || index >= length() || isNull(index)) return null
        return optDouble(index, Double.NaN).takeIf { !it.isNaN() }
    }

    private fun JSONArray?.optIntOrNull(index: Int): Int? {
        if (this == null || index >= length() || isNull(index)) return null
        return optInt(index)
    }

    companion object {
        const val SOURCE_URL = "https://open-meteo.com/"
        private const val FORECAST_URL = "https://api.open-meteo.com/v1/forecast"
        private const val USER_AGENT = "AD-Glasses Android weather client"
        private const val CALL_TIMEOUT_SECONDS = 5L

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(6, TimeUnit.SECONDS)
            .build()
    }
}

private fun weatherDescription(code: Int): String = when (code) {
    0 -> "clear"
    1 -> "mostly clear"
    2 -> "partly cloudy"
    3 -> "overcast"
    45, 48 -> "foggy"
    51, 53, 55, 56, 57 -> "drizzly"
    61, 63, 65, 66, 67 -> "rainy"
    71, 73, 75, 77 -> "snowy"
    80, 81, 82 -> "showery"
    85, 86 -> "snow showers"
    95, 96, 99 -> "thunderstorms"
    else -> "mixed conditions"
}

private fun formatNumber(value: Double): String =
    if (kotlin.math.abs(value - value.toInt()) < 0.05) value.toInt().toString()
    else String.format(Locale.US, "%.1f", value)

private suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, error: IOException) {
            continuation.resumeWithException(error)
        }

        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response) { _, resource, _ -> resource.close() }
        }
    })
}
