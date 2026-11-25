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

    /** True once the accessibility service has connected at least once. */
    var guardArmed: Boolean
        get() = sp.getBoolean(KEY_GUARD_ARMED, false)
        set(value) = sp.edit().putBoolean(KEY_GUARD_ARMED, value).apply()

    var planEnabled: Boolean
        get() = sp.getBoolean(KEY_PLAN_ENABLED, false) && planStartDate != null
        set(value) = sp.edit().putBoolean(KEY_PLAN_ENABLED, value).apply()

    var planGoalMinutes: Int
        get() = sp.getInt(KEY_PLAN_GOAL, DEFAULT_LIMIT)
            .coerceIn(PlanEngine.MIN_GOAL, PlanEngine.MAX_GOAL)
        set(value) = sp.edit().putInt(
            KEY_PLAN_GOAL,
            value.coerceIn(PlanEngine.MIN_GOAL, PlanEngine.MAX_GOAL)
        ).apply()

    var planStartDate: String?
        get() = sp.getString(KEY_PLAN_START, null)
        set(value) {
            val editor = sp.edit()
            if (value.isNullOrBlank()) editor.remove(KEY_PLAN_START) else editor.putString(KEY_PLAN_START, value)
            editor.apply()
        }

    var planBaselineMinutes: Int
        get() = sp.getInt(KEY_PLAN_BASELINE, DEFAULT_LIMIT).coerceAtLeast(1)
        set(value) = sp.edit().putInt(KEY_PLAN_BASELINE, value.coerceAtLeast(1)).apply()

    var planLastAppliedDate: String?
        get() = sp.getString(KEY_PLAN_LAST_APPLIED, null)
        set(value) {
            val editor = sp.edit()
            if (value.isNullOrBlank()) editor.remove(KEY_PLAN_LAST_APPLIED) else editor.putString(KEY_PLAN_LAST_APPLIED, value)
            editor.apply()
        }

    fun clearPlan() {
        sp.edit()
            .remove(KEY_PLAN_ENABLED)
            .remove(KEY_PLAN_GOAL)
            .remove(KEY_PLAN_START)
            .remove(KEY_PLAN_BASELINE)
            .remove(KEY_PLAN_LAST_APPLIED)
            .apply()
    }

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
        private const val KEY_GUARD_ARMED = "guard_armed"
        private const val KEY_PLAN_ENABLED = "plan_enabled"
        private const val KEY_PLAN_GOAL = "plan_goal_minutes"
        private const val KEY_PLAN_START = "plan_start_date"
        private const val KEY_PLAN_BASELINE = "plan_baseline_minutes"
        private const val KEY_PLAN_LAST_APPLIED = "plan_last_applied_date"
    }
}
