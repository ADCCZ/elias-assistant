package io.github.adccz.elias.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.adccz.elias.data.settings.DataStoreVoiceSettingsRepository
import io.github.adccz.elias.data.voice.AndroidTextToSpeech
import io.github.adccz.elias.data.voice.PiperTextToSpeech
import io.github.adccz.elias.data.voice.PlaybackServiceClient
import io.github.adccz.elias.domain.voice.BuiltInVoice
import io.github.adccz.elias.domain.voice.PhoneVoice
import io.github.adccz.elias.domain.voice.SelectedVoice
import io.github.adccz.elias.domain.voice.SpeechSynthesizer
import io.github.adccz.elias.domain.voice.VoiceChecker
import io.github.adccz.elias.domain.voice.VoicePlayer
import io.github.adccz.elias.domain.voice.VoiceSettingsRepository

/** Voice: text-to-speech and playback. */
@Module
@InstallIn(SingletonComponent::class)
abstract class VoiceModule {
    /** The voice the user chose, with the phone's voice as the fallback. */
    @Binds
    abstract fun bindVoiceChecker(impl: SelectedVoice): VoiceChecker

    @Binds
    abstract fun bindSpeechSynthesizer(impl: SelectedVoice): SpeechSynthesizer

    @Binds
    @PhoneVoice
    abstract fun bindPhoneVoiceChecker(impl: AndroidTextToSpeech): VoiceChecker

    @Binds
    @PhoneVoice
    abstract fun bindPhoneSpeechSynthesizer(impl: AndroidTextToSpeech): SpeechSynthesizer

    @Binds
    abstract fun bindBuiltInVoice(impl: PiperTextToSpeech): BuiltInVoice

    @Binds
    abstract fun bindVoicePlayer(impl: PlaybackServiceClient): VoicePlayer

    @Binds
    abstract fun bindVoiceSettingsRepository(impl: DataStoreVoiceSettingsRepository): VoiceSettingsRepository
}
