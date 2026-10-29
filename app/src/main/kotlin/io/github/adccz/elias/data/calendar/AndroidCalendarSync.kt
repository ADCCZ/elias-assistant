package io.github.adccz.elias.data.calendar

import android.Manifest
import android.accounts.Account
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.adccz.elias.core.coroutines.IoDispatcher
import io.github.adccz.elias.domain.calendar.CalendarSync
import io.github.adccz.elias.domain.calendar.SyncRequest
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** One account that owns calendars on the phone, as the calendar provider names it. */
data class SyncAccount(val name: String, val type: String)

/** Which accounts are worth asking to sync. Kept apart from Android so it can be unit-tested. */
object SyncAccounts {
    /** Every online account once. Calendars kept only on the phone (`LOCAL`) have nothing to download. */
    fun of(rows: List<Pair<String?, String?>>): List<SyncAccount> = rows
        .mapNotNull { (name, type) ->
            val cleanName = name?.trim()?.ifEmpty { null } ?: return@mapNotNull null
            val cleanType = type?.trim()?.ifEmpty { null } ?: return@mapNotNull null
            // CalendarContract.ACCOUNT_TYPE_LOCAL
            if (cleanType == "LOCAL") null else SyncAccount(cleanName, cleanType)
        }
        .distinct()
}

/**
 * Asks Android to sync the calendars of every online account now ([ContentResolver.requestSync], a public API that
 * calendar apps use for "refresh"). It only reads which accounts own calendars; it never changes any sync setting.
 */
class AndroidCalendarSync @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : CalendarSync {
    override suspend fun syncNow(): SyncRequest = withContext(ioDispatcher) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return@withContext SyncRequest.Failed
        }
        try {
            val accounts = SyncAccounts.of(queryAccounts())
            if (accounts.isEmpty()) return@withContext SyncRequest.NothingToSync
            val extras = Bundle().apply {
                // A sync the user asked for: run it now, even if the account would sync later on its own.
                putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
                putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
            }
            accounts.forEach {
                ContentResolver.requestSync(Account(it.name, it.type), CalendarContract.AUTHORITY, extras)
            }
            SyncRequest.Sent(accounts.size)
        } catch (e: SecurityException) {
            SyncRequest.Failed
        } catch (e: IllegalArgumentException) {
            SyncRequest.Failed
        }
    }

    private fun queryAccounts(): List<Pair<String?, String?>> {
        val rows = mutableListOf<Pair<String?, String?>>()
        context.contentResolver.query(
            Calendars.CONTENT_URI,
            arrayOf(Calendars.ACCOUNT_NAME, Calendars.ACCOUNT_TYPE),
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) rows += cursor.getString(0) to cursor.getString(1)
        }
        return rows
    }
}
