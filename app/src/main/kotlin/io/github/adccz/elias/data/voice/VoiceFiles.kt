package io.github.adccz.elias.data.voice

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The audio files of a spoken briefing, in the app's cache (`cache/voice/`). They never leave the phone.
 * The clips are deleted before the next briefing is spoken and when playback ends.
 */
@Singleton
class VoiceFiles @Inject constructor(@ApplicationContext context: Context) {
    private val directory = File(context.cacheDir, "voice")

    /** The file for the clip of the section at [index]. */
    fun clip(index: Int): File = File(ensureDirectory(), "clip-$index.wav")

    /** A silent clip of [millis], written once and then reused. */
    fun silence(millis: Long): File {
        val file = File(ensureDirectory(), "pause-$millis.wav")
        if (!file.exists()) {
            // Written under another name first, so a half-written file is never played.
            val temporary = File(directory, "${file.name}.tmp")
            temporary.writeBytes(SilenceWav.create(millis))
            temporary.renameTo(file)
        }
        return file
    }

    /** Deletes the spoken clips. The silent clips hold nothing personal and stay. */
    fun deleteClips() {
        directory.listFiles { file -> file.name.startsWith("clip-") }?.forEach { it.delete() }
    }

    private fun ensureDirectory(): File = directory.apply { mkdirs() }
}
