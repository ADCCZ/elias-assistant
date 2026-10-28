package io.github.adccz.elias.data.voice

import io.github.adccz.elias.domain.briefing.BriefingKind
import io.github.adccz.elias.domain.briefing.SectionKind
import io.github.adccz.elias.domain.voice.BriefingPlaylist
import io.github.adccz.elias.domain.voice.SpeechClip
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlaylistMediaIdsTest {
    private val playlist = BriefingPlaylist.of(
        BriefingKind.MORNING,
        listOf(
            SpeechClip(SectionKind.GREETING_AND_WEATHER, "clip-0.wav"),
            SpeechClip(SectionKind.CALENDAR, "clip-1.wav"),
            SpeechClip(SectionKind.NEWS, "clip-2.wav"),
        ),
    )
    private val ids = playlist.entries.mapIndexed { index, entry -> PlaylistMediaIds.of(entry, index) }

    @Test
    fun `names sections and pauses by their position`() {
        assertEquals(listOf("speech-0", "pause-1", "speech-2", "pause-3", "speech-4"), ids)
    }

    @Test
    fun `replay starts the section being spoken again`() {
        assertEquals(2, PlaylistMediaIds.replayIndex(ids, currentIndex = 2))
        assertEquals(0, PlaylistMediaIds.replayIndex(ids, currentIndex = 0))
    }

    @Test
    fun `replay during a pause repeats the section before it`() {
        assertEquals(2, PlaylistMediaIds.replayIndex(ids, currentIndex = 3))
    }

    @Test
    fun `replay copes with an empty playlist and an index out of range`() {
        assertEquals(0, PlaylistMediaIds.replayIndex(emptyList(), currentIndex = 4))
        assertEquals(4, PlaylistMediaIds.replayIndex(ids, currentIndex = 9))
        assertEquals(0, PlaylistMediaIds.replayIndex(ids, currentIndex = -1))
    }
}
