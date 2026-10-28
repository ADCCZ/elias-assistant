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

/** The shortest look back: a briefing played again soon after still has its headlines. */
val MIN_HEADLINE_WINDOW: Duration = Duration.ofHours(6)

/** The longest look back: older news is not news any more. */
val MAX_HEADLINE_WINDOW: Duration = Duration.ofHours(24)

/**
 * How far back the headlines go (spec: "only headlines since the last briefing"): since [lastBriefingAt], the start
 * of the last briefing that played, but at least [MIN_HEADLINE_WINDOW] and at most [MAX_HEADLINE_WINDOW].
 */
fun headlineWindow(lastBriefingAt: Instant?, now: Instant): Duration {
    if (lastBriefingAt == null || lastBriefingAt.isAfter(now)) return MAX_HEADLINE_WINDOW
    val sinceLast = Duration.between(lastBriefingAt, now)
    return sinceLast.coerceIn(MIN_HEADLINE_WINDOW, MAX_HEADLINE_WINDOW)
}

/**
 * Picks [count] headlines for the spoken briefing. Sources take turns in [sourceOrder], each giving its
 * newest headline first, so one busy feed cannot fill the whole briefing. Expects newest-first input,
 * as [selectHeadlines] returns it.
 */
fun pickForBriefing(headlines: List<Headline>, sourceOrder: List<String>, count: Int): List<Headline> {
    val queues = sourceOrder.map { source -> ArrayDeque(headlines.filter { it.source == source }) }
    val picked = mutableListOf<Headline>()
    while (picked.size < count && queues.any { it.isNotEmpty() }) {
        for (queue in queues) {
            if (picked.size < count) queue.removeFirstOrNull()?.let { picked += it }
        }
    }
    return picked
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
