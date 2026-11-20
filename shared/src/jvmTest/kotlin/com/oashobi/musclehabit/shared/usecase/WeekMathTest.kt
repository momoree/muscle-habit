package com.oashobi.musclehabit.shared.usecase

import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SUNDAY
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeekMathTest {
    // 2026-11-16 は月曜、2026-11-20 は金曜
    private val fri = LocalDate.of(2026, 11, 20)
    private fun d(m: Int, day: Int) = LocalDate.of(2026, m, day)

    @Test
    fun weekStartMondayAndSunday() {
        assertEquals(d(11, 16), WeekMath.weekStartOf(fri, MONDAY))
        assertEquals(d(11, 15), WeekMath.weekStartOf(fri, SUNDAY))
        assertEquals(d(11, 16), WeekMath.weekStartOf(d(11, 16), MONDAY))
    }

    @Test
    fun countsOnlyThisWeekIncludingOffScheduleDays() {
        val visits = setOf(d(11, 15), d(11, 16), d(11, 18), d(11, 21))
        val p = WeekMath.progress(visits, fri, MONDAY, 3)
        assertEquals(3, p.count) // 16, 18, 21（土曜＝予定外でも数える）
        assertEquals(0, p.remaining)
        assertTrue(p.achieved)
        assertEquals(listOf(true, false, true, false, false, true, false), p.visitedDays)
    }

    @Test
    fun streakCountsPastWeeksAndCurrentIfAchieved() {
        val visits = setOf(
            d(10, 26), d(10, 28), d(10, 30), // 10/26週 達成
            d(11, 2), d(11, 4), d(11, 6), // 11/2週 達成
            d(11, 9), d(11, 11), d(11, 13), // 11/9週 達成
            d(11, 16), d(11, 18), // 今週 2/3
        )
        assertEquals(3, WeekMath.streakWeeks(visits, fri, MONDAY, 3))
        assertEquals(4, WeekMath.streakWeeks(visits + d(11, 20), fri, MONDAY, 3))
    }

    @Test
    fun streakBreaksOnMissedWeek() {
        val visits = setOf(
            d(10, 26), d(10, 28), d(10, 30),
            d(11, 2), // 11/2週 未達
            d(11, 9), d(11, 11), d(11, 13),
        )
        assertEquals(1, WeekMath.streakWeeks(visits, fri, MONDAY, 3))
        assertEquals(0, WeekMath.streakWeeks(emptySet(), fri, MONDAY, 3))
    }
}
