package com.gaiding.ditchshortvideo.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import com.gaiding.ditchshortvideo.MainActivity
import com.gaiding.ditchshortvideo.R
import com.gaiding.ditchshortvideo.data.Prefs
import com.gaiding.ditchshortvideo.data.UsageStore
import java.util.concurrent.TimeUnit

object ReminderNotifier {
    const val CHANNEL_ID = "short_video_limit_v1"
    const val REMINDER_ID = 1001
    const val TEST_ID = 1002
    private const val DISMISS_ACTION = "com.gaiding.ditchshortvideo.action.DISMISS_REMINDER"
    private const val TIMEOUT_MINUTES = 30L

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            enableVibration(true)
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    fun sendLimitReminder(
        context: Context,
        prefs: Prefs,
        usedSeconds: Long,
        limitMinutes: Long
    ): Boolean {
        ensureChannel(context)
        if (!canPost(context)) return false

        val usedMinutes = (usedSeconds / 60L).coerceAtLeast(1L)
        val title = prefs.renderedTitle(usedMinutes, limitMinutes)
        val body = prefs.renderedBody(usedMinutes, limitMinutes)
        val notification = build(
            context = context,
            title = title,
            body = body,
            id = REMINDER_ID,
            test = false
        )
        return post(context, REMINDER_ID, notification)
    }

    fun sendTest(context: Context, prefs: Prefs): Boolean {
        ensureChannel(context)
        if (!canPost(context)) return false

        val usage = UsageStore(context)
        val usedMinutes = (usage.todaySeconds() / 60L).coerceAtLeast(1L)
        val limit = prefs.dailyLimitMinutes.toLong()
        val title = "测试 · " + prefs.renderedTitle(usedMinutes, limit)
        val body = prefs.renderedBody(usedMinutes, limit)
        val notification = build(
            context = context,
            title = title,
            body = body,
            id = TEST_ID,
            test = true
        )
        return post(context, TEST_ID, notification)
    }

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    }

    fun cancelReminder(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(REMINDER_ID)
    }

    fun cancelTest(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(TEST_ID)
    }

    fun cancelAll(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(REMINDER_ID)
        manager.cancel(TEST_ID)
    }

    private fun build(
        context: Context,
        title: String,
        body: String,
        id: Int,
        test: Boolean
    ): Notification {
        val openApp = PendingIntent.getActivity(
            context,
            10,
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val dismiss = PendingIntent.getBroadcast(
            context,
            11,
            Intent(context, ReminderActionReceiver::class.java).setAction(DISMISS_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(openApp)
            .setAutoCancel(false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setTimeoutAfter(TimeUnit.MINUTES.toMillis(if (test) 5L else TIMEOUT_MINUTES))
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, R.drawable.ic_notification),
                    "关闭提醒",
                    dismiss
                ).build()
            )

        if (Build.VERSION.SDK_INT >= 36) {
            builder.setShortCriticalText("额度已用完")
        }

        val notification = builder.build()
        if (Build.VERSION.SDK_INT >= 36) {
            notification.extras.putBoolean("android.requestPromotedOngoing", true)
        }
        if (!test || Prefs(context).focusParamExperimental) {
            XiaomiIsland.attachExperimental(context, notification, title, body)
        }
        return notification
    }

    private fun post(context: Context, id: Int, notification: Notification): Boolean =
        runCatching {
            context.getSystemService(NotificationManager::class.java).notify(id, notification)
            true
        }.getOrDefault(false)

    fun dismissAction(): String = DISMISS_ACTION
}
