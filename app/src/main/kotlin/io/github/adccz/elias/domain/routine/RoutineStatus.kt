package io.github.adccz.elias.domain.routine

import io.github.adccz.elias.domain.reading.ReadingStatus

/**
 * The morning routine of the day (spec, M5): the reading queue, today's exercises and recipes. A part that could not
 * be read is null, and the briefings then leave it out. It stays on the phone: the template speaks it after the AI
 * answer.
 */
data class RoutineStatus(val reading: ReadingStatus?, val exercises: List<Exercise>?, val recipes: DayRecipes?) {
    companion object {
        val UNKNOWN = RoutineStatus(reading = null, exercises = null, recipes = null)
    }
}
