package com.gaiding.ditchshortvideo.monitor

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import com.gaiding.ditchshortvideo.MainActivity
import com.gaiding.ditchshortvideo.R
import com.gaiding.ditchshortvideo.system.SystemStatus

/**
 * Best-effort ColorOS survival: a foreground service keeps the process warm,
 * and a 15-minute alarm posts a recovery notification if the system cuts it.
 *
 * Android does not allow an app to re-enable its own accessibility service.
 * If the OS force-stops the app, the user still has to open it once.
 */
object Guardian {
    const val CHANNEL_SERVICE = "guard_service_v1"
    const val CHANNEL_ALERT = "guard_alert_v1"
    const val SERVICE_ID = 2100
    const val ALERT_ID = 2101

    private const val INTERVAL_MS = 15 * 60 * 1000L
    private const val REQUEST_GUARD = 4101

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_SERVICE) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_SERVICE,
                    context.getString(R.string.guard_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = context.getString(R.string.guard_channel_description)
                    setShowBadge(false)
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
            )
        }
        if (manager.getNotificationChannel(CHANNEL_ALERT) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ALERT,
                    context.getString(R.string.guard_alert_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = context.getString(R.string.guard_alert_channel_description)
                    setShowBadge(true)
                    enableVibration(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
            )
        }
    }

    fun schedule(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_GUARD,
            Intent(context, GuardReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = SystemClock.elapsedRealtime() + INTERVAL_MS
        runCatching {
            manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pending)
        }
    }

    fun notifyDisconnected(context: Context) {
        if (!SystemStatus.notificationsAllowed(context)) return
        ensureChannels(context)
        val open = PendingIntent.getActivity(
            context,
            4201,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val fix = PendingIntent.getActivity(
            context,
            4202,
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = Notification.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.guard_disconnected_title))
            .setContentText(context.getString(R.string.guard_disconnected_body))
            .setStyle(
                Notification.BigTextStyle()
                    .bigText(context.getString(R.string.guard_disconnected_body))
            )
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_ERROR)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
        if (Build.VERSION.SDK_INT >= 23) {
            builder.addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, R.drawable.ic_notification),
                    context.getString(R.string.guard_action),
                    fix
                ).build()
            )
        }
        runCatching {
            context.getSystemService(NotificationManager::class.java)
                ?.notify(ALERT_ID, builder.build())
        }
    }
}
