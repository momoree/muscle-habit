package com.oashobi.musclehabit.shared.usecase

import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MonthlyMathTest {
    private fun d(m: Int, day: Int) = LocalDate.of(2026, m, day)

    @Test
    fun countsVisitsAndLastWeightPerMonth() {
        val visits = setOf(d(9, 1), d(9, 3), d(10, 5), d(11, 2), d(11, 4), d(11, 6))
        val bodies = listOf(
            d(8, 30) to 71.0f, // 範囲より前
            d(9, 2) to 70.5f,
            d(9, 28) to 70.0f, // 9月の最後
            d(11, 10) to 69.2f, // 10月は記録なし
        )
        val s = MonthlyMath.summaries(visits, bodies, d(11, 20), 3)
        assertEquals(listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 10), YearMonth.of(2026, 11)), s.map { it.month })
        assertEquals(listOf(2, 1, 3), s.map { it.visits })
        assertEquals(listOf(70.0f, null, 69.2f), s.map { it.weightKg })
        // 9月は8月の 71.0 から、11月は記録のない10月をとばして9月の 70.0 から比べる
        assertEquals(-1.0f, s[0].weightChange!!, 0.001f)
        assertNull(s[1].weightChange)
        assertEquals(-0.8f, s[2].weightChange!!, 0.001f)
    }

    @Test
    fun emptyData() {
        val s = MonthlyMath.summaries(emptySet(), emptyList(), d(1, 15), 6)
        assertEquals(6, s.size)
        assertEquals(YearMonth.of(2025, 8), s.first().month)
        assertEquals(0, s.sumOf { it.visits })
        assertNull(s.last().weightChange)
    }
}
