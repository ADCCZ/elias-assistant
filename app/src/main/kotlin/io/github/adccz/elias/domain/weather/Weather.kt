package io.github.adccz.elias.domain.weather

import io.github.adccz.elias.domain.model.SourceResult
import java.time.ZonedDateTime

/** Hourly forecast for the next days. Values are null when the provider has no data for that hour. */
data class WeatherForecast(val hours: List<HourlyWeather>)

data class HourlyWeather(
    val time: ZonedDateTime,
    val temperatureCelsius: Double?,
    val precipitationMm: Double?,
    val precipitationProbabilityPercent: Int?,
    val condition: WeatherCondition?,
    val windSpeedKmh: Double?,
    val windGustsKmh: Double?,
)

/** Loads the forecast for Elias's home. Implemented in the data layer. */
interface WeatherRepository {
    suspend fun forecast(): SourceResult<WeatherForecast>
}

/**
 * Weather condition from a WMO weather code, with its Czech name for listening.
 *
 * Entries are ordered from calm to severe. The order is used to pick the most significant
 * precipitation of the day.
 */
enum class WeatherCondition(val czech: String, val isPrecipitation: Boolean) {
    CLEAR("jasno", false),
    MAINLY_CLEAR("skoro jasno", false),
    PARTLY_CLOUDY("polojasno", false),
    OVERCAST("zataženo", false),
    FOG("mlha", false),
    DRIZZLE("mrholení", true),
    LIGHT_RAIN("slabý déšť", true),
    RAIN_SHOWERS("přeháňky", true),
    RAIN("déšť", true),
    LIGHT_SNOW("slabé sněžení", true),
    SNOW_SHOWERS("sněhové přeháňky", true),
    SNOW("sněžení", true),
    HEAVY_RAIN_SHOWERS("silné přeháňky", true),
    HEAVY_RAIN("silný déšť", true),
    HEAVY_SNOW("silné sněžení", true),
    FREEZING_DRIZZLE("mrznoucí mrholení", true),
    FREEZING_RAIN("mrznoucí déšť", true),
    THUNDERSTORM("bouřky", true),
    THUNDERSTORM_WITH_HAIL("bouřky s kroupami", true),
    ;

    companion object {
        /** Maps a WMO code (as used by Open-Meteo) to a condition, or null for an unknown code. */
        fun fromWmoCode(code: Int): WeatherCondition? = when (code) {
            0 -> CLEAR
            1 -> MAINLY_CLEAR
            2 -> PARTLY_CLOUDY
            3 -> OVERCAST
            45, 48 -> FOG
            51, 53, 55 -> DRIZZLE
            56, 57 -> FREEZING_DRIZZLE
            61 -> LIGHT_RAIN
            63 -> RAIN
            65 -> HEAVY_RAIN
            66, 67 -> FREEZING_RAIN
            71 -> LIGHT_SNOW
            73, 77 -> SNOW
            75 -> HEAVY_SNOW
            80, 81 -> RAIN_SHOWERS
            82 -> HEAVY_RAIN_SHOWERS
            85, 86 -> SNOW_SHOWERS
            95 -> THUNDERSTORM
            96, 99 -> THUNDERSTORM_WITH_HAIL
            else -> null
        }
    }
}
