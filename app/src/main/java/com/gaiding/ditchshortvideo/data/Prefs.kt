package com.gaiding.ditchshortvideo.data

import android.content.Context
import android.content.SharedPreferences
import com.gaiding.ditchshortvideo.R

class Prefs(context: Context) {
    private val appContext = context.applicationContext
    private val sp: SharedPreferences =
        appContext.getSharedPreferences("ditch_settings", Context.MODE_PRIVATE)

    var dailyLimitMinutes: Int
        get() = sp.getInt(KEY_LIMIT, DEFAULT_LIMIT).coerceIn(MIN_LIMIT, MAX_LIMIT)
        set(value) {
            sp.edit().putInt(KEY_LIMIT, value.coerceIn(MIN_LIMIT, MAX_LIMIT)).apply()
        }

    val monitoredPackages: Set<String>
        get() {
            val stored = sp.getStringSet(KEY_APPS, null) ?: return MonitoredApps.defaultPackages()
            return stored.filterTo(LinkedHashSet()) { MonitoredApps.isKnown(it) }
        }

    fun setMonitoredPackages(packages: Set<String>) {
        sp.edit().putStringSet(KEY_APPS, packages.toHashSet()).apply()
    }

    fun isMonitored(packageName: String?): Boolean =
        packageName != null && monitoredPackages.contains(packageName)

    var notificationsEnabled: Boolean
        get() = sp.getBoolean(KEY_NOTIFICATIONS, true)
        set(value) = sp.edit().putBoolean(KEY_NOTIFICATIONS, value).apply()

    var reminderTitle: String
        get() = sp.getString(KEY_TITLE, null)?.takeIf { it.isNotBlank() } ?: defaultTitle
        set(value) = sp.edit().putString(KEY_TITLE, value).apply()

    var reminderBody: String
        get() = sp.getString(KEY_BODY, null)?.takeIf { it.isNotBlank() } ?: defaultBody
        set(value) = sp.edit().putString(KEY_BODY, value).apply()

    var lastReminderDate: String?
        get() = sp.getString(KEY_LAST_REMINDER, null)
        set(value) = sp.edit().putString(KEY_LAST_REMINDER, value).apply()

    var onboardingSeen: Boolean
        get() = sp.getBoolean(KEY_ONBOARDING, false)
        set(value) = sp.edit().putBoolean(KEY_ONBOARDING, value).apply()

    var focusParamExperimental: Boolean
        get() = sp.getBoolean(KEY_FOCUS_PARAM, false)
        set(value) = sp.edit().putBoolean(KEY_FOCUS_PARAM, value).apply()

    val defaultTitle: String
        get() = appContext.getString(R.string.default_reminder_title)

    val defaultBody: String
        get() = appContext.getString(R.string.default_reminder_body)

    fun renderedTitle(usedMinutes: Long, limitMinutes: Long): String =
        render(reminderTitle, usedMinutes, limitMinutes)

    fun renderedBody(usedMinutes: Long, limitMinutes: Long): String =
        render(reminderBody, usedMinutes, limitMinutes)

    fun resetReminderText() {
        sp.edit().remove(KEY_TITLE).remove(KEY_BODY).apply()
    }

    private fun render(template: String, usedMinutes: Long, limitMinutes: Long): String =
        template
            .replace("{used}", usedMinutes.toString())
            .replace("{limit}", limitMinutes.toString())
            .trim()

    companion object {
        const val DEFAULT_LIMIT = 30
        const val MIN_LIMIT = 1
        const val MAX_LIMIT = 1440

        private const val KEY_LIMIT = "daily_limit_minutes"
        private const val KEY_APPS = "monitored_packages"
        private const val KEY_NOTIFICATIONS = "notifications_enabled"
        private const val KEY_TITLE = "reminder_title"
        private const val KEY_BODY = "reminder_body"
        private const val KEY_LAST_REMINDER = "last_reminder_date"
        private const val KEY_ONBOARDING = "onboarding_seen"
        private const val KEY_FOCUS_PARAM = "focus_param_experimental"
    }
}
