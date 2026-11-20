package com.oashobi.musclehabit.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oashobi.musclehabit.shared.usecase.WorkoutFormat
import com.oashobi.musclehabit.ui.common.AppCard
import com.oashobi.musclehabit.ui.common.jp
import com.oashobi.musclehabit.ui.common.lines
import com.oashobi.musclehabit.ui.common.longJp
import com.oashobi.musclehabit.ui.common.shortJp
import com.oashobi.musclehabit.ui.theme.AppColors
import java.time.format.DateTimeFormatter

private val HM = DateTimeFormatter.ofPattern("H:mm")

@Composable
fun HomeScreen(
    onOpenWorkout: (visitId: String, afterCheckIn: Boolean) -> Unit,
    onRecordBody: () -> Unit,
    onOpenSchedule: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        vm.refreshToday()
        onPauseOrDispose { }
    }
    var confirmCancel by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 読み込み前に「予定なし」などが一瞬出ないようにする
        if (!s.loaded) return@Column
        val p = s.progress
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(s.today.longJp(), fontSize = 14.sp, color = AppColors.SubText)
            val headline = when {
                p == null -> ""
                p.achieved -> "今週の目標を達成！"
                else -> "今週の目標まで、あと${p.remaining}回"
            }
            Text(headline, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }

        if (p != null) WeekCard(p.count, p.goal, p.streakWeeks, p.visitedDays, s.today.dayOfWeek.value, p.weekStart)

        ScheduleCard(s, onOpenSchedule)

        if (s.checkedIn) {
            CheckedInCard(
                onOpenWorkout = { s.todayVisit?.let { onOpenWorkout(it.id, false) } },
                onCancel = { confirmCancel = true },
            )
        } else {
            Button(
                onClick = { vm.checkIn { id -> onOpenWorkout(id, true) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(20.dp), ambientColor = AppColors.Primary, spotColor = AppColors.Primary),
                shape = RoundedCornerShape(20.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 22.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("チェックイン", fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Text("ジムに着いたらタップ", fontSize = 13.sp, color = AppColors.Surface.copy(alpha = 0.9f))
                }
            }
            if (s.passed) {
                Text(
                    "今日はパスにしています。行ったらチェックインすれば記録が変わります",
                    fontSize = 13.sp,
                    color = AppColors.SubText,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            } else if (s.todaySlot != null) {
                TextButton(onClick = vm::pass, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("今日はパス", color = AppColors.SubText)
                }
            }
        }

        val workout = if (s.checkedIn) s.todayWorkout else null
        val showing = workout ?: s.lastWorkout
        if (showing != null) {
            AppCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 16.dp), verticalSpacing = 10) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (workout != null) "今日のトレーニング" else "前回のトレーニング", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(showing.date.shortJp(), fontSize = 12.sp, color = AppColors.SubText)
                }
                showing.lines(s.exercises).forEach { (name, detail) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(name, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text(detail, fontSize = 14.sp, color = AppColors.SubText)
                    }
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val b = s.latestBody
            Text(
                buildAnnotatedString {
                    append("体重 ")
                    withStyle(SpanStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.Text)) {
                        append(if (b != null) WorkoutFormat.weightInput(b.weightKg) + "kg" else "未記録")
                    }
                },
                fontSize = 14.sp,
                color = AppColors.SubText,
            )
            TextButton(onClick = onRecordBody) { Text("＋ 体重を記録", fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("チェックインを取り消す？") },
            text = { Text("今日の「行った」の記録を取り消します。トレーニングの記録は残ります。") },
            confirmButton = {
                TextButton(onClick = { confirmCancel = false; vm.cancelToday() }) { Text("取り消す") }
            },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("やめる") } },
        )
    }
}

@Composable
private fun WeekCard(
    count: Int,
    goal: Int,
    streak: Int,
    visited: List<Boolean>,
    todayDow: Int,
    weekStart: java.time.LocalDate,
) {
    AppCard(verticalSpacing = 16) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("今週の達成", fontSize = 13.sp, color = AppColors.SubText)
                Text(
                    buildAnnotatedString {
                        append(count.toString())
                        withStyle(SpanStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AppColors.SubText)) { append(" / ${goal}回") }
                    },
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 44.sp,
                )
            }
            if (streak > 0) {
                Row(
                    Modifier
                        .background(AppColors.StreakBg, RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Outlined.LocalFireDepartment, null, tint = AppColors.Streak, modifier = Modifier.size(16.dp))
                    Text("${streak}週連続達成中", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AppColors.Streak)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            visited.forEachIndexed { i, v ->
                val date = weekStart.plusDays(i.toLong())
                val isToday = date.dayOfWeek.value == todayDow
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val circle = Modifier.size(36.dp)
                    when {
                        v -> Box(circle.background(AppColors.Primary, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Check, "行った", tint = AppColors.Surface, modifier = Modifier.size(18.dp))
                        }
                        isToday -> Box(circle.border(2.dp, AppColors.Primary, CircleShape))
                        else -> Box(circle.background(AppColors.Neutral, CircleShape))
                    }
                    Text(
                        date.dayOfWeek.jp(),
                        fontSize = 12.sp,
                        color = if (isToday) AppColors.Primary else AppColors.SubText,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduleCard(s: HomeState, onOpenSchedule: () -> Unit) {
    AppCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier
                    .size(44.dp)
                    .background(AppColors.PrimaryLight, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Schedule, null, tint = AppColors.Primary, modifier = Modifier.size(22.dp))
            }
            val slot = s.todaySlot
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                when {
                    slot != null -> {
                        Text("今日の予定", fontSize = 13.sp, color = AppColors.SubText)
                        Text("${slot.time.format(HM)} ジム", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                    s.nextSlotAt != null -> {
                        Text("次の予定", fontSize = 13.sp, color = AppColors.SubText)
                        Text("${s.nextSlotAt.toLocalDate().shortJp()} ${s.nextSlotAt.format(HM)}", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                    else -> {
                        Text("予定はまだありません", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("通う曜日と時刻を決めましょう", fontSize = 13.sp, color = AppColors.SubText)
                    }
                }
            }
            when {
                slot != null && !s.checkedIn && s.todayVisit == null ->
                    Text("${slot.time.minusMinutes(s.leadMinutes.toLong()).format(HM)}に通知", fontSize = 12.sp, color = AppColors.SubText)
                !s.hasSchedule -> TextButton(onClick = onOpenSchedule) { Text("設定する") }
            }
        }
    }
}

@Composable
private fun CheckedInCard(onOpenWorkout: () -> Unit, onCancel: () -> Unit) {
    AppCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .size(44.dp)
                    .background(AppColors.Primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Check, null, tint = AppColors.Surface)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("チェックイン済み", fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                Text(
                    "取り消す",
                    fontSize = 12.sp,
                    color = AppColors.SubText,
                    modifier = Modifier
                        .clickable(onClick = onCancel)
                        .padding(vertical = 2.dp),
                )
            }
            Button(
                onClick = onOpenWorkout,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.PrimaryLight, contentColor = AppColors.PrimaryDark),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
            ) {
                Text("記録する", fontWeight = FontWeight.Bold)
            }
        }
    }
}
