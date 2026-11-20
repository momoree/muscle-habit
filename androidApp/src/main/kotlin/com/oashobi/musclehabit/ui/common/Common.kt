package com.oashobi.musclehabit.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.oashobi.musclehabit.data.DayWorkout
import com.oashobi.musclehabit.data.LoggedExercise
import com.oashobi.musclehabit.data.db.ExerciseEntity
import com.oashobi.musclehabit.shared.model.ExerciseCategory
import com.oashobi.musclehabit.shared.usecase.WorkoutFormat
import com.oashobi.musclehabit.ui.theme.AppColors
import java.time.DayOfWeek
import java.time.LocalDate

/** 白い角丸カード（画面イメージのカード） */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    verticalSpacing: Int = 12,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(20.dp), clip = false)
            .clip(RoundedCornerShape(20.dp))
            .background(AppColors.Surface)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing.dp),
        content = content,
    )
}

fun DayOfWeek.jp(): String = when (this) {
    DayOfWeek.MONDAY -> "月"
    DayOfWeek.TUESDAY -> "火"
    DayOfWeek.WEDNESDAY -> "水"
    DayOfWeek.THURSDAY -> "木"
    DayOfWeek.FRIDAY -> "金"
    DayOfWeek.SATURDAY -> "土"
    DayOfWeek.SUNDAY -> "日"
}

/** 11月20日（金） */
fun LocalDate.longJp(): String = "${monthValue}月${dayOfMonth}日（${dayOfWeek.jp()}）"

/** 11/20（金） */
fun LocalDate.shortJp(): String = "$monthValue/$dayOfMonth（${dayOfWeek.jp()}）"

/** 1種目分の記録を「60kg × 10・10・8回」「20分」の形にする */
fun LoggedExercise.detail(exercise: ExerciseEntity?): String = when {
    sets.isNotEmpty() -> WorkoutFormat.sets(sets.map { it.weightKg to it.reps })
    minutes != null -> WorkoutFormat.minutes(minutes)
    exercise?.category == ExerciseCategory.CARDIO -> "時間の記録なし"
    else -> "種目のみ"
}

/** その日のトレーニングを「種目名・内容」の行にする */
fun DayWorkout.lines(exercises: Map<String, ExerciseEntity>): List<Pair<String, String>> =
    items.map { item ->
        val ex = exercises[item.exerciseId]
        (ex?.name ?: "（削除した種目）") to item.detail(ex)
    }
