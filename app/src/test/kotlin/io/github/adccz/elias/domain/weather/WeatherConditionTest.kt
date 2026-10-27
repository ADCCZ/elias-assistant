package io.github.adccz.elias.domain.weather

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class WeatherConditionTest {
    @ParameterizedTest(name = "WMO {0} is {1}")
    @CsvSource(
        "0, CLEAR",
        "3, OVERCAST",
        "48, FOG",
        "61, LIGHT_RAIN",
        "82, HEAVY_RAIN_SHOWERS",
        "86, SNOW_SHOWERS",
        "99, THUNDERSTORM_WITH_HAIL",
    )
    fun `maps WMO codes`(code: Int, expected: WeatherCondition) {
        assertEquals(expected, WeatherCondition.fromWmoCode(code))
    }

    @Test
    fun `an unknown code maps to null`() {
        assertNull(WeatherCondition.fromWmoCode(4))
    }

    @Test
    fun `thunderstorms are more significant than rain`() {
        assertEquals(
            WeatherCondition.THUNDERSTORM,
            listOf(WeatherCondition.RAIN, WeatherCondition.THUNDERSTORM, WeatherCondition.DRIZZLE).max(),
        )
    }
}
