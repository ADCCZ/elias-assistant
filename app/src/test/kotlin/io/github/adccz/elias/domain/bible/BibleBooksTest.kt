package io.github.adccz.elias.domain.bible

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class BibleBooksTest {
    @Test
    fun `has the sixty-six books of the protestant canon with ČEP chapter counts`() {
        assertEquals(66, BibleBooks.all.size)
        assertEquals(39, BibleBooks.all.indexOfFirst { it.abbreviation == "Mt" })
        // ČEP numbers Joel and Malachi like the Hebrew Bible: 4 and 3 chapters instead of 3 and 4.
        assertEquals(1189, BibleBooks.all.sumOf { it.chapters })
        assertEquals(66, BibleBooks.all.map { it.abbreviation }.toSet().size)
        assertEquals(66, BibleBooks.all.map { it.name }.toSet().size)
    }

    @ParameterizedTest(name = "{0} has {1} chapters")
    @CsvSource(
        "Gn, 50",
        "Ž, 150",
        "Iz, 66",
        "Jl, 4",
        "Mal, 3",
        "Abd, 1",
        "Mk, 16",
        "Sk, 28",
        "Zj, 22",
    )
    fun `knows the number of chapters`(abbreviation: String, chapters: Int) {
        assertEquals(chapters, BibleBooks.byAbbreviation(abbreviation)?.chapters)
    }

    @ParameterizedTest(name = "\"{0}\" is {1}")
    @CsvSource(
        "1 K, 1. Korintským",
        "1K, 1. Korintským",
        "2Pa, 2. Paralipomenon",
        "' Ezd ', Ezdráš",
        "Ab, Abakuk",
        "Abk, Abakuk",
        "Sf, Sofonjáš",
    )
    fun `finds a book by its abbreviation`(abbreviation: String, name: String) {
        assertEquals(name, BibleBooks.byAbbreviation(abbreviation)?.name)
    }

    @Test
    fun `an unknown abbreviation finds nothing`() {
        assertNull(BibleBooks.byAbbreviation("Xy"))
        assertNull(BibleBooks.byAbbreviation("Tob"))
    }
}
