package com.oashobi.musclehabit.shared.usecase

import com.oashobi.musclehabit.shared.model.ScheduleSlot
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** 前日の準備リストの通知（フェーズ2）。 */
data class PrepReminder(
    /** 通知を出す時刻（ジムの日の前日の prepTime） */
    val triggerAt: LocalDateTime,
    /** ジムの日 */
    val gymDate: LocalDate,
)

object PrepMath {

    val DEFAULT_TIME: LocalTime = LocalTime.of(21, 0)
    val DEFAULT_ITEMS = listOf("ウェア", "シューズ", "タオル", "水筒")

    /**
     * 次の準備通知。ジムの日の前日 prepTime に出す。通知時刻を過ぎた分は出さない（夜中に遅れて出さない）。
     * @param skipDates すでにチェックイン・パスした日（その日の準備は要らない）
     */
    fun next(
        slots: List<ScheduleSlot>,
        now: LocalDateTime,
        prepTime: LocalTime,
        skipDates: Set<LocalDate> = emptySet(),
    ): PrepReminder? {
        if (slots.none { it.enabled }) return null
        for (offset in 1L..15L) {
            val gymDate = now.toLocalDate().plusDays(offset)
            if (gymDate in skipDates) continue
            if (ReminderMath.todaySlot(slots, gymDate) == null) continue
            val trigger = gymDate.minusDays(1).atTime(prepTime)
            if (trigger.isAfter(now)) return PrepReminder(trigger, gymDate)
        }
        return null
    }

    /**
     * ホームに準備リストを出すなら、そのジムの日を返す。
     * 前日の prepTime から、当日の予定時刻まで出す。
     */
    fun cardDate(slots: List<ScheduleSlot>, now: LocalDateTime, prepTime: LocalTime, todayRecorded: Boolean): LocalDate? {
        val today = now.toLocalDate()
        val todaySlot = ReminderMath.todaySlot(slots, today)
        if (todaySlot != null && !todayRecorded && now.toLocalTime().isBefore(todaySlot.time)) return today
        val tomorrow = today.plusDays(1)
        if (ReminderMath.todaySlot(slots, tomorrow) != null && !now.toLocalTime().isBefore(prepTime)) return tomorrow
        return null
    }
}

object PrepText {
    fun title(): String = "明日はジムの日"
    fun body(items: List<String>): String =
        if (items.isEmpty()) "持ち物をそろえておくと、明日が楽になります" else "${items.joinToString("・")}をそろえておこう"
}
