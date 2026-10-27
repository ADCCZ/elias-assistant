package io.github.adccz.elias.domain.speech

/** Grammatical gender of the counted noun. In Czech it changes "jeden/jedna/jedno" and "dva/dvě". */
enum class Gender { MASCULINE, FEMININE, NEUTER }

/**
 * Czech numbers written out for listening.
 *
 * Text-to-speech can read digits, but it does not know Czech grammar: "2 události" needs "dvě",
 * "21 °C" sounds best as "jednadvacet stupňů". These functions produce the words Elias says.
 */
object CzechNumbers {
    private val units = listOf("nula", "jeden", "dva", "tři", "čtyři", "pět", "šest", "sedm", "osm", "devět")
    private val teens = listOf(
        "deset", "jedenáct", "dvanáct", "třináct", "čtrnáct",
        "patnáct", "šestnáct", "sedmnáct", "osmnáct", "devatenáct",
    )
    private val tens =
        listOf("", "", "dvacet", "třicet", "čtyřicet", "padesát", "šedesát", "sedmdesát", "osmdesát", "devadesát")
    private val hundreds = listOf(
        "", "sto", "dvě stě", "tři sta", "čtyři sta", "pět set", "šest set", "sedm set", "osm set", "devět set",
    )

    /**
     * A cardinal number from −999 to 999 in the usual order: "dvacet pět", "sto třicet devět".
     * [gender] decides the form of a final 1 or 2 ("jedna kapitola", "dvě události").
     */
    fun cardinal(number: Int, gender: Gender = Gender.MASCULINE): String {
        require(number in -999..999) { "Only numbers from -999 to 999 are supported, got $number" }
        if (number < 0) return "minus " + cardinal(-number, gender)
        if (number == 0) return units[0]

        val parts = mutableListOf<String>()
        val rest = number % 100
        if (number >= 100) parts += hundreds[number / 100]
        when {
            rest == 0 -> Unit

            rest < 10 -> parts += unitWord(rest, gender, inCompound = number >= 100)

            rest < 20 -> parts += teens[rest - 10]

            else -> {
                parts += tens[rest / 10]
                if (rest % 10 != 0) parts += unitWord(rest % 10, gender, inCompound = true)
            }
        }
        return parts.joinToString(" ")
    }

    /**
     * Counts a noun with the right Czech form: [one] for 1, [few] for 2–4, [many] for 0 and 5+.
     * Compound numbers from 21 to 99 use the one-word form ("pětadvacet stupňů"), which always takes [many].
     *
     * Examples: `count(3, "stupeň", "stupně", "stupňů")` → "tři stupně", `count(-1, ...)` → "minus jeden stupeň".
     */
    fun count(number: Int, one: String, few: String, many: String, gender: Gender = Gender.MASCULINE): String {
        if (number < 0) return "minus " + count(-number, one, few, many, gender)
        return when {
            number == 1 -> "${unitWord(1, gender, inCompound = false)} $one"
            number in 2..4 -> "${unitWord(number, gender, inCompound = false)} $few"
            number in 21..99 && number % 10 != 0 -> "${invertedCompound(number)} $many"
            else -> "${cardinal(number, gender)} $many"
        }
    }

    /** "jednadvacet", "pětadvacet": the one-word form of 21–99, used before nouns. */
    private fun invertedCompound(number: Int): String {
        // "jedna" already ends in "a": jednadvacet, but dvaadvacet, pětadvacet
        val prefix = if (number % 10 == 1) "jedna" else "${units[number % 10]}a"
        return prefix + tens[number / 10]
    }

    private fun unitWord(digit: Int, gender: Gender, inCompound: Boolean): String = when (digit) {
        1 -> when {
            inCompound -> "jedna"

            // "dvacet jedna", the common spoken form
            gender == Gender.MASCULINE -> "jeden"

            gender == Gender.FEMININE -> "jedna"

            else -> "jedno"
        }

        2 -> if (gender == Gender.MASCULINE) "dva" else "dvě"

        else -> units[digit]
    }

    /** Nominative feminine ordinal 1–99: "první", "dvacátá pátá" (for "první kapitola"). */
    fun ordinalFeminine(number: Int): String {
        require(number in 1..99) { "Only ordinals from 1 to 99 are supported, got $number" }
        if (number < 20) return feminineOrdinalsTo19[number - 1]
        val tensWord = feminineOrdinalTens[number / 10]
        return if (number % 10 == 0) tensWord else "$tensWord ${feminineOrdinalsTo19[number % 10 - 1]}"
    }

    private val feminineOrdinalsTo19 = listOf(
        "první", "druhá", "třetí", "čtvrtá", "pátá", "šestá", "sedmá", "osmá", "devátá", "desátá",
        "jedenáctá", "dvanáctá", "třináctá", "čtrnáctá", "patnáctá", "šestnáctá", "sedmnáctá", "osmnáctá",
        "devatenáctá",
    )
    private val feminineOrdinalTens = listOf(
        "", "", "dvacátá", "třicátá", "čtyřicátá", "padesátá", "šedesátá", "sedmdesátá", "osmdesátá", "devadesátá",
    )

    /** Words in a text, for checking the length of a briefing. */
    fun wordCount(text: String): Int = text.split(Regex("\\s+")).count { word -> word.any { it.isLetterOrDigit() } }
}
