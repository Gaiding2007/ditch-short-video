package com.gaiding.ditchshortvideo.system

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import com.gaiding.ditchshortvideo.monitor.ShortVideoAccessibilityService

/**
 * Small wrappers around Android settings. No AndroidX, no hidden APIs.
 */
object SystemStatus {
    fun accessibilityEnabled(context: Context): Boolean {
        val expected = ComponentName(context, ShortVideoAccessibilityService::class.java)
        val raw = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(raw)
        for (item in splitter) {
            val component = ComponentName.unflattenFromString(item) ?: continue
            if (component == expected) return true
        }
        return false
    }

    fun notificationsAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    }

    fun batteryUnrestricted(context: Context): Boolean {
        val power = context.getSystemService(PowerManager::class.java)
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun openAccessibilitySettings(context: Context) {
        open(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    fun openNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        if (!open(context, intent)) openAppDetails(context)
    }

    fun openBatterySettings(context: Context) {
        if (batteryUnrestricted(context)) {
            open(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            return
        }
        val request = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}")
        )
        if (open(context, request)) return
        if (open(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) return
        openAppDetails(context)
    }

    fun openAppDetails(context: Context) {
        open(
            context,
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            )
        )
    }

    fun isInstalled(context: Context, packageName: String): Boolean =
        runCatching { context.packageManager.getPackageInfo(packageName, 0) }.isSuccess

    fun isXiaomi(): Boolean {
        val mark = "${Build.MANUFACTURER} ${Build.BRAND}".lowercase()
        return mark.contains("xiaomi") || mark.contains("redmi") || mark.contains("poco")
    }

    private fun open(context: Context, intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
