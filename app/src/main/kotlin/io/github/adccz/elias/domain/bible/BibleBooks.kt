package io.github.adccz.elias.domain.bible

/**
 * One book of the Bible.
 *
 * [abbreviation] is the Czech ecumenical translation's (ČEP) abbreviation with a space after a leading number
 * ("1 K"). [name] is shown on the screen ("1. Korintským"), [spokenName] is said aloud ("První list Korintským").
 * [chapters] is the number of chapters in ČEP, which sometimes differs from English Bibles (Joel, Malachi).
 */
data class BibleBook(val abbreviation: String, val name: String, val spokenName: String, val chapters: Int) {
    val isPsalms: Boolean get() = abbreviation == PSALMS

    internal companion object {
        const val PSALMS = "Ž"
    }
}

/** One chapter of one book. */
data class Chapter(val book: BibleBook, val number: Int) {
    init {
        require(number in 1..book.chapters) { "${book.abbreviation} has no chapter $number" }
    }

    /** As written on the screen: "Marek 3", "Žalm 23", "Judův" (a book with one chapter). */
    val label: String
        get() = when {
            book.isPsalms -> "Žalm $number"
            book.chapters == 1 -> book.name
            else -> "${book.name} $number"
        }
}

/**
 * The 66 books of the Protestant canon in the order of ČEP, with its abbreviations and chapter counts.
 *
 * The chapter counts were checked on 2026-10-29 against the ČEP text on obohu.cz: the last chapter of every book has
 * verses and the chapter after it has none. ČEP follows the Hebrew numbering, so Joel has four chapters and Malachi
 * three (1189 chapters in total).
 */
object BibleBooks {
    val all: List<BibleBook> = listOf(
        // Old Testament
        BibleBook("Gn", "Genesis", "Genesis", 50),
        BibleBook("Ex", "Exodus", "Exodus", 40),
        BibleBook("Lv", "Leviticus", "Leviticus", 27),
        BibleBook("Nu", "Numeri", "Numeri", 36),
        BibleBook("Dt", "Deuteronomium", "Deuteronomium", 34),
        BibleBook("Joz", "Jozue", "Jozue", 24),
        BibleBook("Sd", "Soudců", "Kniha Soudců", 21),
        BibleBook("Rt", "Rút", "Rút", 4),
        BibleBook("1 S", "1. Samuelova", "První kniha Samuelova", 31),
        BibleBook("2 S", "2. Samuelova", "Druhá kniha Samuelova", 24),
        BibleBook("1 Kr", "1. Královská", "První kniha královská", 22),
        BibleBook("2 Kr", "2. Královská", "Druhá kniha královská", 25),
        BibleBook("1 Pa", "1. Paralipomenon", "První kniha Paralipomenon", 29),
        BibleBook("2 Pa", "2. Paralipomenon", "Druhá kniha Paralipomenon", 36),
        BibleBook("Ezd", "Ezdráš", "Ezdráš", 10),
        BibleBook("Neh", "Nehemjáš", "Nehemjáš", 13),
        BibleBook("Est", "Ester", "Ester", 10),
        BibleBook("Jb", "Jób", "Jób", 42),
        BibleBook(BibleBook.PSALMS, "Žalmy", "Žalm", 150),
        BibleBook("Př", "Přísloví", "Přísloví", 31),
        BibleBook("Kaz", "Kazatel", "Kazatel", 12),
        BibleBook("Pís", "Píseň písní", "Píseň písní", 8),
        BibleBook("Iz", "Izajáš", "Izajáš", 66),
        BibleBook("Jr", "Jeremjáš", "Jeremjáš", 52),
        BibleBook("Pl", "Pláč", "Pláč", 5),
        BibleBook("Ez", "Ezechiel", "Ezechiel", 48),
        BibleBook("Da", "Daniel", "Daniel", 12),
        BibleBook("Oz", "Ozeáš", "Ozeáš", 14),
        BibleBook("Jl", "Jóel", "Jóel", 4),
        BibleBook("Am", "Ámos", "Ámos", 9),
        BibleBook("Abd", "Abdijáš", "Abdijáš", 1),
        BibleBook("Jon", "Jonáš", "Jonáš", 4),
        BibleBook("Mi", "Micheáš", "Micheáš", 7),
        BibleBook("Na", "Nahum", "Nahum", 3),
        BibleBook("Abk", "Abakuk", "Abakuk", 3),
        BibleBook("Sf", "Sofonjáš", "Sofonjáš", 3),
        BibleBook("Ag", "Ageus", "Ageus", 2),
        BibleBook("Za", "Zacharjáš", "Zacharjáš", 14),
        BibleBook("Mal", "Malachiáš", "Malachiáš", 3),
        // New Testament
        BibleBook("Mt", "Matouš", "Matouš", 28),
        BibleBook("Mk", "Marek", "Marek", 16),
        BibleBook("L", "Lukáš", "Lukáš", 24),
        BibleBook("J", "Jan", "Jan", 21),
        BibleBook("Sk", "Skutky apoštolů", "Skutky apoštolů", 28),
        BibleBook("Ř", "Římanům", "List Římanům", 16),
        BibleBook("1 K", "1. Korintským", "První list Korintským", 16),
        BibleBook("2 K", "2. Korintským", "Druhý list Korintským", 13),
        BibleBook("Ga", "Galatským", "List Galatským", 6),
        BibleBook("Ef", "Efezským", "List Efezským", 6),
        BibleBook("Fp", "Filipským", "List Filipským", 4),
        BibleBook("Ko", "Koloským", "List Koloským", 4),
        BibleBook("1 Te", "1. Tesalonickým", "První list Tesalonickým", 5),
        BibleBook("2 Te", "2. Tesalonickým", "Druhý list Tesalonickým", 3),
        BibleBook("1 Tm", "1. Timoteovi", "První list Timoteovi", 6),
        BibleBook("2 Tm", "2. Timoteovi", "Druhý list Timoteovi", 4),
        BibleBook("Tt", "Titovi", "List Titovi", 3),
        BibleBook("Fm", "Filemonovi", "List Filemonovi", 1),
        BibleBook("Žd", "Židům", "List Židům", 13),
        BibleBook("Jk", "Jakubův", "List Jakubův", 5),
        BibleBook("1 Pt", "1. Petrův", "První list Petrův", 5),
        BibleBook("2 Pt", "2. Petrův", "Druhý list Petrův", 3),
        BibleBook("1 J", "1. Janův", "První list Janův", 5),
        BibleBook("2 J", "2. Janův", "Druhý list Janův", 1),
        BibleBook("3 J", "3. Janův", "Třetí list Janův", 1),
        BibleBook("Ju", "Judův", "List Judův", 1),
        BibleBook("Zj", "Zjevení Janovo", "Zjevení Janovo", 22),
    )

    private val byAbbreviation: Map<String, BibleBook> = all.associateBy { it.abbreviation } +
        // Older plans wrote Habakkuk as "Ab".
        mapOf("Ab" to all.single { it.abbreviation == "Abk" })

    /** The book with this abbreviation. "1K" is read like "1 K". Null for an unknown abbreviation. */
    fun byAbbreviation(abbreviation: String): BibleBook? =
        byAbbreviation[abbreviation.trim().replace(leadingNumber, "$1 $2")]

    private val leadingNumber = Regex("""^(\d)\s*(\p{L})""")
}
