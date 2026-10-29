package io.github.adccz.elias.domain.topics

import io.github.adccz.elias.domain.calendar.CalendarEvent
import io.github.adccz.elias.domain.calendar.PhoneCalendar
import java.text.Collator
import java.time.LocalDate
import java.util.Locale

/**
 * A topic of the user's plans ("Škola", "Seberozvoj", "Španělština"). Each topic is one calendar on the phone:
 * the user writes plans for the next days into that calendar in Google Calendar (spec, M4.5).
 */
data class Topic(val calendarId: Long, val name: String)

/** The plans of one topic on one day. [plans] may be empty: the topic has nothing planned for that day. */
data class TopicPlans(val topic: Topic, val plans: List<CalendarEvent>)

/**
 * One day split into ordinary [events] (from calendars that are not topics) and the plans of every topic.
 * [topics] has every topic in a fixed order, also those without plans that day.
 */
data class DayAgenda(val date: LocalDate, val events: List<CalendarEvent>, val topics: List<TopicPlans>) {
    val topicsWithPlans: List<TopicPlans> get() = topics.filter { it.plans.isNotEmpty() }

    val topicsWithoutPlans: List<Topic> get() = topics.filter { it.plans.isEmpty() }.map { it.topic }
}

object Topics {
    private val czechOrder: Collator = Collator.getInstance(Locale.forLanguageTag("cs-CZ"))

    /**
     * The topics among the phone's [calendars], in Czech alphabetical order ("Seberozvoj", "Škola", "Španělština").
     * A stored id whose calendar is gone (deleted, account removed) is skipped.
     */
    fun resolve(calendars: List<PhoneCalendar>, topicIds: Set<Long>): List<Topic> = calendars
        .filter { it.id in topicIds }
        .map { Topic(it.id, it.name) }
        .sortedWith(compareBy(czechOrder) { it.name })

    /** Splits the [events] of [date] into ordinary events and the plans of each of the [topics]. Order is kept. */
    fun agenda(date: LocalDate, events: List<CalendarEvent>, topics: List<Topic>): DayAgenda {
        val topicIds = topics.map { it.calendarId }.toSet()
        val (plans, ordinary) = events.partition { it.calendarId != null && it.calendarId in topicIds }
        return DayAgenda(
            date = date,
            events = ordinary,
            topics = topics.map { topic -> TopicPlans(topic, plans.filter { it.calendarId == topic.calendarId }) },
        )
    }
}
