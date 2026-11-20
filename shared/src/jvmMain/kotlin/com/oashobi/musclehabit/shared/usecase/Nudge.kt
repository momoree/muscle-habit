package com.oashobi.musclehabit.shared.usecase

import com.oashobi.musclehabit.shared.model.ScheduleSlot
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** ホームの見出しと、その下の一言。 */
data class Nudge(val headline: String, val sub: String? = null)

/**
 * 未達時の後押し（フェーズ2）。行かなかったことを責めず、残りを前向きに伝える。
 * 今週中に届かない回数になっても「足りない」とは言わず、行けた分を認める言い方にする。
 */
object NudgeMath {
    private const val DAY_NAMES = "月火水木金土日"

    fun dayName(date: LocalDate): String = DAY_NAMES[date.dayOfWeek.value - 1].toString()

    /** 今週、まだ行ける日数。今日すでに記録した（行った・パスした）なら今日は数えない。 */
    fun daysLeft(p: WeekProgress, today: LocalDate, todayRecorded: Boolean): Int {
        val end = p.weekStart.plusDays(6)
        val all = ChronoUnit.DAYS.between(today, end).toInt() + 1
        return (if (todayRecorded) all - 1 else all).coerceAtLeast(0)
    }

    /** 今週の残りの予定日（今日を含む。今日すでに記録したなら今日は除く）。 */
    fun scheduledLeft(slots: List<ScheduleSlot>, p: WeekProgress, today: LocalDate, todayRecorded: Boolean): List<LocalDate> {
        val days = slots.filter { it.enabled }.map { it.dayOfWeek }.toSet()
        val end = p.weekStart.plusDays(6)
        val start = if (todayRecorded) today.plusDays(1) else today
        return generateSequence(start) { it.plusDays(1) }
            .takeWhile { !it.isAfter(end) }
            .filter { it.dayOfWeek in days }
            .toList()
    }

    fun home(p: WeekProgress, today: LocalDate, todayRecorded: Boolean, slots: List<ScheduleSlot>): Nudge {
        if (p.achieved) {
            return Nudge("今週の目標を達成！", if (p.count > p.goal) "目標より${p.count - p.goal}回多く行けています" else null)
        }
        val left = daysLeft(p, today, todayRecorded)
        val n = p.remaining
        if (n > left) {
            val headline = if (p.count == 0) "今週は休む週にしても大丈夫" else "今週は${p.count}回。行けた分だけ前進です"
            val sub = if (left > 0) "あと${left}日あります。行けたらそれで十分" else "来週また${p.goal}回をめざしましょう"
            return Nudge(headline, sub)
        }
        val headline = if (n == 1) "あと1回で今週の目標達成" else "今週の目標まで、あと${n}回"
        val planned = scheduledLeft(slots, p, today, todayRecorded)
        val sub = when {
            planned.isEmpty() -> "予定外の日に行っても回数に数えます"
            planned.size >= n -> "予定はあと${planned.size}日（${planned.joinToString("・") { dayName(it) }}）"
            else -> "予定はあと${planned.size}日。ほかの日に行っても数えます"
        }
        return Nudge(headline, sub)
    }
}
