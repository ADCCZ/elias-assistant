package io.github.adccz.elias.data.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.adccz.elias.core.coroutines.IoDispatcher
import io.github.adccz.elias.domain.calendar.CalendarEvent
import io.github.adccz.elias.domain.calendar.CalendarRepository
import io.github.adccz.elias.domain.calendar.PhoneCalendar
import io.github.adccz.elias.domain.model.FailureReason
import io.github.adccz.elias.domain.model.SourceResult
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Reads event occurrences from Android's calendar provider (the Google Calendar synced to the phone).
 *
 * It queries `Instances`, not `Events`: Instances has one row per occurrence, so repeating events
 * (every Tuesday...) are already expanded. This class only copies rows out of the cursor.
 * All the date logic lives in [CalendarInstanceMapper], which is unit-tested.
 */
class ContentResolverCalendarRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val mapper: CalendarInstanceMapper,
    private val zone: ZoneId,
    private val clock: Clock,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : CalendarRepository {
    override suspend fun eventsOn(date: LocalDate): SourceResult<List<CalendarEvent>> =
        when (val result = eventsFrom(date, days = 1)) {
            is SourceResult.Success -> SourceResult.Success(result.data.getValue(date), result.loadedAt)
            is SourceResult.Failure -> result
        }

    override suspend fun eventsFrom(
        firstDay: LocalDate,
        days: Int,
    ): SourceResult<Map<LocalDate, List<CalendarEvent>>> = readCalendar {
        // One extra day on both sides catches all-day events, which are stored in UTC.
        // The mapper then picks exactly the events of each requested day.
        val from = firstDay.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = firstDay.plusDays(days + 1L).atStartOfDay(zone).toInstant().toEpochMilli()
        val rows = queryInstances(from, to)
        (0 until days).map { firstDay.plusDays(it.toLong()) }.associateWith { mapper.eventsOn(it, zone, rows) }
    }

    override suspend fun calendars(): SourceResult<List<PhoneCalendar>> = readCalendar {
        mapper.calendars(queryCalendars())
    }

    private suspend fun <T> readCalendar(read: () -> T): SourceResult<T> = withContext(ioDispatcher) {
        if (!hasPermission()) return@withContext SourceResult.Failure(FailureReason.NO_PERMISSION)
        try {
            SourceResult.Success(read(), clock.instant())
        } catch (e: SecurityException) {
            SourceResult.Failure(FailureReason.NO_PERMISSION, e.message)
        }
    }

    private fun hasPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
        PackageManager.PERMISSION_GRANTED

    private fun queryInstances(fromMillis: Long, toMillis: Long): List<CalendarInstanceRow> {
        val uri = Instances.CONTENT_URI.buildUpon()
            .also { ContentUris.appendId(it, fromMillis) }
            .also { ContentUris.appendId(it, toMillis) }
            .build()
        val rows = mutableListOf<CalendarInstanceRow>()
        // Only calendars the user shows in the calendar app.
        context.contentResolver.query(uri, INSTANCE_PROJECTION, "${Instances.VISIBLE} = 1", null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                rows += CalendarInstanceRow(
                    title = cursor.getString(COLUMN_TITLE),
                    beginMillis = cursor.getLong(COLUMN_BEGIN),
                    endMillis = cursor.getLong(COLUMN_END),
                    isAllDay = cursor.getInt(COLUMN_ALL_DAY) == 1,
                    location = cursor.getString(COLUMN_LOCATION),
                    calendarName = cursor.getString(COLUMN_CALENDAR_NAME),
                    isCanceled = !cursor.isNull(
                        COLUMN_STATUS,
                    ) && cursor.getInt(COLUMN_STATUS) == Events.STATUS_CANCELED,
                    isDeclinedByMe = !cursor.isNull(COLUMN_SELF_STATUS) &&
                        cursor.getInt(COLUMN_SELF_STATUS) == Attendees.ATTENDEE_STATUS_DECLINED,
                    calendarId = if (cursor.isNull(COLUMN_CALENDAR_ID)) null else cursor.getLong(COLUMN_CALENDAR_ID),
                )
            }
        }
        return rows
    }

    private fun queryCalendars(): List<CalendarRow> {
        val rows = mutableListOf<CalendarRow>()
        context.contentResolver.query(Calendars.CONTENT_URI, CALENDAR_PROJECTION, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                rows += CalendarRow(
                    id = cursor.getLong(CALENDAR_COLUMN_ID),
                    displayName = cursor.getString(CALENDAR_COLUMN_NAME),
                    accountName = cursor.getString(CALENDAR_COLUMN_ACCOUNT),
                    color = if (cursor.isNull(CALENDAR_COLUMN_COLOR)) null else cursor.getInt(CALENDAR_COLUMN_COLOR),
                    isVisible = cursor.getInt(CALENDAR_COLUMN_VISIBLE) == 1,
                    isSynced = cursor.getInt(CALENDAR_COLUMN_SYNC_EVENTS) == 1,
                )
            }
        }
        return rows
    }

    private companion object {
        val INSTANCE_PROJECTION = arrayOf(
            Instances.TITLE,
            Instances.BEGIN,
            Instances.END,
            Instances.ALL_DAY,
            Instances.EVENT_LOCATION,
            Instances.CALENDAR_DISPLAY_NAME,
            Instances.STATUS,
            Instances.SELF_ATTENDEE_STATUS,
            Instances.CALENDAR_ID,
        )
        const val COLUMN_TITLE = 0
        const val COLUMN_BEGIN = 1
        const val COLUMN_END = 2
        const val COLUMN_ALL_DAY = 3
        const val COLUMN_LOCATION = 4
        const val COLUMN_CALENDAR_NAME = 5
        const val COLUMN_STATUS = 6
        const val COLUMN_SELF_STATUS = 7
        const val COLUMN_CALENDAR_ID = 8

        val CALENDAR_PROJECTION = arrayOf(
            Calendars._ID,
            Calendars.CALENDAR_DISPLAY_NAME,
            Calendars.ACCOUNT_NAME,
            Calendars.CALENDAR_COLOR,
            Calendars.VISIBLE,
            Calendars.SYNC_EVENTS,
        )
        const val CALENDAR_COLUMN_ID = 0
        const val CALENDAR_COLUMN_NAME = 1
        const val CALENDAR_COLUMN_ACCOUNT = 2
        const val CALENDAR_COLUMN_COLOR = 3
        const val CALENDAR_COLUMN_VISIBLE = 4
        const val CALENDAR_COLUMN_SYNC_EVENTS = 5
    }
}
