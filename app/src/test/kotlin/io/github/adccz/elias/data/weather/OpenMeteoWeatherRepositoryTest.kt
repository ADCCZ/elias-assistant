package io.github.adccz.elias.data.weather

import io.github.adccz.elias.core.time.PRAGUE_ZONE
import io.github.adccz.elias.data.network.createHttpClient
import io.github.adccz.elias.domain.model.FailureReason
import io.github.adccz.elias.domain.model.GeoLocation
import io.github.adccz.elias.domain.model.SourceResult
import io.github.adccz.elias.domain.weather.WeatherCondition
import io.github.adccz.elias.domain.weather.WeatherForecast
import io.github.adccz.elias.domain.weather.dayWeather
import io.github.adccz.elias.testing.InMemorySourceCache
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenMeteoWeatherRepositoryTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-25T05:00:00Z"), PRAGUE_ZONE)

    /** A fictional forecast for 25 October 2026, the 25-hour day when summer time ends, and the day after. */
    private val dstFixture = javaClass.getResource("/open-meteo/forecast-dst-day.json")!!.readText()

    private val cache = InMemorySourceCache()

    private fun repository(engine: MockEngine) = OpenMeteoWeatherRepository(
        client = createHttpClient(engine, userAgent = "test", maxRetries = 0),
        location = GeoLocation.PILSEN,
        zone = PRAGUE_ZONE,
        clock = clock,
        cache = cache,
    )

    private fun MockRequestHandleScope.respondJson(body: String) =
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

    private suspend fun loadFixture(): WeatherForecast {
        val result = repository(MockEngine { respondJson(dstFixture) }).forecast()
        return assertInstanceOf(SourceResult.Success::class.java, result).data as WeatherForecast
    }

    @Test
    fun `asks for unix time for Pilsen in the Prague zone`() = runTest {
        var requested: Url? = null
        val engine = MockEngine { request ->
            requested = request.url
            respondJson(dstFixture)
        }

        repository(engine).forecast()

        val url = requireNotNull(requested)
        assertEquals("api.open-meteo.com", url.host)
        assertEquals("49.7475", url.parameters["latitude"])
        assertEquals("Europe/Prague", url.parameters["timezone"])
        assertEquals("unixtime", url.parameters["timeformat"])
    }

    @Test
    fun `the DST day has 25 hours and 2 am happens twice`() = runTest {
        val dstDay = loadFixture().hours.filter { it.time.toLocalDate() == LocalDate.of(2026, 10, 25) }

        assertEquals(25, dstDay.size)
        assertEquals(2, dstDay.count { it.time.hour == 2 })
    }

    @Test
    fun `summarizes the DST day by local time`() = runTest {
        val day = requireNotNull(loadFixture().dayWeather(LocalDate.of(2026, 10, 25)))

        assertEquals(7.5, day.morningTemperature!!, 0.001) // 6, 7 and 8 o'clock local time
        assertEquals(11.0, day.afternoonTemperature!!, 0.001)
        assertEquals(WeatherCondition.OVERCAST, day.sky)
        assertEquals(WeatherCondition.LIGHT_RAIN, day.precipitation)
        assertEquals(LocalTime.of(15, 0), day.precipitationFrom)
        assertEquals(1.8, day.precipitationMm, 0.001)
        assertEquals(70, day.maxPrecipitationProbabilityPercent)
        assertEquals(30.0, day.maxGustKmh)
    }

    @Test
    fun `a dry day has no precipitation`() = runTest {
        val day = requireNotNull(loadFixture().dayWeather(LocalDate.of(2026, 10, 26)))

        assertEquals(WeatherCondition.CLEAR, day.sky)
        assertNull(day.precipitation)
        assertNull(day.precipitationFrom)
    }

    @Test
    fun `a day outside the forecast has no summary`() = runTest {
        assertNull(loadFixture().dayWeather(LocalDate.of(2026, 11, 1)))
    }

    @Test
    fun `a server error becomes SERVER_ERROR`() = runTest {
        val result = repository(MockEngine { respondError(HttpStatusCode.InternalServerError) }).forecast()

        assertEquals(SourceResult.Failure(FailureReason.SERVER_ERROR, "HTTP 500"), result)
    }

    @Test
    fun `malformed JSON becomes INVALID_DATA`() = runTest {
        val result = repository(MockEngine { respondJson("{ not json") }).forecast()

        assertEquals(FailureReason.INVALID_DATA, (result as SourceResult.Failure).reason)
    }

    @Test
    fun `arrays of different lengths become INVALID_DATA`() = runTest {
        val broken = dstFixture.replaceFirst("\"temperature_2m\": [", "\"temperature_2m\": [1.0, ")

        val result = repository(MockEngine { respondJson(broken) }).forecast()

        assertEquals(FailureReason.INVALID_DATA, (result as SourceResult.Failure).reason)
    }

    @Test
    fun `no connection becomes NETWORK`() = runTest {
        val result = repository(MockEngine { throw IOException("offline") }).forecast()

        assertEquals(FailureReason.NETWORK, (result as SourceResult.Failure).reason)
    }

    @Test
    fun `returns cached weather when network fails`() = runTest {
        repository(MockEngine { respondJson(dstFixture) }).forecast()

        val result = repository(MockEngine { throw IOException("offline") }).forecast()

        val cached = assertInstanceOf(SourceResult.Success::class.java, result)
        assertTrue(cached.fromCache)
        assertEquals(clock.instant(), cached.loadedAt)
        assertEquals(loadFixture(), cached.data)
    }
}
