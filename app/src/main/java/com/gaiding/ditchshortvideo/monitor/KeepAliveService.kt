package com.gaiding.ditchshortvideo.monitor

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import com.gaiding.ditchshortvideo.MainActivity
import com.gaiding.ditchshortvideo.R
import com.gaiding.ditchshortvideo.system.SystemStatus

/**
 * A low-priority foreground service whose only job is to keep the page
 * detector's process alive on OEM ROMs that clean background apps aggressively.
 *
 * It does not read the screen and does not try to enable accessibility itself;
 * Android forbids that. It only keeps the process warm and reports state.
 */
class KeepAliveService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            updateNotification()
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        running = true
        Guardian.ensureChannels(this)
        try {
            startForegroundCompat(buildNotification())
        } catch (_: Exception) {
            running = false
            stopSelf()
            return
        }
        Guardian.schedule(this)
        handler.post(ticker)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        updateNotification()
        Guardian.schedule(this)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Guardian.schedule(this)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacks(ticker)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                Guardian.SERVICE_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(Guardian.SERVICE_ID, notification)
        }
    }

    private fun updateNotification() {
        runCatching {
            startForegroundCompat(buildNotification())
        }
    }

    private fun buildNotification(): Notification {
        val enabled = SystemStatus.accessibilityEnabled(this)
        val connected = MonitorRuntime.state.connected
        val title = when {
            !enabled -> getString(R.string.guard_disconnected_title)
            !connected -> "页面识别等待连接"
            else -> getString(R.string.guard_title)
        }
        val text = when {
            !enabled -> getString(R.string.guard_disconnected_body)
            !connected -> "系统已允许，但服务还没连上；点此检查。"
            else -> getString(R.string.guard_text)
        }
        val open = PendingIntent.getActivity(
            this,
            4301,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = Notification.Builder(this, Guardian.CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
        if (!enabled || !connected) {
            val fix = PendingIntent.getActivity(
                this,
                4302,
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_notification),
                    getString(R.string.guard_action),
                    fix
                ).build()
            )
        }
        return builder.build()
    }

    companion object {
        private const val TICK_MS = 30_000L

        @Volatile
        var running: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, KeepAliveService::class.java)) }
        }
    }
}
