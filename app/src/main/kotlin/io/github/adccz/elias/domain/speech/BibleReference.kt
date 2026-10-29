package io.github.adccz.elias.domain.speech

import io.github.adccz.elias.domain.bible.BibleBooks
import io.github.adccz.elias.domain.bible.Chapter

/**
 * Turns Czech Bible references into words for listening:
 * "Mk 1" → "Marek, první kapitola", "Jk 1,19–27" → "List Jakubův, první kapitola, verše devatenáct až dvacet sedm",
 * "Ž 139" → "Žalm sto třicet devět". Several references joined by " a " are read one after another.
 *
 * Abbreviations follow the Czech ecumenical translation ([BibleBooks]). An unknown reference is returned unchanged,
 * so a typo in the plan never breaks the briefing.
 */
object BibleReference {
    fun spoken(reference: String): String = reference
        .split(" a ")
        .joinToString(" a ") { spokenSingle(it.trim()) }

    /**
     * One chapter: "Marek, třetí kapitola", "Žalm dvacet tři". A book with one chapter is only named ("List Filemonovi").
     */
    fun spoken(chapter: Chapter): String {
        val book = chapter.book
        return when {
            book.isPsalms -> "${book.spokenName} ${label(chapter.number)}"
            book.chapters == 1 -> book.spokenName
            else -> "${book.spokenName}, ${CzechNumbers.ordinalFeminine(chapter.number)} kapitola"
        }
    }

    /**
     * The chapters a reference points to, verses left out: "Ř 12,1–8" is Romans 12, "Ž 23 a 1 J 4,7–12" is Psalm 23
     * and 1 John 4. Parts that are not a known book and chapter are skipped.
     */
    fun chapters(reference: String): List<Chapter> = reference
        .split(" a ")
        .mapNotNull { parse(it.trim())?.chapter }

    private class Parsed(val chapter: Chapter, val firstVerse: Int?, val lastVerse: Int?)

    private val pattern = Regex("""^((?:\d\s?)?\p{L}+)\s+(\d+)(?:,(\d+)(?:\s*[–-]\s*(\d+))?)?$""")

    private fun parse(reference: String): Parsed? {
        val match = pattern.matchEntire(reference) ?: return null
        val (abbreviation, chapterText, firstVerseText, lastVerseText) = match.destructured
        val book = BibleBooks.byAbbreviation(abbreviation) ?: return null
        val number = chapterText.toIntOrNull()?.takeIf { it in 1..book.chapters } ?: return null
        return Parsed(Chapter(book, number), firstVerseText.toIntOrNull(), lastVerseText.toIntOrNull())
    }

    private fun spokenSingle(reference: String): String {
        val parsed = parse(reference) ?: return reference
        val chapter = parsed.chapter
        val firstVerse = parsed.firstVerse ?: return spoken(chapter)
        // With verses the chapter is always said, also in a book with one chapter.
        val chapterPart = if (chapter.book.isPsalms) {
            spoken(chapter)
        } else {
            "${chapter.book.spokenName}, ${CzechNumbers.ordinalFeminine(chapter.number)} kapitola"
        }
        val versePart = parsed.lastVerse
            ?.let { ", verše ${label(firstVerse)} až ${label(it)}" }
            ?: ", verš ${label(firstVerse)}"
        return chapterPart + versePart
    }

    /** Numbers used as labels are read in the counting form: "jedna, dva, tři" ("verš jedna", "Žalm dva"). */
    private fun label(number: Int): String = if (number == 1) "jedna" else CzechNumbers.cardinal(number)
}
