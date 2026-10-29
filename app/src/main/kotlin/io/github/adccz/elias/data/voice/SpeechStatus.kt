package io.github.adccz.elias.data.voice

import io.github.adccz.elias.domain.voice.SpeechState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What Elias is saying, shared by the playback service (which writes it) and the screens (which show it).
 * Both run in the app's one process, so a singleton is enough: no binding or messages needed.
 */
@Singleton
class SpeechStatus @Inject constructor() {
    private val _state = MutableStateFlow<SpeechState>(SpeechState.Idle)
    val state: StateFlow<SpeechState> = _state.asStateFlow()

    fun set(state: SpeechState) {
        _state.value = state
    }
}
