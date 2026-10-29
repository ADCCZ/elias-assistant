package io.github.adccz.elias.data.status

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.adccz.elias.core.android.canPostNotifications
import io.github.adccz.elias.data.schedule.EveningBriefingStarter
import io.github.adccz.elias.domain.status.SystemChecks
import io.github.adccz.elias.domain.status.SystemStatus
import javax.inject.Inject

/** Asks Android what Elias may do. Thin on purpose: the screen decides what to show. */
class AndroidSystemChecks @Inject constructor(@ApplicationContext private val context: Context) : SystemChecks {
    override fun current() = SystemStatus(
        calendar = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED,
        notifications = context.canPostNotifications(),
        // Android 11 and older need no permission for exact alarms.
        exactAlarms = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
        batteryUnrestricted = context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName),
        fullScreen = EveningBriefingStarter.canShowFullScreen(context),
    )
}
