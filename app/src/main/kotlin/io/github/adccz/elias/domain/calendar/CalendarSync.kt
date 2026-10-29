package io.github.adccz.elias.domain.calendar

/** What happened when the user asked the phone to download the calendars now. */
sealed interface SyncRequest {
    /** The phone was asked to sync [accounts] online accounts. The new events arrive a moment later. */
    data class Sent(val accounts: Int) : SyncRequest

    /** There is no online account on the phone, only calendars kept on the phone itself. */
    data object NothingToSync : SyncRequest

    /** The phone refused the request (no calendar permission, or the system did not allow it). */
    data object Failed : SyncRequest
}

/**
 * Asks the phone's accounts (Google, Samsung...) to download their calendars now, like "Synchronizovat nyní" in the
 * account settings. Elias never changes what is synced: switching a calendar's sync on stays in the calendar app.
 */
interface CalendarSync {
    suspend fun syncNow(): SyncRequest
}
