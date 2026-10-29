package io.github.adccz.elias.domain.briefing

import io.github.adccz.elias.domain.journal.JournalReminder
import io.github.adccz.elias.domain.speech.CzechNumbers
import io.github.adccz.elias.domain.speech.Gender
import javax.inject.Inject

/**
 * Writes the briefings from fixed Czech sentences, without any network.
 *
 * This is what Elias says when the AI is off, has no key or fails, so it must always work and must never
 * invent anything: every sentence comes from the input or says plainly that a source is missing.
 */
class TemplateBriefingBuilder @Inject constructor() {
    /**
     * The morning briefing. [reservedWords] are said besides it (the morning routine, added after it), so the
     * shortening keeps the whole morning within the target length.
     */
    fun morning(input: MorningInput, reservedWords: Int = 0): BriefingText {
        // Try the full version first, then drop the least important details until it fits the target length.
        val budgets = listOf(
            Budget(events = 4, headlines = 3, withMinimum = true, plansPerTopic = 3),
            Budget(events = 4, headlines = 2, withMinimum = true, plansPerTopic = 3),
            Budget(events = 3, headlines = 2, withMinimum = false, plansPerTopic = 2),
            Budget(events = 2, headlines = 1, withMinimum = false, plansPerTopic = 1),
        )
        val versions = budgets.asSequence().map { morningWith(input, it) }
        return versions.firstOrNull { it.wordCount + reservedWords <= BriefingLength.MORNING.last } ?: versions.last()
    }

    fun evening(input: EveningInput): BriefingText = BriefingText(
        BriefingKind.EVENING,
        listOf(
            BriefingSection(
                SectionKind.CHECKLIST,
                "Dobrý večer, Oldo. Než den skončí, odškrtni dnešek v checklistu, zapiš dnešní výdaje " +
                    "a vyber zítřejší hlavní úkol.",
            ),
            BriefingSection(SectionKind.TOMORROW, tomorrow(input)),
            BriefingSection(SectionKind.REFLECTION, reflection(input.theme)),
            BriefingSection(SectionKind.GOOD_NIGHT, "Teď dej telefon pryč a do půlnoci spát. Dobrou noc, Oldo."),
        ),
    )

    /**
     * The opening of Saturday's evening briefing (spec: "sabat skončil"): a question about the sabbath study and a
     * reminder that tomorrow is for reviewing goals and planning the week. [study] is today's study, already spoken.
     */
    fun sabbathEnded(study: String?): BriefingSection {
        val question = study?.let { "Dnešní studium: $it. Co si z něj odnášíš?" }
            ?: "Co si odnášíš ze sobotního studia?"
        return BriefingSection(
            SectionKind.SABBATH_ENDED,
            "Dobrý večer, Oldo. Sabat skončil. $question Zítra tě čeká revize cílů a plán na další týden.",
        )
    }

    /**
     * Yesterday's step from the journal (spec, M4.5). A long text is not read out, the journal has it.
     * It is said on the phone only: the journal never goes to the AI that writes briefings.
     */
    fun journalStep(reminder: JournalReminder): BriefingSection {
        val text = reminder.text.replace(whitespace, " ").trim()
        val spoken = when {
            CzechNumbers.wordCount(text) > MAX_SPOKEN_JOURNAL_WORDS -> if (reminder.isStep) {
                "Včera sis do deníku zapsal krok na dnešek. Najdeš ho v aplikaci."
            } else {
                "Včera sis do deníku zapsal, na čem se chceš zlepšit. Najdeš to v aplikaci."
            }

            reminder.isStep -> "Včera sis dal za krok: ${sentence(text)}"

            else -> "Včera sis zapsal, na čem se chceš zlepšit: ${sentence(text)}"
        }
        return BriefingSection(SectionKind.JOURNAL_STEP, spoken)
    }

    /**
     * The morning routine (spec, M5): the reading, today's exercises and the recipe. It is said on the phone after the
     * AI answer, like the journal step, so the names stay exact.
     */
    fun routine(facts: RoutineFacts): BriefingSection {
        val sentences = mutableListOf<String>()
        when (val reading = facts.reading) {
            is ReadingFact.Next -> sentences += "Ranní čtení: ${reading.spoken}."
            ReadingFact.DoneToday -> sentences += "Dnešní čtení máš hotové."
            ReadingFact.Finished -> sentences += "Pořadník čtení je dočtený, další knihy si vyber v aplikaci."
            null -> Unit
        }
        if (facts.exercises.isNotEmpty()) {
            sentences += "Cviky na dnešek: ${joinCzech(facts.exercises.map { lowerFirst(withoutEndPunctuation(it)) })}."
        }
        facts.recipe?.let { sentences += "Recept dne: ${lowerFirst(withoutEndPunctuation(it))}." }
        return BriefingSection(SectionKind.ROUTINE, sentences.joinToString(" "))
    }

    /** The evening reminder of today's chapter (spec, M5). [chapter] is spoken: "Marek, třetí kapitola". */
    fun readingReminder(chapter: String): BriefingSection =
        BriefingSection(SectionKind.READING_REMINDER, "Ještě tě čeká dnešní čtení: $chapter.")

    /** The evening reminder when today's journal entry is missing (spec, M4.5). */
    fun journalReminder(): BriefingSection = BriefingSection(
        SectionKind.JOURNAL_REMINDER,
        "Dnešní zápis v deníku ti ještě chybí. Zapiš si myšlenku dne, co ti utkvělo a na čem se chceš zlepšit.",
    )

    private data class Budget(val events: Int, val headlines: Int, val withMinimum: Boolean, val plansPerTopic: Int)

    private fun morningWith(input: MorningInput, budget: Budget) = BriefingText(
        BriefingKind.MORNING,
        listOf(
            BriefingSection(SectionKind.GREETING_AND_WEATHER, greetingAndWeather(input)),
            BriefingSection(SectionKind.CALENDAR, todayCalendar(input, budget)),
            BriefingSection(SectionKind.PLAN, plan(input, budget.withMinimum)),
            BriefingSection(SectionKind.NEWS, news(input, budget.headlines)),
            BriefingSection(SectionKind.WARNINGS, warnings(input)),
            BriefingSection(SectionKind.CLOSING, "To je pro dnešek všechno. Hezký den."),
        ),
    )

    private fun greetingAndWeather(input: MorningInput): String {
        val sentences = mutableListOf("Dobré ráno, Oldo. Dnes je ${input.dateSpoken}.")
        val weather = input.weather
        if (weather == null) {
            sentences += "Počasí se mi nepodařilo zjistit."
            return sentences.joinToString(" ")
        }

        val temperatures = listOfNotNull(
            weather.morningTemperature?.let { "ráno $it" },
            weather.afternoonTemperature?.let { "odpoledne $it" },
        )
        val overview = (temperatures + listOfNotNull(weather.sky)).joinToString(", ")
        if (overview.isNotEmpty()) sentences += "V Plzni bude $overview."

        sentences += when {
            weather.precipitation == null -> "Srážky nehlásí."

            else -> {
                val time = weather.precipitationFrom?.let(::upperFirst) ?: "Během dne"
                val probability = weather.precipitationProbability?.let { ", s pravděpodobností $it" }.orEmpty()
                "$time hlásí ${weather.precipitation}$probability."
            }
        }
        weather.strongWindGusts?.let { sentences += "Pozor na vítr, v nárazech až $it." }
        return sentences.joinToString(" ")
    }

    private fun todayCalendar(input: MorningInput, budget: Budget): String {
        val events = input.events ?: return "Kalendář se mi nepodařilo přečíst."
        if (events.isEmpty() && input.topicPlans.isEmpty()) return "V kalendáři dnes nic nemáš."
        val sentences = mutableListOf<String>()
        if (events.isNotEmpty()) {
            sentences += "V kalendáři máš dnes ${countEvents(events.size)}. " + eventList(events, budget.events)
        }
        if (input.topicPlans.isNotEmpty()) {
            sentences += "Plány na dnešek. " + topicPlans(input.topicPlans, budget.plansPerTopic)
        }
        return sentences.joinToString(" ")
    }

    private fun tomorrow(input: EveningInput): String {
        val sentences = mutableListOf("Zítra je ${input.tomorrowSpoken}.")
        val events = input.tomorrowEvents
        sentences += when {
            events == null -> "Kalendář na zítřek se mi nepodařilo přečíst."
            events.isEmpty() && input.tomorrowTopicPlans.isEmpty() -> "V kalendáři nic nemáš."
            events.isEmpty() -> ""
            else -> "V kalendáři máš ${countEvents(events.size)}. " + eventList(events, maxEvents = 3)
        }
        if (input.tomorrowTopicPlans.isNotEmpty()) {
            sentences += "Plány na zítřek. " + topicPlans(input.tomorrowTopicPlans, maxPlans = 2)
        }
        if (input.topicsWithoutPlansTomorrow.isNotEmpty()) {
            sentences += "Bez plánu na zítřek: ${joinCzech(input.topicsWithoutPlansTomorrow)}."
        }
        input.deadlinesTomorrow.forEach { sentences += "Máš ${deadlineLabel(it)}: ${describe(it)}." }
        return sentences.filter { it.isNotEmpty() }.joinToString(" ")
    }

    /** "Škola: připravit referát a v půl třetí odpoledne přečíst kapitolu. Španělština: lekce pět." */
    private fun topicPlans(topics: List<TopicPlanFacts>, maxPlans: Int): String {
        var shortened = false
        val spoken = topics.map { topic ->
            if (topic.plans.size > maxPlans) shortened = true
            val plans = topic.plans.take(maxPlans).map { plan ->
                val title = lowerFirst(withoutEndPunctuation(plan.title))
                plan.time?.let { "$it $title" } ?: title
            }
            "${topic.topic}: ${joinCzech(plans)}."
        }
        val rest = if (shortened) listOf("Další plány najdeš v aplikaci.") else emptyList()
        return (spoken + rest).joinToString(" ")
    }

    private fun eventList(events: List<EventFacts>, maxEvents: Int): String {
        val spoken = events.take(maxEvents).map { event ->
            val place = event.location?.let { ", $it" }.orEmpty()
            sentence("${upperFirst(event.time)}: ${event.title}$place")
        }
        val rest = if (events.size > maxEvents) listOf("Zbytek najdeš v kalendáři.") else emptyList()
        return (spoken + rest).joinToString(" ")
    }

    private fun plan(input: MorningInput, withMinimum: Boolean): String {
        if (input.planUnavailable) return "Plán se mi nepodařilo načíst."
        val plan = input.plan ?: return "" // the plan is not valid today: nothing to say
        val sentences = mutableListOf<String>()
        plan.phase?.let { sentences += "Jsi ve fázi $it." }
        if (withMinimum && plan.dailyMinimum.isNotEmpty()) {
            sentences += "Minimum dne: ${joinCzech(plan.dailyMinimum.map(::lowerFirst))}."
        }
        plan.saturdayStudy?.let { sentences += "Sobotní studium: $it." }
        if (plan.items.isNotEmpty()) sentences += "Na programu je ${joinCzech(plan.items.map(::lowerFirst))}."
        plan.theme?.let {
            sentences +=
                "Téma týdne je ${lowerFirst(it.title)}: ${lowerFirst(withoutEndPunctuation(it.task))}."
        }
        return sentences.joinToString(" ")
    }

    private fun news(input: MorningInput, maxHeadlines: Int): String = when {
        input.headlines.isNotEmpty() ->
            "Ze světa: " + input.headlines.take(maxHeadlines).joinToString(" ") { sentence(it) }

        input.headlinesUnavailable -> "Titulky se mi nepodařilo načíst."

        else -> ""
    }

    private fun warnings(input: MorningInput): String {
        val today = input.deadlinesToday.map { "Dnes máš ${deadlineLabel(it)}: ${describe(it)}." }
        val tomorrow = input.deadlinesTomorrow.map { "Zítra máš ${deadlineLabel(it)}: ${describe(it)}." }
        return (today + tomorrow).joinToString(" ")
    }

    private fun reflection(theme: ThemeFacts?): String {
        if (theme == null) return ""
        theme.reflectionQuestion?.let { return sentence(it, ending = '?') }
        return "Téma týdne je ${lowerFirst(theme.title)}. " +
            "Jak se ti dnes dařilo ${lowerFirst(withoutEndPunctuation(theme.task))}?"
    }

    // After "máš" Czech needs the accusative: "máš jednu událost" (2+ look the same as the nominative).
    private fun countEvents(count: Int) = if (count ==
        1
    ) {
        "jednu událost"
    } else {
        CzechNumbers.count(count, "událost", "události", "událostí", Gender.FEMININE)
    }

    private fun deadlineLabel(deadline: DeadlineFacts) = if (deadline.mandatory) "povinný termín" else "termín"

    private fun describe(deadline: DeadlineFacts): String {
        val title = withoutEndPunctuation(deadline.title)
        return deadline.subject?.let { "$it, ${lowerFirst(title)}" } ?: title
    }

    private companion object {
        /** About ten seconds of speech. A longer journal text would crowd out the rest of the morning. */
        const val MAX_SPOKEN_JOURNAL_WORDS = 30

        val whitespace = Regex("""\s+""")
    }
}

/** "a, b a c". */
internal fun joinCzech(items: List<String>): String = when (items.size) {
    0 -> ""
    1 -> items[0]
    else -> items.dropLast(1).joinToString(", ") + " a " + items.last()
}

/** Lowercases the first letter, but keeps acronyms and names written in capitals ("SQL", "UP-105"). */
internal fun lowerFirst(text: String): String {
    val looksLikeAcronym = text.length > 1 && text[1].isUpperCase()
    return if (looksLikeAcronym) text else text.replaceFirstChar { it.lowercase() }
}

internal fun upperFirst(text: String): String = text.replaceFirstChar { it.uppercase() }

internal fun withoutEndPunctuation(text: String): String = text.trim().trimEnd('.', '!', '?', ',', ';', ':')

/** Makes [text] one sentence: trimmed, with exactly one closing mark. */
internal fun sentence(text: String, ending: Char = '.'): String {
    val trimmed = text.trim()
    return if (trimmed.lastOrNull() in listOf('.', '!', '?')) trimmed else withoutEndPunctuation(trimmed) + ending
}
