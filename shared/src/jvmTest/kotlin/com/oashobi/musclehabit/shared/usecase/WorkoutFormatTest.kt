package com.oashobi.musclehabit.shared.usecase

import kotlin.test.Test
import kotlin.test.assertEquals

class WorkoutFormatTest {
    @Test
    fun weight() {
        assertEquals("60kg", WorkoutFormat.weight(60f))
        assertEquals("62.5kg", WorkoutFormat.weight(62.5f))
        assertEquals("自重", WorkoutFormat.weight(0f))
        assertEquals("62.5", WorkoutFormat.weightInput(62.5f))
    }

    @Test
    fun sets() {
        assertEquals("60kg × 10・10・8回", WorkoutFormat.sets(listOf(60f to 10, 60f to 10, 60f to 8)))
        assertEquals("60kg×10・62.5kg×8回", WorkoutFormat.sets(listOf(60f to 10, 62.5f to 8)))
        assertEquals("自重 × 12回", WorkoutFormat.sets(listOf(0f to 12)))
    }

    @Test
    fun diff() {
        assertEquals("−1.2kg", WorkoutFormat.weightDiff(-1.2f))
        assertEquals("+0.4kg", WorkoutFormat.weightDiff(0.4f))
        assertEquals("±0kg", WorkoutFormat.weightDiff(0.01f))
    }
}
