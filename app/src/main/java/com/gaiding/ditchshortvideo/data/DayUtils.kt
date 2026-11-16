package com.gaiding.ditchshortvideo.data

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

object DayUtils {
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)

    fun today(): LocalDate = LocalDate.now(ZoneId.systemDefault())

    fun parseDate(value: String?): LocalDate? {
        if (value.isNullOrBlank()) return null
        return runCatching { LocalDate.parse(value) }.getOrNull()
    }

    /** A number that is comfortable in the big ring, e.g. 0, 7, 128. */
    fun bigMinutes(seconds: Long): String {
        if (seconds <= 0L) return "0"
        if (seconds < 60L) return "<1"
        return (seconds / 60L).toString()
    }

    fun minutesRoundedUp(seconds: Long): Long {
        if (seconds <= 0L) return 0L
        return ceil(seconds / 60.0).toLong()
    }

    fun humanDuration(seconds: Long): String {
        val safe = max(0L, seconds)
        if (safe < 60L) {
            return if (safe == 0L) "0 分钟" else "不到 1 分钟"
        }
        val minutes = safe / 60L
        if (minutes < 60L) return "$minutes 分钟"
        val hours = minutes / 60L
        val rest = minutes % 60L
        return if (rest == 0L) "$hours 小时" else "$hours 小时 $rest 分钟"
    }

    fun remainingText(remainingSeconds: Long): String {
        if (remainingSeconds <= 0L) return "额度已用完"
        if (remainingSeconds < 60L) return "还剩不到 1 分钟"
        return "还剩 ${minutesRoundedUp(remainingSeconds)} 分钟"
    }

    fun overText(overSeconds: Long): String {
        if (overSeconds < 60L) return "已超出不到 1 分钟"
        return "已超出 ${overSeconds / 60L} 分钟"
    }

    fun clock(seconds: Long): String {
        val safe = max(0L, seconds)
        val minutes = safe / 60L
        val rest = safe % 60L
        return String.format(Locale.CHINA, "%d:%02d", minutes, rest)
    }

    fun dateLabel(date: LocalDate): String = date.format(dateFormatter)

    fun monthLabel(date: LocalDate): String = "${date.monthValue}月"
}
