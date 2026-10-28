package io.github.adccz.elias.domain.status

/** What the phone allows Elias to do on its own. Each false value has a fix on the status screen. */
data class SystemStatus(
    /** Reading the calendar, for today's and tomorrow's events. */
    val calendar: Boolean,
    /** Showing ordinary notifications, for the Friday sabbath reminder. */
    val notifications: Boolean,
    /** Exact alarms, for the evening briefing, the prefetch and the reminder on time. */
    val exactAlarms: Boolean,
    /** No battery optimization for Elias, so the phone does not put the prefetch to sleep. */
    val batteryUnrestricted: Boolean,
)

/** Reads the [SystemStatus]. Implemented with Android's permission and power APIs. */
fun interface SystemChecks {
    fun current(): SystemStatus
}
