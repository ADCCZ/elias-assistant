package io.github.adccz.elias.data.news

import io.github.adccz.elias.data.network.InvalidResponseException
import io.github.adccz.elias.domain.news.Headline
import java.io.InputStream
import java.io.StringReader
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler

/**
 * Reads the items of an RSS 2.0 feed.
 *
 * It uses the SAX parser from `javax.xml`, which exists both on Android and on the desktop JVM,
 * so the same code runs in the app and in unit tests without extra libraries.
 */
class RssParser @Inject constructor() {
    /** @throws InvalidResponseException when the XML is malformed. */
    fun parse(xml: InputStream, sourceName: String): List<Headline> {
        val handler = RssHandler(sourceName)
        try {
            val parser = SAXParserFactory.newInstance().apply {
                // Defense against XXE attacks: never load external entities or DTDs.
                // Not every parser knows every feature, so unknown ones are skipped.
                trySetFeature("http://xml.org/sax/features/external-general-entities", false)
                trySetFeature("http://xml.org/sax/features/external-parameter-entities", false)
                trySetFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            }.newSAXParser()
            parser.parse(InputSource(xml), handler)
        } catch (e: SAXException) {
            throw InvalidResponseException("$sourceName: malformed RSS (${e.message})")
        }
        return handler.headlines
    }

    private fun SAXParserFactory.trySetFeature(name: String, value: Boolean) {
        runCatching { setFeature(name, value) }
    }
}

private class RssHandler(private val sourceName: String) : DefaultHandler() {
    val headlines = mutableListOf<Headline>()

    private var insideItem = false
    private val text = StringBuilder()
    private var title: String? = null
    private var link: String? = null
    private var guid: String? = null
    private var pubDate: String? = null

    override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes?) {
        if (qName == "item") {
            insideItem = true
            title = null
            link = null
            guid = null
            pubDate = null
        }
        text.setLength(0)
    }

    override fun characters(ch: CharArray, start: Int, length: Int) {
        text.appendRange(ch, start, start + length)
    }

    override fun endElement(uri: String?, localName: String?, qName: String) {
        if (!insideItem) return
        val value = text.toString().cleanWhitespace().ifEmpty { null }
        when (qName) {
            "title" -> title = value

            "link" -> link = value

            "guid" -> guid = value

            "pubDate" -> pubDate = value

            "item" -> {
                insideItem = false
                addHeadline()
            }
        }
    }

    // A second line of defense against XXE: any external entity resolves to an empty document.
    override fun resolveEntity(publicId: String?, systemId: String?): InputSource = InputSource(StringReader(""))

    private fun addHeadline() {
        val title = title ?: return // an item without a title is useless for a spoken briefing
        headlines += Headline(
            id = guid ?: link ?: title,
            title = title,
            source = sourceName,
            link = link,
            publishedAt = pubDate?.let(::parseRfc1123),
        )
    }
}

private fun String.cleanWhitespace(): String = trim().replace(Regex("\\s+"), " ")

/**
 * RSS dates look like "Tue, 27 Oct 2026 12:11:48 GMT" or "Tue, 27 Oct 2026 13:03:00 +0100".
 * The day name is dropped before parsing: feeds sometimes get it wrong, and java.time would reject the date.
 */
private fun parseRfc1123(value: String): Instant? = try {
    val withoutDayName = value.replace(Regex("^\\p{Alpha}+,\\s*"), "")
    ZonedDateTime.parse(withoutDayName, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
} catch (e: DateTimeParseException) {
    null // a headline with a broken date is still a headline
}
