package io.github.adccz.elias.data.voice

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The audio files of spoken briefings, in the app's cache (`cache/voice/`). They never leave the phone.
 *
 * Each briefing gets its own folder. The playback service deletes its briefing's folder when it ends, and a new
 * briefing deletes older folders first, so a service that is just ending never deletes the clips of the next one.
 */
@Singleton
class VoiceFiles(private val directory: File) {
    @Inject constructor(@ApplicationContext context: Context) : this(File(context.cacheDir, "voice"))

    /** A new, empty folder for the clips of one briefing. The clips of older briefings are deleted. */
    fun newBriefingFolder(): File {
        deleteAllBriefings()
        return File(directory, "$BRIEFING_PREFIX${UUID.randomUUID()}").apply { mkdirs() }
    }

    /** The file for the clip of the section at [index]. */
    fun clip(folder: File, index: Int): File = File(folder, "clip-$index.wav")

    /** A silent clip of [millis], written once and then reused. It holds nothing personal. */
    fun silence(millis: Long): File {
        directory.mkdirs()
        val file = File(directory, "pause-$millis.wav")
        if (!file.exists()) {
            // Written under another name first, so a half-written file is never played.
            val temporary = File(directory, "${file.name}.tmp")
            temporary.writeBytes(SilenceWav.create(millis))
            temporary.renameTo(file)
        }
        return file
    }

    /** Deletes the briefing folders that [paths] point into. Anything else, such as the silent clips, stays. */
    fun deleteBriefingsOf(paths: Collection<String>) {
        paths.mapNotNull { File(it).parentFile }
            .filter { it.isBriefingFolder() }
            .toSet()
            .forEach { it.deleteRecursively() }
    }

    fun deleteAllBriefings() {
        directory.listFiles { file -> file.isBriefingFolder() }?.forEach { it.deleteRecursively() }
    }

    private fun File.isBriefingFolder() = parentFile == directory && name.startsWith(BRIEFING_PREFIX)

    private companion object {
        const val BRIEFING_PREFIX = "briefing-"
    }
}
