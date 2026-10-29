package io.github.adccz.elias.domain.briefing

import io.github.adccz.elias.domain.speech.CzechNumbers

enum class BriefingKind {
    MORNING,
    EVENING,

    /** Not a briefing: today's exercises spoken with "Cvič se mnou" (M5). They share the playback service. */
    EXERCISES,
}

/** The parts of a briefing. The order here is the speaking order (spec: "Obsah souhrnů"). */
enum class SectionKind {
    // Morning
    GREETING_AND_WEATHER,
    OVERNIGHT_MESSAGES,
    CALENDAR,
    PLAN,

    /** The morning routine (M5): reading, exercises and the recipe. Written on the phone, never by the AI. */
    ROUTINE,

    /** Yesterday's step from the journal (M4.5). Written on the phone, never by the AI. */
    JOURNAL_STEP,
    NEWS,
    WARNINGS,
    CLOSING,

    // Evening

    /** Saturday evening only: "the sabbath has ended", with the greeting. It takes the checklist's place. */
    SABBATH_ENDED,
    CHECKLIST,
    TOMORROW,
    REFLECTION,

    /** Today's chapter when nothing was read yet (M5), not on Saturday. Written on the phone. */
    READING_REMINDER,

    /** A reminder to write today's journal entry when it is missing (M4.5). Written on the phone. */
    JOURNAL_REMINDER,
    GOOD_NIGHT,

    // Cvič se mnou (M5)

    /** One thing Elias says while leading the exercises. Never part of a briefing. */
    EXERCISE,
}

data class BriefingSection(val kind: SectionKind, val text: String)

/**
 * A briefing split into sections. Keeping the sections apart lets the overnight messages (M6)
 * be inserted locally after the greeting, even when the rest was written by the AI.
 */
data class BriefingText(val kind: BriefingKind, val sections: List<BriefingSection>) {
    /** The sections Elias says, in speaking order, trimmed and without empty ones. Each one is spoken as one clip. */
    val spokenSections: List<BriefingSection>
        get() = sections
            .sortedBy { it.kind.ordinal }
            .map { it.copy(text = it.text.trim()) }
            .filter { it.text.isNotEmpty() }

    /** The text to speak: the spoken sections separated by a pause (empty line). */
    val text: String get() = spokenSections.joinToString("\n\n") { it.text }

    val wordCount: Int get() = CzechNumbers.wordCount(text)
}

/** Target lengths from the spec: about a minute in the morning, half of that in the evening. */
object BriefingLength {
    val MORNING = 120..180
    val EVENING = 60..100

    /** About how long the morning routine is (M5). It is added after the AI answer. */
    const val ROUTINE_WORDS = 25

    /** What the AI writes in the morning: the whole briefing without the routine. */
    val MORNING_AI = (MORNING.first - ROUTINE_WORDS)..(MORNING.last - ROUTINE_WORDS)
}
