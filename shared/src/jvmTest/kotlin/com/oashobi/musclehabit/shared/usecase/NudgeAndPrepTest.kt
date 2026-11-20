package com.oashobi.musclehabit.shared.usecase

import com.oashobi.musclehabit.shared.model.ScheduleSlot
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NudgeAndPrepTest {
    private val seven = LocalTime.of(19, 0)
    private val slots = listOf(ScheduleSlot(MONDAY, seven), ScheduleSlot(WEDNESDAY, seven), ScheduleSlot(FRIDAY, seven))
    private fun d(day: Int) = LocalDate.of(2026, 11, day) // 16日=月, 20日=金, 22日=日

    private fun progress(vararg days: Int, today: LocalDate) =
        WeekMath.progress(days.map { d(it) }.toSet(), today, MONDAY, 3)

    @Test
    fun mondayMorningShowsPlannedDays() {
        val n = NudgeMath.home(progress(today = d(16)), d(16), false, slots)
        assertEquals("今週の目標まで、あと3回", n.headline)
        assertEquals("予定はあと3日（月・水・金）", n.sub)
    }

    @Test
    fun oneLeft() {
        val n = NudgeMath.home(progress(16, 18, today = d(20)), d(20), false, slots)
        assertEquals("あと1回で今週の目標達成", n.headline)
        assertEquals("予定はあと1日（金）", n.sub)
    }

    @Test
    fun fewerPlannedDaysThanRemaining() {
        // 木曜、まだ0回。予定は金だけだが、日曜までに3日ある
        val n = NudgeMath.home(progress(today = d(19)), d(19), false, slots)
        assertEquals("今週の目標まで、あと3回", n.headline)
        assertEquals("予定はあと1日。ほかの日に行っても数えます", n.sub)
    }

    @Test
    fun unreachableDoesNotBlame() {
        // 土曜、1回だけ。残り2日で2回は届くので回数を出す
        assertEquals("今週の目標まで、あと2回", NudgeMath.home(progress(16, today = d(21)), d(21), false, slots).headline)
        // 日曜、1回だけ。残り1日で2回は届かない
        val n = NudgeMath.home(progress(16, today = d(22)), d(22), false, slots)
        assertEquals("今週は1回。行けた分だけ前進です", n.headline)
        assertEquals("あと1日あります。行けたらそれで十分", n.sub)
        // 日曜にパス済み
        val passed = NudgeMath.home(progress(16, today = d(22)), d(22), true, slots)
        assertEquals("来週また3回をめざしましょう", passed.sub)
    }

    @Test
    fun achieved() {
        assertEquals("今週の目標を達成！", NudgeMath.home(progress(16, 18, 20, today = d(20)), d(20), true, slots).headline)
    }

    @Test
    fun reminderBodyWhenUnreachable() {
        val gymAt = LocalDateTime.of(2026, 11, 22, 19, 0)
        assertEquals("19:00にジムの予定です。行けた分だけ前進です", ReminderText.body(gymAt, 2, daysLeft = 1))
        assertEquals("19:00にジムの予定です。今週あと1回で達成", ReminderText.body(gymAt, 1, daysLeft = 1))
    }

    @Test
    fun prepIsTheEveningBefore() {
        val r = PrepMath.next(slots, LocalDateTime.of(2026, 11, 20, 12, 0), LocalTime.of(21, 0))!!
        // 金曜の昼 → 次のジムは月曜なので、日曜21時
        assertEquals(LocalDateTime.of(2026, 11, 22, 21, 0), r.triggerAt)
        assertEquals(d(23), r.gymDate)
    }

    @Test
    fun prepSkipsPassedTriggerAndRecordedDays() {
        // 日曜22時（21時を過ぎた）→ 月曜分は出さず、火曜21時（水曜分）
        val r = PrepMath.next(slots, LocalDateTime.of(2026, 11, 22, 22, 0), LocalTime.of(21, 0))!!
        assertEquals(d(25), r.gymDate)
        // 水曜をすでにパスしていれば金曜分
        val r2 = PrepMath.next(slots, LocalDateTime.of(2026, 11, 22, 22, 0), LocalTime.of(21, 0), setOf(d(25)))!!
        assertEquals(d(27), r2.gymDate)
        assertNull(PrepMath.next(emptyList(), LocalDateTime.of(2026, 11, 22, 22, 0), LocalTime.of(21, 0)))
    }

    @Test
    fun prepCard() {
        val nine = LocalTime.of(21, 0)
        // 日曜21時半 → 月曜の準備
        assertEquals(d(23), PrepMath.cardDate(slots, LocalDateTime.of(2026, 11, 22, 21, 30), nine, false))
        // 日曜の昼 → まだ出さない
        assertNull(PrepMath.cardDate(slots, LocalDateTime.of(2026, 11, 22, 12, 0), nine, false))
        // 月曜の昼（予定前、未チェックイン）→ 今日の準備
        assertEquals(d(23), PrepMath.cardDate(slots, LocalDateTime.of(2026, 11, 23, 12, 0), nine, false))
        // 月曜にチェックイン済み → 出さない
        assertNull(PrepMath.cardDate(slots, LocalDateTime.of(2026, 11, 23, 12, 0), nine, true))
        assertTrue(PrepText.body(listOf("ウェア", "タオル")).startsWith("ウェア・タオル"))
    }
}
