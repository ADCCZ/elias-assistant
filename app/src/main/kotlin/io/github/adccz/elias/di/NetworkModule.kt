package io.github.adccz.elias.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.adccz.elias.BuildConfig
import io.github.adccz.elias.data.network.createHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    /** One client for the whole app, so connections are reused. */
    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = createHttpClient(
        engine = OkHttp.create(),
        userAgent = "Elias/${BuildConfig.VERSION_NAME} (+https://github.com/ADCCZ/elias-assistant)",
    )
}
