package io.github.adccz.elias.domain.briefing

/**
 * Checks that a briefing written by the AI can be spoken as it is.
 * The AI cannot be told to use a low temperature any more (API 2026-10-21), so its output is checked instead.
 */
object BriefingValidator {
    /**
     * Looser than the spec's 120–180 and 60–100 words: a slightly long text is better than a fallback. The morning
     * routine (M5) is added after the AI answer, so the AI's morning part is that much shorter.
     */
    val MORNING_WORDS = (100 - BriefingLength.ROUTINE_WORDS)..(210 - BriefingLength.ROUTINE_WORDS)
    val EVENING_WORDS = 50..125

    private val listOrHeading = Regex("""(^|\n)\s*([-*•#]|\d+[.)])\s""")
    private val leftovers = listOf("**", "__", "{", "}", "[", "]", "_spoken", "```")

    /** Returns null when the briefing is fine, otherwise a short technical reason. */
    fun problem(briefing: BriefingText): String? {
        val words = briefing.wordCount
        val (allowed, firstSection) = when (briefing.kind) {
            BriefingKind.MORNING -> MORNING_WORDS to SectionKind.GREETING_AND_WEATHER
            BriefingKind.EVENING -> EVENING_WORDS to SectionKind.CHECKLIST
            BriefingKind.EXERCISES -> return "Not a briefing"
        }
        return when {
            briefing.sections.none { it.kind == firstSection && it.text.isNotBlank() } -> "The greeting is missing"
            words !in allowed -> "$words words, expected $allowed"
            listOrHeading.containsMatchIn(briefing.text) -> "Contains a list or a heading"
            leftovers.any { it in briefing.text } -> "Contains markup or JSON leftovers"
            else -> null
        }
    }
}
