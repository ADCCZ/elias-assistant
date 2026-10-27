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
    return headlines
        .filter { it.publishedAt == null || it.publishedAt in oldestAllowed..now }
        .sortedWith(compareByDescending<Headline> { it.publishedAt != null }.thenByDescending { it.publishedAt })
        .distinctBy { it.source to it.id }
        // The same story can come from two feeds with the same title. Keep the newest one.
        .distinctBy { normalizeTitle(it.title) }
        .groupBy { it.source }
        .flatMap { (_, fromSource) -> fromSource.take(perSource) }
        .sortedWith(compareByDescending<Headline> { it.publishedAt != null }.thenByDescending { it.publishedAt })
}

/** "Vláda schválila rozpočet!" and "vlada schvalila  rozpocet" become the same key. */
internal fun normalizeTitle(title: String): String = Normalizer.normalize(title, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "") // diacritics
    .lowercase()
    .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
    .trim()
