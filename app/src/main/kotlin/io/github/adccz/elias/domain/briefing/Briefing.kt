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
    /** The text to speak: non-empty sections in speaking order, separated by a pause (empty line). */
    val text: String
        get() = sections
            .sortedBy { it.kind.ordinal }
            .map { it.text.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n\n")

    val wordCount: Int get() = CzechNumbers.wordCount(text)
}

/** Target lengths from the spec: about a minute in the morning, half of that in the evening. */
object BriefingLength {
    val MORNING = 120..180
    val EVENING = 60..100
}
