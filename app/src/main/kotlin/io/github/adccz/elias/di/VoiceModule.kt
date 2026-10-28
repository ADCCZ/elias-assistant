package io.github.adccz.elias.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.adccz.elias.data.voice.AndroidTextToSpeech
import io.github.adccz.elias.domain.voice.VoiceChecker

/** Voice: text-to-speech and playback. */
@Module
@InstallIn(SingletonComponent::class)
abstract class VoiceModule {
    @Binds
    abstract fun bindVoiceChecker(impl: AndroidTextToSpeech): VoiceChecker
}
