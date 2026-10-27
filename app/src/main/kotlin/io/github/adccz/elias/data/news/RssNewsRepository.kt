package io.github.adccz.elias.data.news

import io.github.adccz.elias.core.coroutines.IoDispatcher
import io.github.adccz.elias.data.network.loadSafely
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

class RssNewsRepository @Inject constructor(
    private val client: HttpClient,
    private val parser: RssParser,
    private val clock: Clock,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : NewsRepository {
    // Parsing runs off the main thread, so callers can use this from a ViewModel directly.
    override suspend fun headlines(feed: NewsFeed): SourceResult<List<Headline>> = withContext(ioDispatcher) {
        loadSafely(clock) {
            // Raw bytes, not text: the XML declaration says which encoding the feed uses.
            val bytes = client.get(feed.url).body<ByteArray>()
            parser.parse(bytes.inputStream(), feed.name)
        }
    }
}
