package io.github.adccz.elias.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.adccz.elias.data.settings.DataStoreVoiceSettingsRepository
import io.github.adccz.elias.data.voice.AndroidTextToSpeech
import io.github.adccz.elias.data.voice.MediaControllerVoicePlayer
import io.github.adccz.elias.domain.voice.SpeechSynthesizer
import io.github.adccz.elias.domain.voice.VoiceChecker
import io.github.adccz.elias.domain.voice.VoicePlayer
import io.github.adccz.elias.domain.voice.VoiceSettingsRepository

/** Voice: text-to-speech and playback. */
@Module
@InstallIn(SingletonComponent::class)
abstract class VoiceModule {
    @Binds
    abstract fun bindVoiceChecker(impl: AndroidTextToSpeech): VoiceChecker

    @Binds
    abstract fun bindSpeechSynthesizer(impl: AndroidTextToSpeech): SpeechSynthesizer

    @Binds
    abstract fun bindVoicePlayer(impl: MediaControllerVoicePlayer): VoicePlayer

    @Binds
    abstract fun bindVoiceSettingsRepository(impl: DataStoreVoiceSettingsRepository): VoiceSettingsRepository
}
