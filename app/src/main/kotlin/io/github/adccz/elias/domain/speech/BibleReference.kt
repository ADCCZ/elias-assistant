package io.github.adccz.elias.domain.speech

/**
 * Turns Czech Bible references into words for listening:
 * "Mk 1" → "Marek, první kapitola", "Jk 1,19–27" → "List Jakubův, první kapitola, verše devatenáct až dvacet sedm",
 * "Ž 139" → "Žalm sto třicet devět". Several references joined by " a " are read one after another.
 *
 * Abbreviations follow the Czech ecumenical translation. An unknown reference is returned unchanged,
 * so a typo in the plan never breaks the briefing.
 */
object BibleReference {
    fun spoken(reference: String): String = reference
        .split(" a ")
        .joinToString(" a ") { spokenSingle(it.trim()) }

    private val pattern = Regex("""^((?:\d\s?)?\p{L}+)\s+(\d+)(?:,(\d+)(?:\s*[–-]\s*(\d+))?)?$""")

    private fun spokenSingle(reference: String): String {
        val match = pattern.matchEntire(reference) ?: return reference
        val (abbreviation, chapterText, firstVerseText, lastVerseText) = match.destructured
        val book = books[abbreviation.replace(Regex("""^(\d)(\p{L})"""), "$1 $2")] ?: return reference
        val chapter = chapterText.toInt()

        val chapterPart = if (book == PSALM) {
            "$book ${label(chapter)}"
        } else if (chapter in 1..99) {
            "$book, ${CzechNumbers.ordinalFeminine(chapter)} kapitola"
        } else {
            return reference
        }
        val versePart = when {
            firstVerseText.isEmpty() -> ""
            lastVerseText.isEmpty() -> ", verš ${label(firstVerseText.toInt())}"
            else -> ", verše ${label(firstVerseText.toInt())} až ${label(lastVerseText.toInt())}"
        }
        return chapterPart + versePart
    }

    /** Numbers used as labels are read in the counting form: "jedna, dva, tři" ("verš jedna", "Žalm dva"). */
    private fun label(number: Int): String = if (number == 1) "jedna" else CzechNumbers.cardinal(number)

    private const val PSALM = "Žalm"

    private val books = mapOf(
        // Old Testament (the books most likely in a reading plan)
        "Gn" to "Genesis", "Ex" to "Exodus", "Lv" to "Leviticus", "Nu" to "Numeri", "Dt" to "Deuteronomium",
        "Joz" to "Jozue", "Sd" to "Kniha Soudců", "Rt" to "Rút",
        "1 S" to "První kniha Samuelova", "2 S" to "Druhá kniha Samuelova",
        "1 Kr" to "První kniha královská", "2 Kr" to "Druhá kniha královská",
        "Neh" to "Nehemjáš", "Est" to "Ester", "Jb" to "Job", "Ž" to PSALM, "Př" to "Přísloví",
        "Kaz" to "Kazatel", "Pís" to "Píseň písní", "Iz" to "Izajáš", "Jr" to "Jeremjáš", "Pl" to "Pláč",
        "Ez" to "Ezechiel", "Da" to "Daniel", "Oz" to "Ozeáš", "Jl" to "Jóel", "Am" to "Ámos", "Jon" to "Jonáš",
        "Mi" to "Micheáš", "Ab" to "Abakuk", "Ag" to "Ageus", "Za" to "Zacharjáš", "Mal" to "Malachiáš",
        // New Testament
        "Mt" to "Matouš", "Mk" to "Marek", "L" to "Lukáš", "J" to "Jan", "Sk" to "Skutky apoštolů",
        "Ř" to "List Římanům", "1 K" to "První list Korintským", "2 K" to "Druhý list Korintským",
        "Ga" to "List Galatským", "Ef" to "List Efezským", "Fp" to "List Filipským", "Ko" to "List Koloským",
        "1 Te" to "První list Tesalonickým", "2 Te" to "Druhý list Tesalonickým",
        "1 Tm" to "První list Timoteovi", "2 Tm" to "Druhý list Timoteovi", "Tt" to "List Titovi",
        "Fm" to "List Filemonovi", "Žd" to "List Židům", "Jk" to "List Jakubův",
        "1 Pt" to "První list Petrův", "2 Pt" to "Druhý list Petrův",
        "1 J" to "První list Janův", "2 J" to "Druhý list Janův", "3 J" to "Třetí list Janův",
        "Ju" to "List Judův", "Zj" to "Zjevení Janovo",
    )
}
