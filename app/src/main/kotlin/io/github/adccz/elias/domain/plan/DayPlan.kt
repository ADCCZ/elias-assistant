package io.github.adccz.elias.domain.plan

import java.time.LocalDate

/** What the plan says about one particular day. */
data class DayPlan(
    val date: LocalDate,
    /** False when the date is outside the plan. Then only [deadlinesToday] and [deadlinesTomorrow] are filled. */
    val isPlanValid: Boolean,
    val phase: Phase?,
    val dailyMinimum: List<String>,
    val rules: List<String>,
    val weeklyTheme: WeeklyTheme?,
    val morningReading: String?,
    val saturdayStudy: String?,
    val items: List<DayItem>,
    val deadlinesToday: List<Deadline>,
    val deadlinesTomorrow: List<Deadline>,
)

/** One task for the day, from a recurring item or a one-off entry. */
data class DayItem(val title: String, val note: String? = null)

/** Picks everything from the plan that applies on [date]. */
fun Plan.dayPlan(date: LocalDate): DayPlan {
    val deadlinesToday = deadlines.filter { it.date == date }
    val deadlinesTomorrow = deadlines.filter { it.date == date.plusDays(1) }

    if (!isValidOn(date)) {
        return DayPlan(
            date = date,
            isPlanValid = false,
            phase = null,
            dailyMinimum = emptyList(),
            rules = emptyList(),
            weeklyTheme = null,
            morningReading = null,
            saturdayStudy = null,
            items = emptyList(),
            deadlinesToday = deadlinesToday,
            deadlinesTomorrow = deadlinesTomorrow,
        )
    }

    val recurring = recurringItems
        .filter { date.dayOfWeek in it.days }
        .map { DayItem(title = it.title, note = it.note) }
    val oneTime = oneTimeItems
        .filter { it.date == date }
        .map { DayItem(title = it.text) }

    return DayPlan(
        date = date,
        isPlanValid = true,
        phase = phases.firstOrNull { date in it.from..it.to },
        dailyMinimum = dailyMinimum,
        rules = rules,
        // A theme covers its Monday and the six days after it.
        weeklyTheme = weeklyThemes.firstOrNull { date >= it.weekStart && date < it.weekStart.plusWeeks(1) },
        morningReading = morningReadings.firstOrNull { it.date == date }?.text,
        saturdayStudy = saturdayStudies.firstOrNull { it.date == date }?.text,
        items = recurring + oneTime,
        deadlinesToday = deadlinesToday,
        deadlinesTomorrow = deadlinesTomorrow,
    )
}
