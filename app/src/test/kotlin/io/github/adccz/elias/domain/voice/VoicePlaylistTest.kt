package io.github.adccz.elias.domain.voice

import io.github.adccz.elias.domain.briefing.BriefingKind
import io.github.adccz.elias.domain.briefing.SectionKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VoicePlaylistTest {
    private val greeting = SpeechClip(SectionKind.GREETING_AND_WEATHER, "voice/clip-0.wav")
    private val calendar = SpeechClip(SectionKind.CALENDAR, "voice/clip-1.wav")
    private val news = SpeechClip(SectionKind.NEWS, "voice/clip-2.wav")

    @Test
    fun `puts a pause between each two sections`() {
        val playlist = BriefingPlaylist.of(BriefingKind.MORNING, listOf(greeting, calendar, news))

        assertEquals(
            listOf(
                PlaylistEntry.Speech(greeting),
                PlaylistEntry.Pause(BriefingPlaylist.PAUSE_MILLIS),
                PlaylistEntry.Speech(calendar),
                PlaylistEntry.Pause(BriefingPlaylist.PAUSE_MILLIS),
                PlaylistEntry.Speech(news),
            ),
            playlist.entries,
        )
    }

    @Test
    fun `one section needs no pause`() {
        val playlist = BriefingPlaylist.of(BriefingKind.EVENING, listOf(greeting))

        assertEquals(listOf(PlaylistEntry.Speech(greeting)), playlist.entries)
    }

    @Test
    fun `keeps the briefing kind and allows an empty playlist`() {
        val playlist = BriefingPlaylist.of(BriefingKind.EVENING, emptyList())

        assertEquals(BriefingKind.EVENING, playlist.kind)
        assertTrue(playlist.entries.isEmpty())
    }

    @Test
    fun `the pause length can be changed`() {
        val playlist = BriefingPlaylist.of(BriefingKind.MORNING, listOf(greeting, news), pauseMillis = 300)

        assertEquals(PlaylistEntry.Pause(300), playlist.entries[1])
    }

    @Test
    fun `briefings the user starts play as media, scheduled ones as an alarm`() {
        assertEquals(PlaybackStream.MEDIA, PlaybackTrigger.USER.stream)
        assertEquals(PlaybackStream.ALARM, PlaybackTrigger.SCHEDULE.stream)
    }
}
