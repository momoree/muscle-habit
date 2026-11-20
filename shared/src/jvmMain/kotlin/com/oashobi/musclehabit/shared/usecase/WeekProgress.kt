package com.oashobi.musclehabit.shared.usecase

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** 今週の達成状況。 */
data class WeekProgress(
    val weekStart: LocalDate,
    val count: Int,
    val goal: Int,
    /** 連続で目標を達成した週の数（今週が達成済みなら今週も含む）。 */
    val streakWeeks: Int,
    /** 今週の各日（weekStart から7日分）に行ったかどうか。 */
    val visitedDays: List<Boolean>,
) {
    val remaining: Int get() = (goal - count).coerceAtLeast(0)
    val achieved: Boolean get() = count >= goal
}

/**
 * 週の達成率・連続週数の計算。保存せず、チェックイン記録（行った日の一覧）から毎回計算する。
 * 予定外の曜日に行った日も回数に数える。同じ日に何度チェックインしても1回。
 */
object WeekMath {

    fun weekStartOf(date: LocalDate, firstDay: DayOfWeek): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(firstDay))

    fun countInWeek(visitDates: Set<LocalDate>, weekStart: LocalDate): Int =
        (0L until 7L).count { weekStart.plusDays(it) in visitDates }

    fun streakWeeks(visitDates: Set<LocalDate>, today: LocalDate, firstDay: DayOfWeek, goal: Int): Int {
        if (goal <= 0 || visitDates.isEmpty()) return 0
        val thisWeek = weekStartOf(today, firstDay)
        val earliest = weekStartOf(visitDates.min(), firstDay)
        var streak = if (countInWeek(visitDates, thisWeek) >= goal) 1 else 0
        // 今週はまだ途中なので、未達でも連続は途切れない。先週からさかのぼる。
        var week = thisWeek.minusWeeks(1)
        while (!week.isBefore(earliest) && countInWeek(visitDates, week) >= goal) {
            streak++
            week = week.minusWeeks(1)
        }
        return streak
    }

    fun progress(visitDates: Set<LocalDate>, today: LocalDate, firstDay: DayOfWeek, goal: Int): WeekProgress {
        val start = weekStartOf(today, firstDay)
        return WeekProgress(
            weekStart = start,
            count = countInWeek(visitDates, start),
            goal = goal,
            streakWeeks = streakWeeks(visitDates, today, firstDay, goal),
            visitedDays = (0L until 7L).map { start.plusDays(it) in visitDates },
        )
    }
}
