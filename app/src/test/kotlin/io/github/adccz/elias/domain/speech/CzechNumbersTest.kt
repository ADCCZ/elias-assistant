package io.github.adccz.elias.domain.speech

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class CzechNumbersTest {
    @ParameterizedTest(name = "{0} ({1}) = {2}")
    @CsvSource(
        "0, MASCULINE, nula",
        "1, MASCULINE, jeden",
        "1, FEMININE, jedna",
        "1, NEUTER, jedno",
        "2, MASCULINE, dva",
        "2, FEMININE, dvě",
        "12, MASCULINE, dvanáct",
        "20, MASCULINE, dvacet",
        "21, MASCULINE, dvacet jedna",
        "25, MASCULINE, dvacet pět",
        "100, MASCULINE, sto",
        "139, MASCULINE, sto třicet devět",
        "200, MASCULINE, dvě stě",
        "-3, MASCULINE, minus tři",
    )
    fun `cardinal numbers`(number: Int, gender: Gender, expected: String) {
        assertEquals(expected, CzechNumbers.cardinal(number, gender))
    }

    @ParameterizedTest(name = "{0} °C = {1}")
    @CsvSource(
        "1, jeden stupeň",
        "3, tři stupně",
        "4, čtyři stupně",
        "5, pět stupňů",
        "0, nula stupňů",
        "12, dvanáct stupňů",
        "21, jednadvacet stupňů",
        "25, pětadvacet stupňů",
        "30, třicet stupňů",
        "-1, minus jeden stupeň",
        "-2, minus dva stupně",
        "-7, minus sedm stupňů",
    )
    fun `temperatures use the right noun form`(degrees: Int, expected: String) {
        assertEquals(expected, CzechNumbers.count(degrees, "stupeň", "stupně", "stupňů"))
    }

    @Test
    fun `feminine and neuter nouns change one and two`() {
        assertEquals("jedna událost", CzechNumbers.count(1, "událost", "události", "událostí", Gender.FEMININE))
        assertEquals("dvě události", CzechNumbers.count(2, "událost", "události", "událostí", Gender.FEMININE))
        assertEquals("pět událostí", CzechNumbers.count(5, "událost", "události", "událostí", Gender.FEMININE))
        assertEquals("jedno procento", CzechNumbers.count(1, "procento", "procenta", "procent", Gender.NEUTER))
        assertEquals("dvě procenta", CzechNumbers.count(2, "procento", "procenta", "procent", Gender.NEUTER))
        assertEquals("sedmdesát procent", CzechNumbers.count(70, "procento", "procenta", "procent", Gender.NEUTER))
    }

    @ParameterizedTest(name = "{0}. = {1}")
    @CsvSource("1, první", "2, druhá", "13, třináctá", "16, šestnáctá", "20, dvacátá", "25, dvacátá pátá")
    fun `feminine ordinals`(number: Int, expected: String) {
        assertEquals(expected, CzechNumbers.ordinalFeminine(number))
    }

    @Test
    fun `counts words but not punctuation`() {
        assertEquals(5, CzechNumbers.wordCount("Dobré ráno, Oldo. Dnes – úterý!"))
    }
}
