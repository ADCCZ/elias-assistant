package io.github.adccz.elias.data.ai

import io.github.adccz.elias.domain.briefing.BriefingKind
import io.github.adccz.elias.domain.briefing.BriefingSection
import io.github.adccz.elias.domain.briefing.BriefingText
import io.github.adccz.elias.domain.briefing.DeadlineFacts
import io.github.adccz.elias.domain.briefing.EveningInput
import io.github.adccz.elias.domain.briefing.EventFacts
import io.github.adccz.elias.domain.briefing.MorningInput
import io.github.adccz.elias.domain.briefing.SectionKind
import io.github.adccz.elias.domain.briefing.ThemeFacts
import io.github.adccz.elias.domain.briefing.TopicPlanFacts
import io.github.adccz.elias.domain.briefing.WeatherFacts
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

// The JSON the model receives (input) and must return (output). Kept apart from the domain models,
// so the wire format can change without touching the rest of the app.

@Serializable
internal data class MorningData(
    val date: String,
    val weather: WeatherData?,
    @SerialName("weather_unavailable") val weatherUnavailable: Boolean,
    val events: List<EventData>?,
    @SerialName("topic_plans") val topicPlans: List<TopicPlanData>,
    @SerialName("calendar_unavailable") val calendarUnavailable: Boolean,
    val plan: PlanData?,
    @SerialName("plan_unavailable") val planUnavailable: Boolean,
    val headlines: List<String>,
    @SerialName("headlines_unavailable") val headlinesUnavailable: Boolean,
    @SerialName("deadlines_today") val deadlinesToday: List<DeadlineData>,
    @SerialName("deadlines_tomorrow") val deadlinesTomorrow: List<DeadlineData>,
)

@Serializable
internal data class EveningData(
    val tomorrow: String,
    @SerialName("tomorrow_events") val tomorrowEvents: List<EventData>?,
    @SerialName("tomorrow_topic_plans") val tomorrowTopicPlans: List<TopicPlanData>,
    @SerialName("topics_without_plans") val topicsWithoutPlans: List<String>,
    @SerialName("calendar_unavailable") val calendarUnavailable: Boolean,
    @SerialName("deadlines_tomorrow") val deadlinesTomorrow: List<DeadlineData>,
    @SerialName("weekly_theme") val weeklyTheme: ThemeData?,
)

@Serializable
internal data class WeatherData(
    @SerialName("morning_temperature") val morningTemperature: String?,
    @SerialName("afternoon_temperature") val afternoonTemperature: String?,
    val sky: String?,
    val precipitation: String?,
    @SerialName("precipitation_from") val precipitationFrom: String?,
    @SerialName("precipitation_probability") val precipitationProbability: String?,
    @SerialName("strong_wind_gusts") val strongWindGusts: String?,
)

@Serializable
internal data class EventData(val time: String, val title: String, val location: String?)

@Serializable
internal data class TopicPlanData(val topic: String, val plans: List<PlanItemData>)

/** A plan without a time (an all-day event) has no `time`. */
@Serializable
internal data class PlanItemData(val title: String, val time: String?)

@Serializable
internal data class PlanData(
    val phase: String?,
    @SerialName("daily_minimum") val dailyMinimum: List<String>,
    @SerialName("morning_reading") val morningReading: String?,
    @SerialName("saturday_study") val saturdayStudy: String?,
    val items: List<String>,
    @SerialName("weekly_theme") val weeklyTheme: ThemeData?,
)

@Serializable
internal data class ThemeData(
    val title: String,
    val task: String,
    @SerialName("reflection_question") val reflectionQuestion: String?,
)

@Serializable
internal data class DeadlineData(val subject: String?, val title: String, val mandatory: Boolean)

internal fun MorningInput.toData() = MorningData(
    date = dateSpoken,
    weather = weather?.toData(),
    weatherUnavailable = weather == null,
    events = events?.map { it.toData() },
    topicPlans = topicPlans.map { it.toData() },
    calendarUnavailable = events == null,
    plan = plan?.let {
        PlanData(it.phase, it.dailyMinimum, it.morningReading, it.saturdayStudy, it.items, it.theme?.toData())
    },
    planUnavailable = planUnavailable,
    headlines = headlines,
    headlinesUnavailable = headlinesUnavailable,
    deadlinesToday = deadlinesToday.map { it.toData() },
    deadlinesTomorrow = deadlinesTomorrow.map { it.toData() },
)

internal fun EveningInput.toData() = EveningData(
    tomorrow = tomorrowSpoken,
    tomorrowEvents = tomorrowEvents?.map { it.toData() },
    tomorrowTopicPlans = tomorrowTopicPlans.map { it.toData() },
    topicsWithoutPlans = topicsWithoutPlansTomorrow,
    calendarUnavailable = tomorrowEvents == null,
    deadlinesTomorrow = deadlinesTomorrow.map { it.toData() },
    weeklyTheme = theme?.toData(),
)

private fun WeatherFacts.toData() = WeatherData(
    morningTemperature,
    afternoonTemperature,
    sky,
    precipitation,
    precipitationFrom,
    precipitationProbability,
    strongWindGusts,
)

private fun EventFacts.toData() = EventData(time, title, location)

private fun TopicPlanFacts.toData() = TopicPlanData(topic, plans.map { PlanItemData(it.title, it.time) })

private fun ThemeFacts.toData() = ThemeData(title, task, reflectionQuestion)

private fun DeadlineFacts.toData() = DeadlineData(subject, title, mandatory)

// Output: one string per section. Structured outputs guarantee this shape.

@Serializable
internal data class MorningSections(
    @SerialName("greeting_and_weather") val greetingAndWeather: String,
    val calendar: String,
    val plan: String,
    val news: String,
    val warnings: String,
    val closing: String,
) {
    fun toBriefing() = BriefingText(
        BriefingKind.MORNING,
        listOf(
            BriefingSection(SectionKind.GREETING_AND_WEATHER, greetingAndWeather),
            BriefingSection(SectionKind.CALENDAR, calendar),
            BriefingSection(SectionKind.PLAN, plan),
            BriefingSection(SectionKind.NEWS, news),
            BriefingSection(SectionKind.WARNINGS, warnings),
            BriefingSection(SectionKind.CLOSING, closing),
        ),
    )
}

@Serializable
internal data class EveningSections(
    val checklist: String,
    val tomorrow: String,
    val reflection: String,
    @SerialName("good_night") val goodNight: String,
) {
    fun toBriefing() = BriefingText(
        BriefingKind.EVENING,
        listOf(
            BriefingSection(SectionKind.CHECKLIST, checklist),
            BriefingSection(SectionKind.TOMORROW, tomorrow),
            BriefingSection(SectionKind.REFLECTION, reflection),
            BriefingSection(SectionKind.GOOD_NIGHT, goodNight),
        ),
    )
}

/** JSON schemas for structured outputs: every object needs `additionalProperties: false`. */
internal object BriefingSchemas {
    val morning: JsonObject = sectionsSchema(
        "greeting_and_weather",
        "calendar",
        "plan",
        "news",
        "warnings",
        "closing",
    )
    val evening: JsonObject = sectionsSchema("checklist", "tomorrow", "reflection", "good_night")

    private fun sectionsSchema(vararg sections: String) = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            sections.forEach { putJsonObject(it) { put("type", "string") } }
        }
        putJsonArray("required") { sections.forEach { add(it) } }
        put("additionalProperties", false)
    }
}
