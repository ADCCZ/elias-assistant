package io.github.adccz.elias.data.voice

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class VoiceFilesTest {
    @TempDir
    lateinit var cache: File

    private val voice by lazy { File(cache, "voice") }
    private val files by lazy { VoiceFiles(voice) }

    private fun writeClip(folder: File, index: Int) = files.clip(folder, index).apply { writeText("audio") }

    @Test
    fun `each briefing gets its own empty folder`() {
        val first = files.newBriefingFolder()
        val second = files.newBriefingFolder()

        assertNotEquals(first, second)
        assertTrue(second.isDirectory)
        assertEquals(second, files.clip(second, 0).parentFile)
    }

    @Test
    fun `a new briefing deletes the clips of older ones but keeps the silence`() {
        val old = files.newBriefingFolder()
        writeClip(old, 0)
        val silence = files.silence(700)

        files.newBriefingFolder()

        assertFalse(old.exists())
        assertTrue(silence.exists())
    }

    @Test
    fun `an ending service deletes only its own briefing`() {
        val ending = files.newBriefingFolder()
        val endingClip = writeClip(ending, 0)
        val next = File(voice, "briefing-next").apply { mkdirs() }
        val nextClip = writeClip(next, 0)

        files.deleteBriefingsOf(listOf(endingClip.path, files.silence(700).path))

        assertFalse(ending.exists())
        assertTrue(nextClip.exists())
        assertTrue(files.silence(700).exists())
    }

    @Test
    fun `never deletes files outside its folder`() {
        val outside = File(cache, "briefing-other/clip-0.wav").apply {
            parentFile.mkdirs()
            writeText("not ours")
        }

        files.deleteBriefingsOf(listOf(outside.path))

        assertTrue(outside.exists())
    }

    @Test
    fun `the silent clip is written once`() {
        val first = files.silence(700)
        val modified = first.lastModified()

        val second = files.silence(700)

        assertEquals(first, second)
        assertEquals(modified, second.lastModified())
        assertEquals(SilenceWav.create(700).size.toLong(), second.length())
    }
}
