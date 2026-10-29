package io.github.adccz.elias.domain.routine

import io.github.adccz.elias.domain.briefing.BriefingKind
import io.github.adccz.elias.domain.briefing.SectionKind
import io.github.adccz.elias.domain.voice.ExercisePlaylist
import io.github.adccz.elias.domain.voice.PlaylistEntry
import io.github.adccz.elias.domain.voice.SpeechClip
import io.github.adccz.elias.testing.TestRoutine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExerciseScriptTest {
    private val shoulders = TestRoutine.exercises[0] // ten times
    private val twist = TestRoutine.exercises[1] // twenty seconds on each side

    @Test
    fun `opens with the safety sentence and ends quietly`() {
        val steps = ExerciseScript.of(listOf(shoulders))

        assertEquals(
            ExerciseStep.Say(
                "Cvičíme spolu. Cvič pomalu a jen do příjemného tahu, když něco bolí, cvik vynech.",
            ),
            steps.first(),
        )
        assertEquals(ExerciseStep.Wait(ExerciseScript.AFTER_INTRO_MILLIS), steps[1])
        assertEquals(ExerciseStep.Say("To byly dnešní cviky."), steps.last())
    }

    @Test
    fun `reads each exercise, then leaves time to do it`() {
        val steps = ExerciseScript.of(listOf(shoulders))

        assertEquals(ExerciseStep.Say("První cvik: kroužení rameny. Krouž rameny. Desetkrát. Začni."), steps[2])
        // Ten repetitions of three seconds.
        assertEquals(ExerciseStep.Wait(30_000), steps[3])
    }

    @Test
    fun `announces the other side`() {
        val steps = ExerciseScript.of(listOf(shoulders, twist))

        assertEquals(
            listOf(
                ExerciseStep.Say("Druhý cvik: rotace trupu vsedě. Otoč trup. Dvacet sekund, na každou stranu. Začni."),
                ExerciseStep.Wait(20_000),
                ExerciseStep.Say("Druhá strana."),
                ExerciseStep.Wait(20_000),
            ),
            steps.subList(4, 8),
        )
    }

    @Test
    fun `the four exercises of a day take a few minutes`() {
        val steps = ExerciseScript.of(TestRoutine.exercises)
        val silentMillis = steps.filterIsInstance<ExerciseStep.Wait>().sumOf { it.millis }

        assertTrue(silentMillis in 60_000L..300_000L) { "$silentMillis ms" }
        assertTrue(steps.filterIsInstance<ExerciseStep.Say>().none { step -> step.text.any(Char::isDigit) })
    }

    @Test
    fun `without exercises there is nothing to say`() {
        assertEquals(emptyList<ExerciseStep>(), ExerciseScript.of(emptyList()))
    }

    @Test
    fun `the playlist puts the clips and the silences in the order of the steps`() {
        val steps = ExerciseScript.of(listOf(twist))
        val clips = steps.filterIsInstance<ExerciseStep.Say>().mapIndexed { index, _ ->
            SpeechClip(SectionKind.EXERCISE, "clip-$index.wav")
        }

        val playlist = ExercisePlaylist.of(steps, clips)

        assertEquals(BriefingKind.EXERCISES, playlist.kind)
        assertEquals(
            listOf(
                PlaylistEntry.Speech(clips[0]),
                PlaylistEntry.Pause(ExerciseScript.AFTER_INTRO_MILLIS),
                PlaylistEntry.Speech(clips[1]),
                PlaylistEntry.Pause(20_000),
                PlaylistEntry.Speech(clips[2]),
                PlaylistEntry.Pause(20_000),
                PlaylistEntry.Speech(clips[3]),
            ),
            playlist.entries,
        )
    }
}
