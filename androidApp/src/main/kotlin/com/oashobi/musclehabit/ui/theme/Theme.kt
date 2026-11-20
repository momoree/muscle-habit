package com.oashobi.musclehabit.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 画面イメージ（ジム習慣化アプリ 画面イメージ）の色
object AppColors {
    val Primary = Color(0xFF0E7C6B)
    val PrimaryDark = Color(0xFF0A5E51)
    val PrimaryLight = Color(0xFFE3F3EF)
    val Background = Color(0xFFF5F6F8)
    val Surface = Color(0xFFFFFFFF)
    val Text = Color(0xFF16181D)
    val SubText = Color(0xFF5B616B)
    val Neutral = Color(0xFFEEF0F3)
    val Border = Color(0xFFE3E6EA)
    val StreakBg = Color(0xFFFDF3E7)
    val Streak = Color(0xFF9A4A08)
    val Danger = Color(0xFFB3261E)
}

private val colors = lightColorScheme(
    primary = AppColors.Primary,
    onPrimary = Color.White,
    primaryContainer = AppColors.PrimaryLight,
    onPrimaryContainer = AppColors.PrimaryDark,
    secondaryContainer = AppColors.PrimaryLight,
    onSecondaryContainer = AppColors.PrimaryDark,
    tertiaryContainer = AppColors.StreakBg,
    onTertiaryContainer = AppColors.Streak,
    background = AppColors.Background,
    onBackground = AppColors.Text,
    surface = AppColors.Surface,
    onSurface = AppColors.Text,
    surfaceVariant = AppColors.Neutral,
    onSurfaceVariant = AppColors.SubText,
    surfaceContainerLow = AppColors.Surface,
    surfaceContainer = AppColors.Surface,
    surfaceContainerHigh = AppColors.Surface,
    surfaceContainerHighest = AppColors.Neutral,
    outline = AppColors.Border,
    outlineVariant = AppColors.Border,
    error = AppColors.Danger,
)

@Composable
fun MuscleHabitTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
}
