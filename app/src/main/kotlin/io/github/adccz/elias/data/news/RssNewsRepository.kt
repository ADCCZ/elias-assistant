package io.github.adccz.elias.data.news

import io.github.adccz.elias.core.coroutines.IoDispatcher
import io.github.adccz.elias.data.cache.SourceCache
import io.github.adccz.elias.data.cache.loadWithCache
import io.github.adccz.elias.domain.model.SourceResult
import io.github.adccz.elias.domain.news.Headline
import io.github.adccz.elias.domain.news.NewsFeed
import io.github.adccz.elias.domain.news.NewsRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Headlines from RSS feeds. The last feed that worked is kept in [cache], one copy per feed. */
class RssNewsRepository @Inject constructor(
    private val client: HttpClient,
    private val parser: RssParser,
    private val clock: Clock,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val cache: SourceCache,
) : NewsRepository {
    // Parsing runs off the main thread, so callers can use this from a ViewModel directly.
    override suspend fun headlines(feed: NewsFeed): SourceResult<List<Headline>> = withContext(ioDispatcher) {
        loadWithCache(
            cache = cache,
            key = "news:${feed.url}",
            clock = clock,
            // Raw bytes, not text: the XML declaration says which encoding the feed uses.
            fetch = { client.get(feed.url).body<ByteArray>() },
            parse = { payload -> parser.parse(payload.inputStream(), feed.name) },
        )
    }
}
