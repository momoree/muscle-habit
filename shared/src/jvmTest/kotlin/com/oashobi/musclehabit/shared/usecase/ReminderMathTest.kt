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

class ReminderMathTest {
    private val seven = LocalTime.of(19, 0)
    private val slots = listOf(
        ScheduleSlot(MONDAY, seven),
        ScheduleSlot(WEDNESDAY, seven),
        ScheduleSlot(FRIDAY, seven),
    )
    private fun at(day: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, day, h, m) // 20日=金

    @Test
    fun todayBeforeTrigger() {
        val r = ReminderMath.next(slots, at(20, 12), 30)!!
        assertEquals(at(20, 18, 30), r.triggerAt)
        assertEquals(at(20, 19), r.gymAt)
    }

    @Test
    fun betweenTriggerAndGymNotifiesNow() {
        val now = at(20, 18, 45)
        val r = ReminderMath.next(slots, now, 30)!!
        assertEquals(now, r.triggerAt)
    }

    @Test
    fun afterGymTimeGoesToNextScheduledDay() {
        val r = ReminderMath.next(slots, at(20, 19, 30), 30)!!
        assertEquals(at(23, 18, 30), r.triggerAt) // 次の月曜
    }

    @Test
    fun skipsCheckedInDay() {
        val r = ReminderMath.next(slots, at(20, 9), 30, setOf(LocalDate.of(2026, 11, 20)))!!
        assertEquals(at(23, 19), r.gymAt)
    }

    @Test
    fun noEnabledSlots() {
        assertNull(ReminderMath.next(slots.map { it.copy(enabled = false) }, at(20, 9), 30))
    }

    @Test
    fun todayAndNextSlot() {
        val fri = LocalDate.of(2026, 11, 20)
        assertEquals(FRIDAY, ReminderMath.todaySlot(slots, fri)?.dayOfWeek)
        assertEquals(at(23, 19), ReminderMath.nextAfterToday(slots, fri))
    }

    @Test
    fun text() {
        assertEquals("19:00にジムの予定です。今週あと2回", ReminderText.body(at(20, 19), 2))
        assertEquals("19:00にジムの予定です。今週あと1回で達成", ReminderText.body(at(20, 19), 1))
    }
}
