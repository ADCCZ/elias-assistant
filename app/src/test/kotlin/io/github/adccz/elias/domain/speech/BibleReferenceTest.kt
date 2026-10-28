package io.github.adccz.elias.domain.speech

import org.junit.jupiter.api.Assertions.assertEquals
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
        ],
    )
    fun `reads references aloud`(reference: String, expected: String) {
        assertEquals(expected, BibleReference.spoken(reference))
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = ["Xy 3", "Číst cokoli", "Mk"])
    fun `leaves unknown text unchanged`(text: String) {
        assertEquals(text, BibleReference.spoken(text))
    }
}
