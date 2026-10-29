package io.github.adccz.elias.ui.data

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.adccz.elias.R
import io.github.adccz.elias.domain.calendar.CalendarEvent
import io.github.adccz.elias.domain.model.FailureReason
import io.github.adccz.elias.domain.model.SourceResult
import io.github.adccz.elias.domain.overview.TodayOverview
import io.github.adccz.elias.domain.plan.Deadline
import io.github.adccz.elias.ui.common.CzechFormat
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.roundToInt

// One card per data source. Each card shows whether its source loaded, and what it knows.

@Composable
internal fun DaySection(overview: TodayOverview) {
    SectionCard(title = stringResource(R.string.section_day)) {
        val sun = overview.sunTimes
        Text(stringResource(R.string.day_sun, sun.sunrise.timeOrDash(), sun.sunset.timeOrDash()))
        val sabbath = overview.sabbath
        if (overview.loadedAt in sabbath) {
            Text(stringResource(R.string.day_sabbath_now), fontWeight = FontWeight.Medium)
        } else {
            Text(
                stringResource(
                    R.string.day_sabbath_start,
                    sabbath.start.format(CzechFormat.dayAndDate),
                    sabbath.start.format(CzechFormat.time),
                ),
            )
        }
        Text(
            stringResource(
                R.string.day_sabbath_end,
                sabbath.end.format(CzechFormat.dayAndDate),
                sabbath.end.format(CzechFormat.time),
            ),
        )
    }
}

@Composable
internal fun CalendarSection(overview: TodayOverview, onRequestPermission: () -> Unit, onOpenAppSettings: () -> Unit) {
    val result = overview.calendar
    SectionCard(title = stringResource(R.string.section_calendar), status = result, zone = overview.zone) {
        when (result) {
            is SourceResult.Success -> {
                if (result.data.isEmpty()) Text(stringResource(R.string.calendar_empty))
                result.data.forEach { EventRow(it) }
            }

            is SourceResult.Failure -> if (result.reason == FailureReason.NO_PERMISSION) {
                Text(stringResource(R.string.calendar_permission_explanation))
                Button(onClick = onRequestPermission) { Text(stringResource(R.string.calendar_grant)) }
                TextButton(onClick = onOpenAppSettings) { Text(stringResource(R.string.calendar_open_settings)) }
            }
        }
    }
}

@Composable
private fun EventRow(event: CalendarEvent) {
    val time = if (event.isAllDay) {
        stringResource(R.string.calendar_all_day)
    } else {
        stringResource(
            R.string.calendar_time_range,
            event.start.format(CzechFormat.time),
            event.end.format(CzechFormat.time),
        )
    }
    Column {
        Text(
            text = event.title.ifEmpty { stringResource(R.string.calendar_untitled) },
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = listOfNotNull(time, event.location).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun WeatherSection(overview: TodayOverview) {
    val result = overview.weather
    SectionCard(title = stringResource(R.string.section_weather), status = result, zone = overview.zone) {
        if (result !is SourceResult.Success) return@SectionCard
        val day = result.data
        Text(
            stringResource(
                R.string.weather_temperatures,
                day.morningTemperature?.let(CzechFormat::temperature) ?: stringResource(R.string.value_missing),
                day.afternoonTemperature?.let(CzechFormat::temperature) ?: stringResource(R.string.value_missing),
            ),
        )
        day.sky?.let { Text(it.czech.capitalizeFirst()) }
        val precipitation = day.precipitation
        if (precipitation == null) {
            Text(stringResource(R.string.weather_dry))
        } else {
            Text(
                stringResource(
                    R.string.weather_precipitation,
                    precipitation.czech.capitalizeFirst(),
                    day.precipitationFrom?.format(CzechFormat.time) ?: stringResource(R.string.value_missing),
                    CzechFormat.oneDecimal(day.precipitationMm),
                ),
            )
        }
        day.maxPrecipitationProbabilityPercent?.takeIf { it > 0 }?.let {
            Text(stringResource(R.string.weather_probability, it))
        }
        if (day.maxWindKmh != null && day.maxGustKmh != null) {
            Text(stringResource(R.string.weather_wind, day.maxWindKmh.roundToInt(), day.maxGustKmh.roundToInt()))
        }
        // Required by the Open-Meteo data license (CC BY 4.0).
        Text(
            text = stringResource(R.string.weather_attribution),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun PlanSection(overview: TodayOverview) {
    val result = overview.plan
    SectionCard(title = stringResource(R.string.section_plan), status = result, zone = overview.zone) {
        if (result !is SourceResult.Success) return@SectionCard
        val today = result.data
        val day = today.day
        Text(today.planName, fontWeight = FontWeight.Medium)
        if (today.isSample) Hint(stringResource(R.string.plan_sample_note))

        if (day.isPlanValid) {
            day.phase?.let { Text(stringResource(R.string.plan_phase, it.name)) }
            if (day.dailyMinimum.isNotEmpty()) {
                Text(stringResource(R.string.plan_minimum, day.dailyMinimum.joinToString(", ")))
            }
            day.weeklyTheme?.let { Text(stringResource(R.string.plan_theme, it.title, it.task)) }
            day.morningReading?.let { Text(stringResource(R.string.plan_reading, it)) }
            day.saturdayStudy?.let { Text(stringResource(R.string.plan_study, it)) }
            if (day.items.isNotEmpty()) {
                Text(stringResource(R.string.plan_items, day.items.joinToString(", ") { it.title }))
            }
        } else {
            Text(
                stringResource(
                    R.string.plan_not_valid,
                    today.validFrom.format(CzechFormat.shortDate),
                    today.validTo.format(CzechFormat.shortDate),
                ),
            )
        }
        day.deadlinesToday.forEach { Text(stringResource(R.string.plan_deadline_today, it.describe())) }
        day.deadlinesTomorrow.forEach { Text(stringResource(R.string.plan_deadline_tomorrow, it.describe())) }
        if (day.isPlanValid && day.rules.isNotEmpty()) {
            Hint(stringResource(R.string.plan_rules, day.rules.joinToString(", ")))
        }
    }
}

/** The morning routine (M5): the reading queue, today's exercises and the recipes. */
@Composable
internal fun RoutineSection(overview: TodayOverview) {
    val routine = overview.routine
    SectionCard(title = stringResource(R.string.section_routine)) {
        val reading = routine.reading
        val next = reading?.next
        Text(
            when {
                reading == null -> stringResource(R.string.routine_reading_unknown)
                next != null -> stringResource(R.string.routine_reading_next, next.label)
                reading.finished -> stringResource(R.string.routine_reading_finished)
                else -> stringResource(R.string.routine_reading_plan)
            },
        )
        if (reading?.readToday == true) Hint(stringResource(R.string.routine_reading_done))
        val exercises = routine.exercises
        Text(
            if (exercises == null) {
                stringResource(R.string.exercises_failed)
            } else {
                stringResource(R.string.routine_exercises, exercises.joinToString(", ") { it.name })
            },
        )
        val recipes = routine.recipes
        if (recipes == null) {
            Text(stringResource(R.string.recipe_failed))
        } else {
            Text(stringResource(R.string.routine_recipe, recipes.today.name))
            Hint(stringResource(R.string.recipe_tomorrow, recipes.tomorrow.name))
        }
    }
}

@Composable
private fun Deadline.describe(): String {
    val mandatoryNote = if (mandatory) " (${stringResource(R.string.plan_mandatory)})" else ""
    return listOfNotNull(subject, title).joinToString(": ") + mandatoryNote
}

@Composable
internal fun NewsSection(overview: TodayOverview) {
    SectionCard(title = stringResource(R.string.section_news)) {
        overview.newsFeeds.forEach { (feed, result) ->
            Text(
                text = stringResource(R.string.status_feed, feed.name, statusText(result, overview.zone)),
                style = MaterialTheme.typography.bodySmall,
                color = statusColor(result),
            )
        }
        if (overview.headlines.isEmpty()) Text(stringResource(R.string.news_empty))
        overview.headlines.forEach { headline ->
            Column(modifier = Modifier.padding(top = 4.dp)) {
                Text(headline.title)
                Hint(
                    stringResource(
                        R.string.news_source_and_time,
                        headline.source,
                        headline.publishedAt?.atZone(overview.zone).timeOrDash(),
                    ),
                )
            }
        }
    }
}

// Building blocks shared by the sections.

@Composable
private fun SectionCard(
    title: String,
    status: SourceResult<*>? = null,
    zone: ZoneId? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            if (status != null && zone != null) {
                Text(
                    text = statusText(status, zone),
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor(status),
                )
                // The technical detail helps while developing. The Log screen (M8) will take this over.
                (status as? SourceResult.Failure)?.detail?.let { Hint(it) }
            }
            content()
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun statusText(result: SourceResult<*>, zone: ZoneId): String = when (result) {
    is SourceResult.Success -> if (result.fromCache) {
        val loadedAt = result.loadedAt.atZone(zone)
        stringResource(
            R.string.status_cached,
            loadedAt.format(CzechFormat.dayAndDate),
            loadedAt.format(CzechFormat.time),
        )
    } else {
        stringResource(R.string.status_loaded, result.loadedAt.atZone(zone).format(CzechFormat.time))
    }

    is SourceResult.Failure -> stringResource(R.string.status_failed, stringResource(result.reason.messageRes()))
}

@Composable
private fun statusColor(result: SourceResult<*>) = when (result) {
    is SourceResult.Success -> MaterialTheme.colorScheme.onSurfaceVariant
    is SourceResult.Failure -> MaterialTheme.colorScheme.error
}

@StringRes
private fun FailureReason.messageRes(): Int = when (this) {
    FailureReason.NO_PERMISSION -> R.string.failure_no_permission
    FailureReason.NETWORK -> R.string.failure_network
    FailureReason.TIMEOUT -> R.string.failure_timeout
    FailureReason.SERVER_ERROR -> R.string.failure_server_error
    FailureReason.INVALID_DATA -> R.string.failure_invalid_data
    FailureReason.UNKNOWN -> R.string.failure_unknown
}

@Composable
private fun ZonedDateTime?.timeOrDash(): String =
    this?.format(CzechFormat.time) ?: stringResource(R.string.value_missing)

private fun String.capitalizeFirst(): String = replaceFirstChar { it.uppercase() }

private val TodayOverview.zone: ZoneId get() = loadedAt.zone
