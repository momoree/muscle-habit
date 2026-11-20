package com.oashobi.musclehabit.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** スマホアプリと同じ色 */
object AppColors {
    val Primary = Color(0xFF0E7C6B)
    val PrimaryDark = Color(0xFF0A5E51)
    val PrimaryLight = Color(0xFFE3F3EF)
    val Background = Color(0xFFF5F6F8)
    val Surface = Color.White
    val Text = Color(0xFF16181D)
    val SubText = Color(0xFF5B616B)
    val Neutral = Color(0xFFEEF0F3)
    val StreakBg = Color(0xFFFDF3E7)
    val Streak = Color(0xFF9A4A08)
    val Danger = Color(0xFFC62828)
}

@Composable
fun MuscleHabitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = AppColors.Primary,
            onPrimary = Color.White,
            primaryContainer = AppColors.PrimaryLight,
            onPrimaryContainer = AppColors.PrimaryDark,
            secondaryContainer = AppColors.PrimaryLight,
            onSecondaryContainer = AppColors.PrimaryDark,
            background = AppColors.Background,
            surface = AppColors.Surface,
            onSurface = AppColors.Text,
            onSurfaceVariant = AppColors.SubText,
            error = AppColors.Danger,
        ),
        content = content,
    )
}

@Composable
fun Card(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(20.dp),
    spacing: Int = 12,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(AppColors.Surface)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(spacing.dp),
        content = content,
    )
}
