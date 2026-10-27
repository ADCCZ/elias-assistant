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
    val events: List<EventFacts>?,
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

data class PlanFacts(
    val phase: String?,
    val dailyMinimum: List<String>,
    /** "Marek, první kapitola". */
    val morningReading: String?,
    val saturdayStudy: String?,
    val items: List<String>,
    val theme: ThemeFacts?,
)

data class ThemeFacts(val title: String, val task: String, val reflectionQuestion: String?)

data class DeadlineFacts(val subject: String?, val title: String, val mandatory: Boolean)
