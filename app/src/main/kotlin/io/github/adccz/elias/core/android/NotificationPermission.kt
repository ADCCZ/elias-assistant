package io.github.adccz.elias.core.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Whether the app may show ordinary notifications (the sabbath reminder). Android 13 and newer ask the user first,
 * and the user can switch notifications off in the settings on any version.
 */
fun Context.canPostNotifications(): Boolean {
    val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    return granted && NotificationManagerCompat.from(this).areNotificationsEnabled()
}
