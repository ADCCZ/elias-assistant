package io.github.adccz.elias.domain.speech

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Times and dates the way people say them in Czech.
 *
 * Quarter hours use the everyday 12-hour forms ("ve čtvrt na jedenáct", "v půl čtvrté odpoledne").
 * Other times use the 24-hour form ("v šestnáct dvacet"). Everything is rounded to five minutes,
 * because nobody says "v devět sedm" in a briefing.
 */
object SpokenTime {
    /** "v deset", "ve čtvrt na jedenáct", "v půl čtvrté odpoledne", "v šestnáct dvacet", "v poledne". */
    fun at(time: LocalTime): String {
        val rounded = roundToFiveMinutes(time)
        val hour = rounded.hour
        val minute = rounded.minute
        if (minute == 0 && hour == 12) return "v poledne"
        if (minute == 0 && hour == 0) return "o půlnoci"

        val thisHour = twelveHour(hour)
        val nextHour = thisHour % 12 + 1
        val phrase = when (minute) {
            0 -> withV(hourAccusative(thisHour))

            15 -> "ve čtvrt na ${hourAccusative(nextHour)}"

            30 -> "v půl ${hourGenitive(nextHour)}"

            45 -> "ve tři čtvrtě na ${hourAccusative(nextHour)}"

            else -> {
                val hourWord = if (hour == 1) "jednu" else CzechNumbers.cardinal(hour, Gender.FEMININE)
                return withV("$hourWord ${minutesWords(minute)}")
            }
        }
        return phrase + dayPartSuffix(hour)
    }

    /** "kolem třetí odpoledne", "kolem poledne": an approximate time, rounded to the hour. */
    fun around(time: LocalTime): String {
        val hour = if (time.minute >= 30) (time.hour + 1) % 24 else time.hour
        return when (hour) {
            12 -> "kolem poledne"
            0 -> "kolem půlnoci"
            else -> "kolem ${hourGenitive(twelveHour(hour))} ${dayPart(hour)}"
        }
    }

    private fun roundToFiveMinutes(time: LocalTime): LocalTime {
        val totalMinutes = time.hour * 60 + time.minute
        val rounded = ((totalMinutes + 2) / 5 * 5) % (24 * 60)
        return LocalTime.of(rounded / 60, rounded % 60)
    }

    private fun twelveHour(hour: Int): Int = if (hour % 12 == 0) 12 else hour % 12

    /** Morning hours need no label in a daily briefing. Later hours get one: "v půl čtvrté odpoledne". */
    private fun dayPartSuffix(hour: Int): String = when (hour) {
        in 13..17 -> " odpoledne"
        in 18..23 -> " večer"
        in 0..4 -> " v noci"
        else -> ""
    }

    private fun dayPart(hour: Int): String = when (hour) {
        in 5..8 -> "ráno"
        in 9..11 -> "dopoledne"
        in 12..17 -> "odpoledne"
        in 18..21 -> "večer"
        else -> "v noci"
    }

    // "v jednu", "ve dvě", ..., "ve dvanáct"
    private fun hourAccusative(hour: Int): String = when (hour) {
        1 -> "jednu"
        2 -> "dvě"
        else -> CzechNumbers.cardinal(hour)
    }

    // "půl jedné", "půl druhé", ..., "půl dvanácté"; also "kolem třetí"
    private fun hourGenitive(hour: Int): String = hoursGenitive[hour - 1]

    private val hoursGenitive = listOf(
        "jedné", "druhé", "třetí", "čtvrté", "páté", "šesté",
        "sedmé", "osmé", "deváté", "desáté", "jedenácté", "dvanácté",
    )

    // 5 → "nula pět", as in "v devět nula pět"
    private fun minutesWords(minute: Int): String =
        if (minute < 10) "nula ${CzechNumbers.cardinal(minute)}" else CzechNumbers.cardinal(minute, Gender.FEMININE)
}

/** "úterý sedmadvacátého října", "ve středu osmadvacátého října". */
object SpokenDate {
    /** Weekday, day and month: "úterý sedmadvacátého října". */
    fun dayAndDate(date: LocalDate): String = "${weekdayNominative(date.dayOfWeek)} ${dayAndMonth(date)}"

    /** With the preposition: "v úterý sedmadvacátého října", "ve středu osmadvacátého října". */
    fun onDay(date: LocalDate): String = "${weekdayWithPreposition(date.dayOfWeek)} ${dayAndMonth(date)}"

    /** "sedmadvacátého října". */
    fun dayAndMonth(date: LocalDate): String =
        "${dayGenitive[date.dayOfMonth - 1]} ${monthGenitive[date.monthValue - 1]}"

    fun weekdayNominative(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> "pondělí"
        DayOfWeek.TUESDAY -> "úterý"
        DayOfWeek.WEDNESDAY -> "středa"
        DayOfWeek.THURSDAY -> "čtvrtek"
        DayOfWeek.FRIDAY -> "pátek"
        DayOfWeek.SATURDAY -> "sobota"
        DayOfWeek.SUNDAY -> "neděle"
    }

    fun weekdayWithPreposition(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> "v pondělí"
        DayOfWeek.TUESDAY -> "v úterý"
        DayOfWeek.WEDNESDAY -> "ve středu"
        DayOfWeek.THURSDAY -> "ve čtvrtek"
        DayOfWeek.FRIDAY -> "v pátek"
        DayOfWeek.SATURDAY -> "v sobotu"
        DayOfWeek.SUNDAY -> "v neděli"
    }

    private val dayGenitive = listOf(
        "prvního", "druhého", "třetího", "čtvrtého", "pátého", "šestého", "sedmého", "osmého", "devátého", "desátého",
        "jedenáctého", "dvanáctého", "třináctého", "čtrnáctého", "patnáctého", "šestnáctého", "sedmnáctého",
        "osmnáctého", "devatenáctého", "dvacátého", "jednadvacátého", "dvaadvacátého", "třiadvacátého",
        "čtyřiadvacátého", "pětadvacátého", "šestadvacátého", "sedmadvacátého", "osmadvacátého", "devětadvacátého",
        "třicátého", "jednatřicátého",
    )

    private val monthGenitive = listOf(
        "ledna", "února", "března", "dubna", "května", "června",
        "července", "srpna", "září", "října", "listopadu", "prosince",
    )
}

/** Adds the preposition "v", or "ve" where Czech needs it for pronunciation ("ve dvě", "ve tři", "ve čtvrtek"). */
internal fun withV(phrase: String): String {
    val needsVe = listOf("dv", "tř", "čt", "st").any { phrase.startsWith(it) }
    return if (needsVe) "ve $phrase" else "v $phrase"
}
