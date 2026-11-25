package com.gaiding.ditchshortvideo.data

import android.content.Context
import android.net.Uri
import com.gaiding.ditchshortvideo.BuildConfig
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Human-readable local backup. No network, no account; the user picks the
 * destination through the system file picker.
 */
object BackupManager {
    private const val SCHEMA = 1
    private const val APP = "ditch-short-video"
    private const val MAX_DAY_SECONDS = 86_400L

    data class ImportResult(val days: Int, val totalSeconds: Long)

    fun suggestedName(): String = "ditch-short-video-backup-${DayUtils.today()}.json"

    fun export(context: Context, prefs: Prefs, usage: UsageStore, uri: Uri): Boolean {
        val root = JSONObject()
        root.put("schema", SCHEMA)
        root.put("app", APP)
        root.put("version", BuildConfig.VERSION_NAME)
        root.put("exportedAt", Instant.now().toString())

        val settings = JSONObject()
        settings.put("dailyLimitMinutes", prefs.dailyLimitMinutes)
        val packageArray = org.json.JSONArray()
        prefs.monitoredPackages.forEach { packageArray.put(it) }
        settings.put("monitoredPackages", packageArray)
        settings.put("notificationsEnabled", prefs.notificationsEnabled)
        settings.put("reminderTitle", prefs.reminderTitle)
        settings.put("reminderBody", prefs.reminderBody)
        settings.put("planEnabled", prefs.planEnabled)
        settings.put("planGoalMinutes", prefs.planGoalMinutes)
        settings.put("planStartDate", prefs.planStartDate ?: JSONObject.NULL)
        settings.put("planBaselineMinutes", prefs.planBaselineMinutes)
        root.put("settings", settings)

        val records = JSONObject()
        usage.snapshot().toSortedMap().forEach { (date, seconds) ->
            records.put(date.toString(), seconds)
        }
        root.put("usage", records)

        return try {
            context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use {
                it.write(root.toString(2))
            } != null
        } catch (_: Exception) {
            false
        }
    }

    fun import(context: Context, prefs: Prefs, usage: UsageStore, uri: Uri): ImportResult {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("无法读取文件")
        val root = try {
            JSONObject(text)
        } catch (_: Exception) {
            error("备份文件内容不完整或已损坏")
        }
        require(root.optInt("schema", -1) == SCHEMA) { "不是本 App 的备份文件" }
        require(root.optString("app") == APP) { "备份文件不属于扔掉短视频" }

        val settings = root.optJSONObject("settings") ?: JSONObject()
        prefs.dailyLimitMinutes = settings.optInt("dailyLimitMinutes", prefs.dailyLimitMinutes)

        val packages = mutableSetOf<String>()
        settings.optJSONArray("monitoredPackages")?.let { array ->
            for (i in 0 until array.length()) {
                val name = array.optString(i)
                if (MonitoredApps.isKnown(name)) packages.add(name)
            }
        }
        prefs.setMonitoredPackages(
            if (packages.isEmpty()) MonitoredApps.defaultPackages() else packages
        )
        prefs.notificationsEnabled = settings.optBoolean("notificationsEnabled", true)

        val title = settings.optString("reminderTitle", "")
        val body = settings.optString("reminderBody", "")
        if (title.isBlank() || body.isBlank()) prefs.resetReminderText() else {
            prefs.reminderTitle = title
            prefs.reminderBody = body
        }

        val today = DayUtils.today()
        val records = mutableMapOf<LocalDate, Long>()
        root.optJSONObject("usage")?.let { objectValue ->
            objectValue.keys().forEach { key ->
                val date = try {
                    LocalDate.parse(key)
                } catch (_: DateTimeParseException) {
                    null
                }
                if (date != null && !date.isAfter(today.plusDays(1))) {
                    val seconds = objectValue.optLong(key, 0L).coerceIn(0L, MAX_DAY_SECONDS)
                    if (seconds > 0L) records[date] = seconds
                }
            }
        }
        usage.replaceAll(records)

        prefs.clearPlan()
        val planEnabled = settings.optBoolean("planEnabled", false)
        val planStart = if (settings.isNull("planStartDate")) {
            null
        } else {
            DayUtils.parseDate(settings.optString("planStartDate"))
        }
        if (planEnabled && planStart != null) {
            prefs.planGoalMinutes = settings.optInt(
                "planGoalMinutes",
                PlanEngine.goalOptions.first()
            )
            prefs.planBaselineMinutes = settings.optInt("planBaselineMinutes", 1)
            prefs.planStartDate = planStart.toString()
            prefs.planEnabled = true
            prefs.planLastAppliedDate = null
            PlanEngine.applyToday(prefs, usage)
        }

        prefs.lastReminderDate = null
        prefs.onboardingSeen = true
        return ImportResult(records.size, records.values.sum())
    }
}
