package com.gaiding.ditchshortvideo.data

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate

class UsageStore(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("usage_records", Context.MODE_PRIVATE)

    fun secondsFor(date: LocalDate): Long =
        sp.getLong(dayKey(date), 0L).coerceAtLeast(0L)

    fun todaySeconds(): Long = secondsFor(DayUtils.today())

    fun addSeconds(date: LocalDate, seconds: Long) {
        if (seconds <= 0L) return
        val current = secondsFor(date)
        sp.edit().putLong(dayKey(date), current + seconds).apply()
        pruneIfNeeded(date)
    }

    fun recentDays(dayCount: Int): List<DayUsage> {
        val today = DayUtils.today()
        return (dayCount - 1 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            DayUsage(date, secondsFor(date))
        }
    }

    fun recentMap(dayCount: Int): Map<LocalDate, Long> =
        recentDays(dayCount).associate { it.date to it.seconds }

    fun totalBetween(start: LocalDate, endInclusive: LocalDate): Long {
        if (endInclusive.isBefore(start)) return 0L
        var total = 0L
        var date = start
        while (!date.isAfter(endInclusive)) {
            total += secondsFor(date)
            date = date.plusDays(1)
        }
        return total
    }

    fun clearAll() {
        sp.edit().clear().apply()
    }

    private fun pruneIfNeeded(today: LocalDate) {
        val lastPrune = sp.getString(KEY_LAST_PRUNE, null)
        if (lastPrune == today.toString()) return

        val cutoff = today.minusDays(RETENTION_DAYS)
        val editor = sp.edit()
        sp.all.keys
            .filter { it.startsWith(DAY_PREFIX) }
            .forEach { key ->
                val date = DayUtils.parseDate(key.substring(DAY_PREFIX.length))
                if (date != null && date.isBefore(cutoff)) editor.remove(key)
            }
        editor.putString(KEY_LAST_PRUNE, today.toString()).apply()
    }

    private fun dayKey(date: LocalDate): String = DAY_PREFIX + date

    data class DayUsage(val date: LocalDate, val seconds: Long)

    companion object {
        private const val DAY_PREFIX = "d_"
        private const val KEY_LAST_PRUNE = "last_prune"
        private const val RETENTION_DAYS = 400L
    }
}
