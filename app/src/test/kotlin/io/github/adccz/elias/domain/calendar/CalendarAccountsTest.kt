package io.github.adccz.elias.domain.calendar

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CalendarAccountsTest {
    private val personal = CalendarAccount.Online("olda@example.com")
    private val school = CalendarAccount.Online("student@example.edu")

    private fun calendar(id: Long, name: String, account: CalendarAccount, isShown: Boolean = true) =
        PhoneCalendar(id, name, (account as? CalendarAccount.Online)?.name, null, isShown, account)

    @Test
    fun `online accounts come first in the phone's order, then Samsung, then the phone`() {
        val calendars = listOf(
            calendar(1, "My calendar", CalendarAccount.Phone),
            calendar(2, "Samsung Calendar", CalendarAccount.Samsung),
            calendar(3, "Svátky", personal),
            calendar(4, "Předměty", school),
            calendar(5, "Rodina", personal),
        )

        val groups = CalendarAccounts.group(calendars)

        assertEquals(
            listOf(personal, school, CalendarAccount.Samsung, CalendarAccount.Phone),
            groups.map {
                it.account
            },
        )
        assertEquals(listOf("Svátky", "Rodina"), groups.first().calendars.map { it.name })
    }

    @Test
    fun `calendars that do not reach the phone go to the end of their account`() {
        val calendars = listOf(
            calendar(1, "Archiv", personal, isShown = false),
            calendar(2, "Rodina", personal),
            calendar(3, "Svátky", personal),
        )

        assertEquals(
            listOf("Rodina", "Svátky", "Archiv"),
            CalendarAccounts.group(calendars).single().calendars.map { it.name },
        )
    }
}
