package com.gaiding.ditchshortvideo.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A small, deterministic daily-limit plan.
 *
 * The plan only starts after we have seen at least [MIN_DATA_DAYS] days with
 * data. It glides from the recent active-day average to the chosen goal over
 * [HORIZON_DAYS] days. A deterministic ±3 minute wobble keeps the line looking
 * human without changing on every redraw.
 */
object PlanEngine {
    const val MIN_DATA_DAYS = 3
    const val HORIZON_DAYS = 14
    const val MIN_GOAL = 5
    const val MAX_GOAL = 240

    val goalOptions = listOf(15, 30, 45, 60, 90, 120)

    data class PlanState(
        val enabled: Boolean,
        val goalMinutes: Int,
        val startDate: LocalDate?,
        val baselineMinutes: Int,
        val dataDays: Int,
        val todayLimit: Int,
        val dayIndex: Int
    )

    fun recordedDays(usage: UsageStore, lookbackDays: Int = 30): Int =
        usage.recentDays(lookbackDays).count { it.seconds > 0L }

    /**
     * Average minutes on days with data, over the last week. Days without any
     * data are intentionally left out: a day with no short-video use should
     * not inflate the planned limit.
     */
    fun baselineMinutes(usage: UsageStore): Int {
        val active = usage.recentDays(7).filter { it.seconds > 0L }
        if (active.isEmpty()) return 1
        val averageSeconds = active.sumOf { it.seconds }.toDouble() / active.size
        return ceil(averageSeconds / 60.0).toInt().coerceAtLeast(1)
    }

    fun state(prefs: Prefs, usage: UsageStore): PlanState {
        val start = DayUtils.parseDate(prefs.planStartDate)
        val enabled = prefs.planEnabled && start != null
        val goal = prefs.planGoalMinutes.coerceIn(MIN_GOAL, MAX_GOAL)
        val today = DayUtils.today()
        val dayIndex = if (start == null) 0 else {
            ChronoUnit.DAYS.between(start, today).toInt().coerceAtLeast(0)
        }
        val todayLimit = if (enabled && start != null) {
            limitFor(today, goal, prefs.planBaselineMinutes, start)
        } else {
            prefs.dailyLimitMinutes
        }
        return PlanState(
            enabled = enabled,
            goalMinutes = goal,
            startDate = start,
            baselineMinutes = prefs.planBaselineMinutes,
            dataDays = recordedDays(usage),
            todayLimit = todayLimit,
            dayIndex = dayIndex
        )
    }

    /**
     * Creates (or replaces) the plan from today's data. Returns false when
     * there is not enough recorded history yet.
     */
    fun enable(prefs: Prefs, usage: UsageStore, goalMinutes: Int): Boolean {
        if (recordedDays(usage) < MIN_DATA_DAYS) return false
        val goal = goalMinutes.coerceIn(MIN_GOAL, MAX_GOAL)
        prefs.planGoalMinutes = goal
        prefs.planBaselineMinutes = baselineMinutes(usage)
        prefs.planStartDate = DayUtils.today().toString()
        prefs.planEnabled = true
        prefs.planLastAppliedDate = null
        applyToday(prefs, usage)
        return true
    }

    /**
     * Writes today's planned value into the normal daily limit once per day.
     * This is safe to call from Application, Activity and the service.
     */
    fun applyToday(prefs: Prefs, usage: UsageStore): Int {
        val start = DayUtils.parseDate(prefs.planStartDate)
        if (!prefs.planEnabled || start == null) return prefs.dailyLimitMinutes
        val today = DayUtils.today()
        if (prefs.planLastAppliedDate == today.toString()) return prefs.dailyLimitMinutes

        val value = limitFor(
            today,
            prefs.planGoalMinutes.coerceIn(MIN_GOAL, MAX_GOAL),
            prefs.planBaselineMinutes,
            start
        )
        prefs.dailyLimitMinutes = value
        prefs.planLastAppliedDate = today.toString()
        return value
    }

    fun disable(prefs: Prefs) {
        prefs.clearPlan()
    }

    /**
     * Limit curve for any date. Values before the start date simply use the
     * goal, but callers normally only draw from startDate onward.
     */
    fun limitFor(date: LocalDate, goalMinutes: Int, baselineMinutes: Int, start: LocalDate): Int {
        val goal = goalMinutes.coerceIn(MIN_GOAL, MAX_GOAL)
        val elapsed = ChronoUnit.DAYS.between(start, date).toInt()
        if (elapsed < 0) return goal
        val progress = elapsed.coerceAtMost(HORIZON_DAYS).toDouble() / HORIZON_DAYS
        val base = if (baselineMinutes <= goal) {
            goal.toDouble()
        } else {
            baselineMinutes + (goal - baselineMinutes) * progress
        }
        val withNoise = base.roundToInt() + noise(date, goal, start)
        return max(1, withNoise).coerceAtMost(Prefs.MAX_LIMIT)
    }

    private fun noise(date: LocalDate, goal: Int, start: LocalDate): Int {
        var value = date.toEpochDay() * 31L + start.toEpochDay() * 17L + goal * 7L
        value = value xor (value ushr 13)
        value *= 0x5DEECE66DL
        val bucket = ((value % 7L) + 7L) % 7L
        return bucket.toInt() - 3
    }
}
