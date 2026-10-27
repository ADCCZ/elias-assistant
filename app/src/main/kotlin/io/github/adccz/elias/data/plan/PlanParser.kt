package io.github.adccz.elias.data.plan

import io.github.adccz.elias.domain.plan.DatedText
import io.github.adccz.elias.domain.plan.Deadline
import io.github.adccz.elias.domain.plan.Phase
import io.github.adccz.elias.domain.plan.Plan
import io.github.adccz.elias.domain.plan.RecurringItem
import io.github.adccz.elias.domain.plan.WeeklyTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.Inject
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The plan file is wrong. The message says what and where, so it can be fixed by hand. */
class InvalidPlanException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Reads `plan.json` (schema version 1) into a [Plan]. */
class PlanParser @Inject constructor() {
    // Unknown keys are errors on purpose: a typo in a hand-written file should not be silently ignored.
    private val json = Json { ignoreUnknownKeys = false }

    /** @throws InvalidPlanException when the JSON is malformed or a value is invalid. */
    fun parse(text: String): Plan {
        val file = try {
            json.decodeFromString<PlanFile>(text)
        } catch (e: IllegalArgumentException) {
            throw InvalidPlanException("Plan file is not valid JSON for schema 1: ${e.message}", e)
        }
        if (file.schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            throw InvalidPlanException(
                "Unsupported schemaVersion ${file.schemaVersion}, expected $SUPPORTED_SCHEMA_VERSION",
            )
        }
        return file.toPlan()
    }

    private fun PlanFile.toPlan(): Plan {
        val plan = Plan(
            name = name,
            validFrom = date(validFrom, "validFrom"),
            validTo = date(validTo, "validTo"),
            phases = phases.map { Phase(it.name, date(it.from, "phases.from"), date(it.to, "phases.to")) },
            dailyMinimum = dailyMinimum,
            rules = rules,
            weeklyThemes = weeklyThemes.map { theme ->
                val weekStart = date(theme.weekStart, "weeklyThemes.weekStart")
                if (weekStart.dayOfWeek != DayOfWeek.MONDAY) {
                    throw InvalidPlanException(
                        "Weekly theme '${theme.title}' starts on $weekStart, which is not a Monday",
                    )
                }
                WeeklyTheme(weekStart, theme.title, theme.task)
            },
            morningReadings = morningReadings.map { it.toDatedText("morningReadings") },
            saturdayStudies = saturdayStudies.map { it.toDatedText("saturdayStudies") },
            recurringItems = recurring.map { item ->
                if (item.days.isEmpty()) throw InvalidPlanException("Recurring item '${item.title}' has no days")
                RecurringItem(item.title, item.days.map { dayOfWeek(it, item.title) }.toSet(), item.note)
            },
            oneTimeItems = oneTime.map { it.toDatedText("oneTime") },
            deadlines = deadlines.map {
                Deadline(date(it.date, "deadlines.date"), it.title, it.subject, it.mandatory)
            },
        )
        if (plan.validTo < plan.validFrom) throw InvalidPlanException("validTo is before validFrom")
        plan.phases.firstOrNull { it.to < it.from }?.let {
            throw InvalidPlanException("Phase '${it.name}' ends before it starts")
        }
        return plan
    }

    private fun DatedTextEntry.toDatedText(field: String) = DatedText(date(date, "$field.date"), text)

    private fun date(value: String, field: String): LocalDate = try {
        LocalDate.parse(value)
    } catch (e: DateTimeParseException) {
        throw InvalidPlanException("Invalid date '$value' in $field, expected YYYY-MM-DD", e)
    }

    private fun dayOfWeek(value: String, itemTitle: String): DayOfWeek = try {
        DayOfWeek.valueOf(value.uppercase())
    } catch (e: IllegalArgumentException) {
        throw InvalidPlanException("Invalid day '$value' in recurring item '$itemTitle', expected e.g. MONDAY", e)
    }

    private companion object {
        const val SUPPORTED_SCHEMA_VERSION = 1
    }
}

// JSON shape of plan.json. Kept separate from the domain model, so the file format can change on its own.

@Serializable
private data class PlanFile(
    val schemaVersion: Int,
    val name: String,
    val validFrom: String,
    val validTo: String,
    val phases: List<PhaseEntry> = emptyList(),
    val dailyMinimum: List<String> = emptyList(),
    val rules: List<String> = emptyList(),
    val weeklyThemes: List<WeeklyThemeEntry> = emptyList(),
    val morningReadings: List<DatedTextEntry> = emptyList(),
    val saturdayStudies: List<DatedTextEntry> = emptyList(),
    val recurring: List<RecurringEntry> = emptyList(),
    val oneTime: List<DatedTextEntry> = emptyList(),
    val deadlines: List<DeadlineEntry> = emptyList(),
)

@Serializable
private data class PhaseEntry(val name: String, val from: String, val to: String)

@Serializable
private data class WeeklyThemeEntry(val weekStart: String, val title: String, val task: String)

@Serializable
private data class DatedTextEntry(val date: String, val text: String)

@Serializable
private data class RecurringEntry(val title: String, val days: List<String>, val note: String? = null)

@Serializable
private data class DeadlineEntry(
    val date: String,
    val title: String,
    val subject: String? = null,
    val mandatory: Boolean = false,
)
