package io.github.adccz.elias.data.calendar

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SyncAccountsTest {
    @Test
    fun `asks every online account once and skips the phone's own calendars`() {
        val rows = listOf(
            "My calendar" to "LOCAL",
            "olda@example.com" to "com.google",
            "olda@example.com" to "com.google",
            "olda@example.com" to "com.osp.app.signin",
            "student@example.edu" to "com.google",
            null to "com.google",
            " " to "com.google",
            "olda@example.com" to null,
        )

        assertEquals(
            listOf(
                SyncAccount("olda@example.com", "com.google"),
                SyncAccount("olda@example.com", "com.osp.app.signin"),
                SyncAccount("student@example.edu", "com.google"),
            ),
            SyncAccounts.of(rows),
        )
    }
}
