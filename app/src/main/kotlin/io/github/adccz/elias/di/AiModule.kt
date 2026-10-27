package io.github.adccz.elias.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.adccz.elias.BuildConfig
import io.github.adccz.elias.data.ai.ClaudeBriefingBuilder
import io.github.adccz.elias.data.ai.ClaudeConfig
import io.github.adccz.elias.domain.briefing.AiBriefingBuilder

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {
    @Binds
    abstract fun bindAiBriefingBuilder(impl: ClaudeBriefingBuilder): AiBriefingBuilder

    companion object {
        /** The key comes from local.properties via BuildConfig. It is empty when not set, and then AI is skipped. */
        @Provides
        fun provideClaudeConfig(): ClaudeConfig = ClaudeConfig(apiKey = BuildConfig.ANTHROPIC_API_KEY)
    }
}
