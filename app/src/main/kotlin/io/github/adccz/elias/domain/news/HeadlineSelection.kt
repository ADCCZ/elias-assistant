package io.github.adccz.elias.domain.news

import java.text.Normalizer
import java.time.Duration
import java.time.Instant

/**
 * Picks the headlines worth showing: published within [maxAge] before [now], without duplicates,
 * at most [perSource] from each source, newest first. Headlines without a date are kept but go last.
 */
fun selectHeadlines(
    headlines: List<Headline>,
    now: Instant,
    maxAge: Duration = Duration.ofHours(24),
    perSource: Int = 5,
): List<Headline> {
    val oldestAllowed = now.minus(maxAge)
    val newestFirst = compareByDescending<Headline> { it.publishedAt != null }.thenByDescending { it.publishedAt }
    return headlines
        .filter { it.publishedAt == null || it.publishedAt in oldestAllowed..now }
        .sortedWith(newestFirst)
        .distinctBy { it.source to it.id }
        .withoutRepeatedStories()
        .groupBy { it.source }
        .flatMap { (_, fromSource) -> fromSource.take(perSource) }
        .sortedWith(newestFirst)
}

/** Keeps the first (newest) headline of every story and drops later ones that tell the same story. */
private fun List<Headline>.withoutRepeatedStories(): List<Headline> {
    val kept = mutableListOf<Headline>()
    for (headline in this) {
        if (kept.none { isSameStory(it.title, headline.title) }) kept += headline
    }
    return kept
}

/**
 * Two feeds often report one story with different titles, for example
 * "Ministr odvolá ředitele fondu" and "Ministerstvo odvolá ředitele fondu, ten se to dozvěděl z médií".
 *
 * Titles count as the same story when they are equal after [normalizeTitle], or when they share
 * at least [MIN_SHARED_WORDS] significant words that make up most of the shorter title.
 * Words are compared by their first five letters, a rough stand-in for Czech word stems.
 */
internal fun isSameStory(first: String, second: String): Boolean {
    if (normalizeTitle(first) == normalizeTitle(second)) return true
    val firstStems = significantStems(first)
    val secondStems = significantStems(second)
    val shared = (firstStems intersect secondStems).size
    val shorter = minOf(firstStems.size, secondStems.size)
    return shared >= MIN_SHARED_WORDS && shared >= SHARED_SHARE * shorter
}

private const val MIN_SHARED_WORDS = 3
private const val SHARED_SHARE = 0.6
private const val STEM_LENGTH = 5
private const val MIN_WORD_LENGTH = 4

/** Words of four or more letters, cut to their first five letters ("ministerstvo" → "minis"). */
private fun significantStems(title: String): Set<String> = normalizeTitle(title)
    .split(" ")
    .filter { it.length >= MIN_WORD_LENGTH }
    .map { it.take(STEM_LENGTH) }
    .toSet()

/** "Vláda schválila rozpočet!" and "vlada schvalila  rozpocet" become the same key. */
internal fun normalizeTitle(title: String): String = Normalizer.normalize(title, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "") // diacritics
    .lowercase()
    .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
    .trim()
