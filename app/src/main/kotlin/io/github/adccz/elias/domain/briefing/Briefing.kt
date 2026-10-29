package io.github.adccz.elias.domain.briefing

import io.github.adccz.elias.domain.speech.CzechNumbers

enum class BriefingKind { MORNING, EVENING }

/** The parts of a briefing. The order here is the speaking order (spec: "Obsah souhrnů"). */
enum class SectionKind {
    // Morning
    GREETING_AND_WEATHER,
    OVERNIGHT_MESSAGES,
    CALENDAR,
    PLAN,
    NEWS,
    WARNINGS,
    CLOSING,

    // Evening

    /** Saturday evening only: "the sabbath has ended", with the greeting. It takes the checklist's place. */
    SABBATH_ENDED,
    CHECKLIST,
    TOMORROW,
    REFLECTION,
    GOOD_NIGHT,
}

data class BriefingSection(val kind: SectionKind, val text: String)

/**
 * A briefing split into sections. Keeping the sections apart lets the overnight messages (M5)
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
}
