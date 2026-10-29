package io.github.adccz.elias.domain.speech

import io.github.adccz.elias.domain.bible.BibleBooks
import io.github.adccz.elias.domain.bible.Chapter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class BibleReferenceTest {
    @ParameterizedTest(name = "{0}")
    @CsvSource(
        delimiter = '|',
        value = [
            "Mk 1 | Marek, první kapitola",
            "Mt 25 | Matouš, dvacátá pátá kapitola",
            "1 K 13 | První list Korintským, třináctá kapitola",
            "Jk 1,19–27 | List Jakubův, první kapitola, verše devatenáct až dvacet sedm",
            "J 15,9-17 | Jan, patnáctá kapitola, verše devět až sedmnáct",
            "Ž 139 | Žalm sto třicet devět",
            "Ž 23,1 | Žalm dvacet tři, verš jedna",
            "Př 15,1–4 a 1 Pt 2,21–25 | Přísloví, patnáctá kapitola, verše jedna až čtyři a " +
                "První list Petrův, druhá kapitola, verše dvacet jedna až dvacet pět",
            "1 Pa 5 | První kniha Paralipomenon, pátá kapitola",
            "2 Pa 36 | Druhá kniha Paralipomenon, třicátá šestá kapitola",
            "Ezd 7 | Ezdráš, sedmá kapitola",
            "Na 2 | Nahum, druhá kapitola",
            "Sf 3 | Sofonjáš, třetí kapitola",
            "Jl 4 | Jóel, čtvrtá kapitola",
            "Abd 1 | Abdijáš",
            "Fm 1,4–7 | List Filemonovi, první kapitola, verše čtyři až sedm",
        ],
    )
    fun `reads references aloud`(reference: String, expected: String) {
        assertEquals(expected, BibleReference.spoken(reference))
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = ["Xy 3", "Číst cokoli", "Mk", "Mk 17", "Mal 4", "Ž 151"])
    fun `leaves unknown text unchanged`(text: String) {
        assertEquals(text, BibleReference.spoken(text))
    }

    @Test
    fun `reads one chapter of the queue aloud`() {
        fun spoken(abbreviation: String, number: Int) =
            BibleReference.spoken(Chapter(BibleBooks.byAbbreviation(abbreviation)!!, number))

        assertEquals("Marek, třetí kapitola", spoken("Mk", 3))
        assertEquals("Žalm dvacet tři", spoken("Ž", 23))
        assertEquals("List Judův", spoken("Ju", 1))
    }

    @Test
    fun `finds the chapters of a reference without the verses`() {
        val chapters = BibleReference.chapters("Ř 12,1–8 a 1 J 4,7–12 a Xy 3 a Mk 17")

        assertEquals(listOf("Ř 12", "1 J 4"), chapters.map { "${it.book.abbreviation} ${it.number}" })
    }
}
