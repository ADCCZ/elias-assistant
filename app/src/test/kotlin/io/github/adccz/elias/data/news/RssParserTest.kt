package io.github.adccz.elias.data.news

import io.github.adccz.elias.data.network.InvalidResponseException
import java.io.File
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir

class RssParserTest {
    private val parser = RssParser()

    private fun parseResource(name: String) =
        parser.parse(javaClass.getResourceAsStream("/rss/$name")!!, sourceName = "Test")

    @Test
    fun `reads CDATA titles, links, guids and GMT dates`() {
        val headlines = parseResource("cdata-gmt-guid.xml")

        assertEquals(2, headlines.size) // the item without a title is skipped
        val first = headlines[0]
        assertEquals("Město otevřelo novou knihovnu", first.title) // whitespace collapsed
        assertEquals("https://example.org/clanek/knihovna", first.id)
        assertEquals("Test", first.source)
        assertEquals(Instant.parse("2026-10-27T12:11:48Z"), first.publishedAt)
    }

    @Test
    fun `keeps special characters and reads a one-digit day`() {
        val second = parseResource("cdata-gmt-guid.xml")[1]

        assertEquals("Vědci & studenti popsali nový druh brouka", second.title)
        assertEquals(Instant.parse("2026-11-03T09:05:00Z"), second.publishedAt)
    }

    @Test
    fun `uses the link as id when there is no guid and reads numeric offsets`() {
        val headline = parseResource("no-guid-offset.xml")[0]

        assertEquals("https://example.net/zpravy/silnice", headline.id)
        assertEquals(Instant.parse("2026-10-27T12:03:00Z"), headline.publishedAt)
    }

    @Test
    fun `ignores a wrong day name`() {
        val headline = parseResource("no-guid-offset.xml")[1] // "Mon, 27 Oct 2026" was a Tuesday

        assertEquals(Instant.parse("2026-10-27T13:00:00Z"), headline.publishedAt)
    }

    @Test
    fun `keeps a headline whose date cannot be read`() {
        val headline = parseResource("no-guid-offset.xml")[2]

        assertEquals("Položka s rozbitým datem", headline.title)
        assertNull(headline.publishedAt)
    }

    @Test
    fun `malformed XML is reported as invalid data`() {
        assertThrows<InvalidResponseException> {
            parser.parse("<rss><channel><item><title>Bez konce".byteInputStream(), "Test")
        }
    }

    @Test
    fun `never reads external entities (XXE)`(@TempDir dir: File) {
        val secret = File(dir, "secret.txt").apply { writeText("TAJNE-HESLO") }
        val attack = """
            <?xml version="1.0"?>
            <!DOCTYPE rss [ <!ENTITY xxe SYSTEM "${secret.toURI()}"> ]>
            <rss><channel><item><title>Ahoj &xxe;</title></item></channel></rss>
        """.trimIndent()

        val titles = runCatching { parser.parse(attack.byteInputStream(), "Test").map { it.title } }

        // Either the parser refuses the document, or the entity stays empty. The file content never leaks.
        assertFalse(titles.getOrDefault(emptyList()).any { "TAJNE-HESLO" in it })
    }
}
