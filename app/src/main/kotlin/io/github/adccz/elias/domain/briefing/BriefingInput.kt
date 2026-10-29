package io.github.adccz.elias.domain.briefing

import java.time.LocalDate

/**
 * Everything a briefing may say, already turned into spoken Czech where grammar matters
 * (times, dates, temperatures, Bible references). Both the template and the AI get exactly this,
 * so neither has to invent numbers or forms.
 *
 * A `null` source means it could not be loaded; an empty list means there is nothing to say.
 */
data class MorningInput(
    val date: LocalDate,
    /** "úterý sedmadvacátého října". */
    val dateSpoken: String,
    val weather: WeatherFacts?,
    /** Events from calendars that are not topics. Null when the calendar could not be read. */
    val events: List<EventFacts>?,
    /** Today's plans of each topic that has some (M4.5). */
    val topicPlans: List<TopicPlanFacts>,
    /** Null when the plan could not be loaded or is not valid today. */
    val plan: PlanFacts?,
    val planUnavailable: Boolean,
    val headlines: List<String>,
    val headlinesUnavailable: Boolean,
    val deadlinesToday: List<DeadlineFacts>,
    val deadlinesTomorrow: List<DeadlineFacts>,
)

data class EveningInput(
    val date: LocalDate,
    /** "středa osmadvacátého října". */
    val tomorrowSpoken: String,
    val tomorrowEvents: List<EventFacts>?,
    val tomorrowTopicPlans: List<TopicPlanFacts>,
    /** Names of the topics with nothing planned for tomorrow. Empty without topics or a calendar. */
    val topicsWithoutPlansTomorrow: List<String>,
    val deadlinesTomorrow: List<DeadlineFacts>,
    val theme: ThemeFacts?,
)

data class WeatherFacts(
    /** "tři stupně". */
    val morningTemperature: String?,
    /** "čtrnáct stupňů". */
    val afternoonTemperature: String?,
    /** "skoro jasno". */
    val sky: String?,
    /** "slabý déšť", null when dry. */
    val precipitation: String?,
    /** "kolem třetí odpoledne". */
    val precipitationFrom: String?,
    /** "sedmdesát procent". */
    val precipitationProbability: String?,
    /** "padesát kilometrů za hodinu", only when the wind is strong enough to mention. */
    val strongWindGusts: String?,
)

data class EventFacts(
    val title: String,
    /** "celý den", "ve čtvrt na deset", "v půl čtvrté odpoledne". */
    val time: String,
    val location: String?,
)

/** The plans of one topic on one day: "Škola" with "připravit referát". */
data class TopicPlanFacts(val topic: String, val plans: List<PlanItemFacts>)

/** One plan. [time] is spoken ("v půl třetí odpoledne"), null for a plan without a time (an all-day event). */
data class PlanItemFacts(val title: String, val time: String?)

/** Today's part of the plan. The morning reading is not here: it belongs to the routine ([RoutineFacts]). */
data class PlanFacts(
    val phase: String?,
    val dailyMinimum: List<String>,
    val saturdayStudy: String?,
    val items: List<String>,
    val theme: ThemeFacts?,
)

data class ThemeFacts(val title: String, val task: String, val reflectionQuestion: String?)

data class DeadlineFacts(val subject: String?, val title: String, val mandatory: Boolean)

/** What the morning says about the Bible reading (M5). */
sealed interface ReadingFact {
    /** "Marek, třetí kapitola": the next chapter of the queue, or the plan's reading when the queue is not used. */
    data class Next(val spoken: String) : ReadingFact

    /** Something was marked as read today. */
    data object DoneToday : ReadingFact

    /** The queue was read to its end. */
    data object Finished : ReadingFact
}

/**
 * The morning routine (M5), spoken by the template after the AI answer, so the names stay exact and the reading is
 * said also after the plan has expired. [reading] is null when nothing is to be said about it.
 */
data class RoutineFacts(val reading: ReadingFact?, val exercises: List<String>, val recipe: String?)
