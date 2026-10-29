package io.github.adccz.elias.domain.briefing

import io.github.adccz.elias.domain.calendar.CalendarEvent
import io.github.adccz.elias.domain.model.SourceResult
import io.github.adccz.elias.domain.news.DefaultNewsFeeds
import io.github.adccz.elias.domain.news.pickForBriefing
import io.github.adccz.elias.domain.overview.TodayOverview
import io.github.adccz.elias.domain.plan.Deadline
import io.github.adccz.elias.domain.plan.WeeklyTheme
import io.github.adccz.elias.domain.speech.BibleReference
import io.github.adccz.elias.domain.speech.CzechNumbers
import io.github.adccz.elias.domain.speech.Gender
import io.github.adccz.elias.domain.speech.SpokenDate
import io.github.adccz.elias.domain.speech.SpokenTime
import io.github.adccz.elias.domain.weather.DayWeather
import io.github.adccz.elias.domain.weather.WeatherCondition
import kotlin.math.roundToInt

/** Builds the briefing inputs from what Elias knows today ([TodayOverview]). */
object BriefingInputs {
    /** Gusts from this speed on are worth a warning. */
    const val STRONG_GUST_KMH = 50

    /** How many headlines a morning briefing reads. */
    const val HEADLINES_IN_BRIEFING = 3

    fun morning(overview: TodayOverview): MorningInput {
        val date = overview.loadedAt.toLocalDate()
        val plan = (overview.plan as? SourceResult.Success)?.data
        val day = plan?.day
        val feedsLoaded = overview.newsFeeds.values.any { it is SourceResult.Success }
        return MorningInput(
            date = date,
            dateSpoken = SpokenDate.dayAndDate(date),
            weather = (overview.weather as? SourceResult.Success)?.data?.toFacts(),
            events = (overview.calendar as? SourceResult.Success)?.data?.map { it.toFacts() },
            plan = day?.takeIf { it.isPlanValid }?.let {
                PlanFacts(
                    phase = it.phase?.name,
                    dailyMinimum = it.dailyMinimum,
                    morningReading = it.morningReading?.let(BibleReference::spoken),
                    saturdayStudy = it.saturdayStudy?.let(BibleReference::spoken),
                    items = it.items.map { item -> item.title },
                    theme = it.weeklyTheme?.toFacts(),
                )
            },
            planUnavailable = plan == null,
            headlines = pickForBriefing(
                overview.headlines,
                sourceOrder = DefaultNewsFeeds.all.map { it.name },
                count = HEADLINES_IN_BRIEFING,
            ).map { it.title },
            headlinesUnavailable = !feedsLoaded,
            deadlinesToday = day?.deadlinesToday.orEmpty().map { it.toFacts() },
            deadlinesTomorrow = day?.deadlinesTomorrow.orEmpty().map { it.toFacts() },
        )
    }

    fun evening(overview: TodayOverview): EveningInput {
        val date = overview.loadedAt.toLocalDate()
        val day = (overview.plan as? SourceResult.Success)?.data?.day
        return EveningInput(
            date = date,
            tomorrowSpoken = SpokenDate.dayAndDate(date.plusDays(1)),
            tomorrowEvents = (overview.tomorrowCalendar as? SourceResult.Success)?.data?.map { it.toFacts() },
            deadlinesTomorrow = day?.deadlinesTomorrow.orEmpty().map { it.toFacts() },
            theme = day?.takeIf { it.isPlanValid }?.weeklyTheme?.toFacts(),
        )
    }

    /** Today's sabbath study for "the sabbath has ended", spoken. Null without a valid plan or a study today. */
    fun sabbathStudy(overview: TodayOverview): String? = (overview.plan as? SourceResult.Success)?.data?.day
        ?.takeIf { it.isPlanValid }
        ?.saturdayStudy
        ?.let(BibleReference::spoken)

    private fun DayWeather.toFacts(): WeatherFacts {
        val gusts = maxGustKmh?.roundToInt()
        return WeatherFacts(
            morningTemperature = morningTemperature?.let(::degrees),
            afternoonTemperature = afternoonTemperature?.let(::degrees),
            sky = sky?.let(::spokenSky),
            precipitation = precipitation?.czech,
            precipitationFrom = precipitationFrom?.let(SpokenTime::around),
            precipitationProbability = maxPrecipitationProbabilityPercent
                ?.takeIf { precipitation != null && it > 0 }
                ?.let { CzechNumbers.count(it, "procento", "procenta", "procent", Gender.NEUTER) },
            strongWindGusts = gusts?.takeIf { it >= STRONG_GUST_KMH }?.let {
                CzechNumbers.count(it, "kilometr", "kilometry", "kilometrů") + " za hodinu"
            },
        )
    }

    private fun degrees(celsius: Double): String =
        CzechNumbers.count(celsius.roundToInt(), "stupeň", "stupně", "stupňů")

    // "mlha" reads oddly in "bude mlha", so fog becomes "mlhavo" like the other sky words.
    private fun spokenSky(condition: WeatherCondition): String =
        if (condition == WeatherCondition.FOG) "mlhavo" else condition.czech

    private fun CalendarEvent.toFacts() = EventFacts(
        title = title,
        time = if (isAllDay) "celý den" else SpokenTime.at(start.toLocalTime()),
        location = location,
    )

    private fun WeeklyTheme.toFacts() = ThemeFacts(title, task, reflectionQuestion)

    private fun Deadline.toFacts() = DeadlineFacts(subject, title, mandatory)
}
