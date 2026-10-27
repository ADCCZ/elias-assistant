package io.github.adccz.elias.data.weather

import io.github.adccz.elias.data.network.InvalidResponseException
import io.github.adccz.elias.data.network.loadSafely
import io.github.adccz.elias.domain.model.GeoLocation
import io.github.adccz.elias.domain.model.SourceResult
import io.github.adccz.elias.domain.weather.HourlyWeather
import io.github.adccz.elias.domain.weather.WeatherCondition
import io.github.adccz.elias.domain.weather.WeatherForecast
import io.github.adccz.elias.domain.weather.WeatherRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Hourly forecast from Open-Meteo (https://open-meteo.com), free for non-commercial use without a key.
 * Data license CC BY 4.0, so the app shows "Open-Meteo.com" wherever it displays the weather.
 */
class OpenMeteoWeatherRepository @Inject constructor(
    private val client: HttpClient,
    private val location: GeoLocation,
    private val zone: ZoneId,
    private val clock: Clock,
) : WeatherRepository {
    override suspend fun forecast(): SourceResult<WeatherForecast> = loadSafely(clock) {
        val response = client.get(FORECAST_URL) {
            parameter("latitude", location.latitude)
            parameter("longitude", location.longitude)
            parameter("hourly", HOURLY_VARIABLES)
            parameter("timezone", zone.id)
            // Unix time instead of local time strings: Open-Meteo's local times ignore DST changes
            // inside the forecast range. java.time converts the instants correctly.
            parameter("timeformat", "unixtime")
            parameter("forecast_days", FORECAST_DAYS)
            parameter("wind_speed_unit", "kmh")
        }.body<OpenMeteoResponse>()
        response.hourly.toForecast(zone)
    }

    private companion object {
        const val FORECAST_URL = "https://api.open-meteo.com/v1/forecast"
        const val HOURLY_VARIABLES =
            "temperature_2m,precipitation,precipitation_probability,weather_code,wind_speed_10m,wind_gusts_10m"
        const val FORECAST_DAYS = 2
    }
}

@Serializable
internal data class OpenMeteoResponse(val hourly: OpenMeteoHourly)

@Serializable
internal data class OpenMeteoHourly(
    val time: List<Long>,
    @SerialName("temperature_2m") val temperature: List<Double?>,
    val precipitation: List<Double?>,
    @SerialName("precipitation_probability") val precipitationProbability: List<Int?>,
    @SerialName("weather_code") val weatherCode: List<Int?>,
    @SerialName("wind_speed_10m") val windSpeed: List<Double?>,
    @SerialName("wind_gusts_10m") val windGusts: List<Double?>,
) {
    fun toForecast(zone: ZoneId): WeatherForecast {
        val columns = listOf(temperature, precipitation, precipitationProbability, weatherCode, windSpeed, windGusts)
        if (columns.any { it.size != time.size }) {
            throw InvalidResponseException("Open-Meteo hourly arrays have different lengths")
        }
        return WeatherForecast(
            hours = time.indices.map { i ->
                HourlyWeather(
                    time = Instant.ofEpochSecond(time[i]).atZone(zone),
                    temperatureCelsius = temperature[i],
                    precipitationMm = precipitation[i],
                    precipitationProbabilityPercent = precipitationProbability[i],
                    condition = weatherCode[i]?.let(WeatherCondition::fromWmoCode),
                    windSpeedKmh = windSpeed[i],
                    windGustsKmh = windGusts[i],
                )
            },
        )
    }
}
