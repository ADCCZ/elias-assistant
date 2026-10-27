package io.github.adccz.elias.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Creates the one HTTP client the app uses.
 *
 * The engine is a parameter: the app passes OkHttp, tests pass Ktor's MockEngine.
 */
fun createHttpClient(engine: HttpClientEngine, userAgent: String, maxRetries: Int = 2): HttpClient =
    HttpClient(engine) {
        // Non-2xx responses throw, so every source handles errors in one place (see loadSafely).
        expectSuccess = true

        // Retry must be installed before the timeout, so each attempt gets its own timeout.
        install(HttpRequestRetry) {
            retryOnExceptionOrServerErrors(maxRetries)
            exponentialDelay()
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 5_000
            requestTimeoutMillis = 10_000
        }
        install(ContentNegotiation) {
            // Providers add new fields over time. They must not break parsing.
            json(Json { ignoreUnknownKeys = true })
        }
        install(UserAgent) {
            agent = userAgent
        }
    }
