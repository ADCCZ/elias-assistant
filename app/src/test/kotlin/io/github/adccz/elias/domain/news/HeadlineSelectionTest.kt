package io.github.adccz.elias.domain.news

import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HeadlineSelectionTest {
    private val now = Instant.parse("2026-10-27T06:00:00Z")

    private fun headline(title: String, source: String = "ČT24", hoursAgo: Long? = 1, id: String = title) = Headline(
        id = id,
        title = title,
        source = source,
        link = null,
        publishedAt = hoursAgo?.let { now.minusSeconds(it * 3600) },
    )

    @Test
    fun `drops headlines older than 24 hours`() {
        val selected = selectHeadlines(listOf(headline("Čerstvá", hoursAgo = 2), headline("Stará", hoursAgo = 30)), now)

        assertEquals(listOf("Čerstvá"), selected.map { it.title })
    }

    @Test
    fun `sorts newest first and puts undated headlines last`() {
        val selected = selectHeadlines(
            listOf(
                headline("Bez data", hoursAgo = null),
                headline("Starší", hoursAgo = 5),
                headline("Novější", hoursAgo = 1),
            ),
            now,
        )

        assertEquals(listOf("Novější", "Starší", "Bez data"), selected.map { it.title })
    }

    @Test
    fun `removes the same story from two sources, keeping the newer one`() {
        val selected = selectHeadlines(
            listOf(
                headline("Vláda schválila rozpočet", source = "ČT24", hoursAgo = 3),
                headline("vlada schvalila  rozpocet!", source = "iROZHLAS", hoursAgo = 1),
            ),
            now,
        )

        assertEquals(listOf("iROZHLAS"), selected.map { it.source })
    }

    @Test
    fun `removes repeated items with the same id from one source`() {
        val selected = selectHeadlines(
            listOf(
                headline("První verze", id = "a", hoursAgo = 2),
                headline("Opravený titulek", id = "a", hoursAgo = 1),
            ),
            now,
        )

        assertEquals(listOf("Opravený titulek"), selected.map { it.title })
    }

    @Test
    fun `takes at most the given number per source`() {
        val many = (1..8L).map { headline("Zpráva $it", hoursAgo = it) } + headline("Jiný zdroj", source = "Root.cz")

        val selected = selectHeadlines(many, now, perSource = 3)

        assertEquals(3, selected.count { it.source == "ČT24" })
        assertEquals(1, selected.count { it.source == "Root.cz" })
    }
}
