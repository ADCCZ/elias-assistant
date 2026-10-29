package io.github.adccz.elias.domain.news

import io.github.adccz.elias.domain.model.SourceResult
import java.time.Instant

/** One news headline. [id] is unique within its source (the RSS guid, or the link when there is no guid). */
data class Headline(
    val id: String,
    val title: String,
    val source: String,
    val link: String?,
    val publishedAt: Instant?,
)

/** An RSS feed Elias reads. */
data class NewsFeed(val name: String, val url: String)

/** Loads headlines from one feed. Implemented in the data layer. */
interface NewsRepository {
    suspend fun headlines(feed: NewsFeed): SourceResult<List<Headline>>
}

/** Feeds used until they can be chosen in settings (M8). URLs checked on 2026-10-27. */
object DefaultNewsFeeds {
    val all = listOf(
        // "Hlavní zprávy" redirects to the editors' selection.
        NewsFeed(name = "ČT24", url = "https://ct24.ceskatelevize.cz/rss/tema/vyber-redakce-84313"),
        NewsFeed(name = "iROZHLAS", url = "https://www.irozhlas.cz/rss/irozhlas"),
        NewsFeed(name = "Root.cz", url = "https://www.root.cz/rss/zpravicky/"),
    )
}
