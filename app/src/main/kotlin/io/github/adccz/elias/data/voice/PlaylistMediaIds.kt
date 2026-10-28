package io.github.adccz.elias.data.voice

import io.github.adccz.elias.domain.voice.PlaylistEntry

/**
 * IDs of the player's media items. The playback service only sees media items, and the ID tells it
 * whether an item is a spoken section or a pause.
 */
object PlaylistMediaIds {
    private const val SPEECH = "speech-"
    private const val PAUSE = "pause-"

    fun of(entry: PlaylistEntry, index: Int): String = when (entry) {
        is PlaylistEntry.Speech -> "$SPEECH$index"
        is PlaylistEntry.Pause -> "$PAUSE$index"
    }

    /**
     * Where "Znovu" (replay) jumps: to the start of the section being spoken. During the pause after a section,
     * that section is repeated, because it is the one the user just missed.
     */
    fun replayIndex(mediaIds: List<String>, currentIndex: Int): Int {
        if (mediaIds.isEmpty()) return 0
        var index = currentIndex.coerceIn(0, mediaIds.lastIndex)
        while (index > 0 && mediaIds[index].startsWith(PAUSE)) index--
        return index
    }
}
