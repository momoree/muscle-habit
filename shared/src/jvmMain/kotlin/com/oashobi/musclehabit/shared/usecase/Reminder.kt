package com.oashobi.musclehabit.shared.usecase

import com.oashobi.musclehabit.shared.model.ScheduleSlot
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 次に出す当日通知。 */
data class Reminder(
    /** 通知を出す時刻（予定時刻の leadMinutes 分前）。 */
    val triggerAt: LocalDateTime,
    /** ジムの予定時刻。 */
    val gymAt: LocalDateTime,
)

object ReminderMath {

    /**
     * 次の当日通知を求める。
     * @param skipDates すでにチェックイン済み（またはパス済み）の日。その日の通知は出さない。
     * 通知時刻を過ぎていても予定時刻前なら「今すぐ」通知する（予定を直前に登録した場合など）。
     */
    fun next(
        slots: List<ScheduleSlot>,
        now: LocalDateTime,
        leadMinutes: Int,
        skipDates: Set<LocalDate> = emptySet(),
    ): Reminder? {
        val enabled = slots.filter { it.enabled }
        if (enabled.isEmpty()) return null
        for (offset in 0L..14L) {
            val date = now.toLocalDate().plusDays(offset)
            if (date in skipDates) continue
            val candidates = enabled
                .filter { it.dayOfWeek == date.dayOfWeek }
                .map { date.atTime(it.time) }
                .filter { it.isAfter(now) }
                .sorted()
            val gymAt = candidates.firstOrNull() ?: continue
            val trigger = gymAt.minusMinutes(leadMinutes.toLong())
            return Reminder(triggerAt = if (trigger.isBefore(now)) now else trigger, gymAt = gymAt)
        }
        return null
    }

    /** 今日の予定（なければ null）。 */
    fun todaySlot(slots: List<ScheduleSlot>, today: LocalDate): ScheduleSlot? =
        slots.filter { it.enabled && it.dayOfWeek == today.dayOfWeek }.minByOrNull { it.time }

    /** 今日より後で、いちばん近い予定の日時。 */
    fun nextAfterToday(slots: List<ScheduleSlot>, today: LocalDate): LocalDateTime? {
        val enabled = slots.filter { it.enabled }
        for (offset in 1L..7L) {
            val date = today.plusDays(offset)
            val slot = enabled.filter { it.dayOfWeek == date.dayOfWeek }.minByOrNull { it.time } ?: continue
            return date.atTime(slot.time)
        }
        return null
    }
}

/** 通知の文面。行かなかったことを責めず、前向きな残りを伝える。 */
object ReminderText {
    private val hm = DateTimeFormatter.ofPattern("H:mm")

    fun title(): String = "今日はジムの日"

    /**
     * @param daysLeft 今週、今日を含めてまだ行ける日数。残り回数がこれを超えたら（今週中に届かないなら）
     *   回数は言わずに「行けた分だけ前進」と伝える。
     */
    fun body(gymAt: LocalDateTime, remaining: Int, daysLeft: Int = 7): String {
        val time = gymAt.format(hm)
        return when {
            remaining <= 0 -> "${time}にジムの予定です。今週の目標は達成済み、この調子で"
            remaining > daysLeft -> "${time}にジムの予定です。行けた分だけ前進です"
            remaining == 1 -> "${time}にジムの予定です。今週あと1回で達成"
            else -> "${time}にジムの予定です。今週あと${remaining}回"
        }
    }
}
