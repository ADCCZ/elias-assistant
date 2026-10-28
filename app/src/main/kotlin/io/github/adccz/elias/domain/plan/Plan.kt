package io.github.adccz.elias.domain.plan

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * A personal plan for a period of weeks. Everything is driven by dates,
 * so the plan can be replaced by another one without code changes.
 */
data class Plan(
    val name: String,
    val validFrom: LocalDate,
    val validTo: LocalDate,
    val phases: List<Phase>,
    val dailyMinimum: List<String>,
    val rules: List<String>,
    val weeklyThemes: List<WeeklyTheme>,
    val morningReadings: List<DatedText>,
    val saturdayStudies: List<DatedText>,
    val recurringItems: List<RecurringItem>,
    val oneTimeItems: List<DatedText>,
    val deadlines: List<Deadline>,
) {
    fun isValidOn(date: LocalDate): Boolean = date in validFrom..validTo
}

/** A named part of the plan, both dates inclusive. */
data class Phase(val name: String, val from: LocalDate, val to: LocalDate)

/**
 * A theme for the week starting on [weekStart] (a Monday), with a concrete task.
 * [reflectionQuestion] is asked in the evening; when missing, the briefing builds a question from the task.
 */
data class WeeklyTheme(
    val weekStart: LocalDate,
    val title: String,
    val task: String,
    val reflectionQuestion: String? = null,
)

/** A text tied to one date, such as a Bible reading ("Ž 23") or a one-off task. */
data class DatedText(val date: LocalDate, val text: String)

/** A task that repeats on given days of the week while the plan is valid. */
data class RecurringItem(val title: String, val days: Set<DayOfWeek>, val note: String? = null)

/** A deadline. Deadlines count even outside the plan's validity (an exam can come later). */
data class Deadline(val date: LocalDate, val title: String, val subject: String? = null, val mandatory: Boolean = false)

/** A plan as loaded by the app. [isSample] is true when the fictional sample plan is used. */
data class LoadedPlan(val plan: Plan, val isSample: Boolean)
