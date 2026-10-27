package io.github.adccz.elias.domain.speech

import java.time.LocalDate
import java.time.LocalTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class SpokenTimeTest {
    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource(
        "07:00, v sedm",
        "10:00, v deset",
        "10:15, ve čtvrt na jedenáct",
        "10:30, v půl jedenácté",
        "10:45, ve tři čtvrtě na jedenáct",
        "11:45, ve tři čtvrtě na dvanáct",
        "12:00, v poledne",
        "12:30, v půl jedné",
        "13:00, v jednu odpoledne",
        "14:00, ve dvě odpoledne",
        "15:30, v půl čtvrté odpoledne",
        "20:00, v osm večer",
        "16:20, v šestnáct dvacet",
        "01:20, v jednu dvacet",
        "09:07, v devět nula pět", // rounded to five minutes
        "09:58, v deset",
        "03:00, ve tři v noci",
        "00:15, ve čtvrt na jednu v noci",
        "23:59, o půlnoci",
    )
    fun `times at a moment`(time: LocalTime, expected: String) {
        assertEquals(expected, SpokenTime.at(time))
    }

    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource(
        "07:00, kolem sedmé ráno",
        "10:00, kolem desáté dopoledne",
        "12:10, kolem poledne",
        "14:40, kolem třetí odpoledne",
        "15:00, kolem třetí odpoledne",
        "19:00, kolem sedmé večer",
        "23:40, kolem půlnoci",
    )
    fun `approximate times`(time: LocalTime, expected: String) {
        assertEquals(expected, SpokenTime.around(time))
    }

    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource(
        "2026-10-27, úterý sedmadvacátého října",
        "2026-11-01, neděle prvního listopadu",
        "2026-12-31, čtvrtek jednatřicátého prosince",
        "2026-03-02, pondělí druhého března",
    )
    fun `dates in the nominative`(date: LocalDate, expected: String) {
        assertEquals(expected, SpokenDate.dayAndDate(date))
    }

    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource(
        "2026-10-28, ve středu osmadvacátého října",
        "2026-10-29, ve čtvrtek devětadvacátého října",
        "2026-10-31, v sobotu jednatřicátého října",
        "2026-11-24, v úterý čtyřiadvacátého listopadu",
    )
    fun `dates with a preposition`(date: LocalDate, expected: String) {
        assertEquals(expected, SpokenDate.onDay(date))
    }
}
