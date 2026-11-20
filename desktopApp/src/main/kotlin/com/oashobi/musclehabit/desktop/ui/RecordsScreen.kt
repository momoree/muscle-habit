package com.oashobi.musclehabit.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oashobi.musclehabit.desktop.PcBody
import com.oashobi.musclehabit.desktop.Snapshot
import com.oashobi.musclehabit.shared.model.VisitType
import com.oashobi.musclehabit.shared.usecase.NudgeMath
import com.oashobi.musclehabit.shared.usecase.ReminderMath
import com.oashobi.musclehabit.shared.usecase.WeekMath
import com.oashobi.musclehabit.shared.usecase.WorkoutFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.floor

private val DAY_NAMES = listOf("月", "火", "水", "木", "金", "土", "日")
private val MD = DateTimeFormatter.ofPattern("M/d")
private val HM = DateTimeFormatter.ofPattern("H:mm")

/** 記録の閲覧：今週の達成率・連続達成週数・通った日のカレンダー・体重と体脂肪率のグラフ */
@Composable
fun RecordsScreen(snap: Snapshot, today: LocalDate) {
    val s = snap.settings
    val p = WeekMath.progress(snap.visitDates, today, s.weekStart, s.weeklyGoal)
    val nudge = NudgeMath.home(p, today, today in snap.recordedDates, snap.slots)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${today.monthValue}月${today.dayOfMonth}日（${DAY_NAMES[today.dayOfWeek.value - 1]}）", fontSize = 14.sp, color = AppColors.SubText)
            Text(nudge.headline, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            nudge.sub?.let { Text(it, fontSize = 14.sp, color = AppColors.SubText) }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            WeekCard(p.count, p.goal, p.streakWeeks, p.visitedDays, p.weekStart, today, Modifier.weight(1.4f))
            StatsCard(snap, today, Modifier.weight(1f))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            CalendarCard(snap, today, Modifier.weight(1f))
            Column(Modifier.weight(1.4f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                ChartCard("体重", "kg", snap.bodies.map { it.date to it.weightKg }, today)
                if (snap.bodies.any { it.bodyFatPercent != null }) {
                    ChartCard("体脂肪率", "%", snap.bodies.mapNotNull { b: PcBody -> b.bodyFatPercent?.let { b.date to it } }, today)
                }
            }
        }
    }
}

@Composable
private fun WeekCard(
    count: Int,
    goal: Int,
    streak: Int,
    visited: List<Boolean>,
    weekStart: LocalDate,
    today: LocalDate,
    modifier: Modifier,
) {
    Card(modifier, spacing = 18) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Column {
                Text("今週の達成", fontSize = 13.sp, color = AppColors.SubText)
                Text(
                    buildAnnotatedString {
                        append(count.toString())
                        withStyle(SpanStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = AppColors.SubText)) { append(" / ${goal}回") }
                    },
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            if (streak > 0) {
                Row(
                    Modifier
                        .background(AppColors.StreakBg, RoundedCornerShape(999.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
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
                val isToday = date == today
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val circle = Modifier.size(40.dp)
                    when {
                        v -> Box(circle.background(AppColors.Primary, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Check, "行った", tint = AppColors.Surface, modifier = Modifier.size(20.dp))
                        }
                        isToday -> Box(circle.border(2.dp, AppColors.Primary, CircleShape))
                        else -> Box(circle.background(AppColors.Neutral, CircleShape))
                    }
                    Text(
                        DAY_NAMES[date.dayOfWeek.value - 1],
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
private fun StatsCard(snap: Snapshot, today: LocalDate, modifier: Modifier) {
    val month = YearMonth.from(today)
    val inMonth = snap.visitDates.count { YearMonth.from(it) == month }
    val total = snap.visitDates.size
    val todaySlot = ReminderMath.todaySlot(snap.slots, today)
    val next = ReminderMath.nextAfterToday(snap.slots, today)
    Card(modifier, spacing = 14) {
        Stat("今月通った日", "${inMonth}日")
        Stat("これまでに通った日", "${total}日")
        Stat(
            if (todaySlot != null) "今日の予定" else "次の予定",
            when {
                todaySlot != null -> "${todaySlot.time.format(HM)} ジム"
                next != null -> "${next.format(MD)}（${DAY_NAMES[next.dayOfWeek.value - 1]}）${next.format(HM)}"
                else -> "なし"
            },
        )
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(label, fontSize = 13.sp, color = AppColors.SubText)
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CalendarCard(snap: Snapshot, today: LocalDate, modifier: Modifier) {
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    val byDate = snap.visits.groupBy { it.date }
    Card(modifier, spacing = 8) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "前の月") }
            Text("${month.year}年${month.monthValue}月", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            val count = snap.visitDates.count { YearMonth.from(it) == month }
            Text("通った日 ${count}日", fontSize = 13.sp, color = AppColors.SubText)
            IconButton(onClick = { month = month.plusMonths(1) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "次の月") }
        }
        Row(Modifier.fillMaxWidth()) {
            DAY_NAMES.forEach { Text(it, fontSize = 12.sp, color = AppColors.SubText, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
        val first = month.atDay(1)
        val lead = first.dayOfWeek.value - 1 // 月曜始まり
        val cells = lead + month.lengthOfMonth()
        val rows = ceil(cells / 7.0).toInt()
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val day = r * 7 + c - lead + 1
                    Box(Modifier.weight(1f).aspectRatio(1.3f).padding(3.dp), contentAlignment = Alignment.Center) {
                        if (day in 1..month.lengthOfMonth()) {
                            val date = month.atDay(day)
                            val types = byDate[date]?.map { it.type }.orEmpty()
                            val went = types.any { it.countsAsVisit }
                            val minimum = types.contains(VisitType.MINIMUM)
                            val bg = when {
                                went && !minimum -> Modifier.background(AppColors.Primary, RoundedCornerShape(10.dp))
                                went -> Modifier.background(AppColors.PrimaryLight, RoundedCornerShape(10.dp))
                                date == today -> Modifier.border(2.dp, AppColors.Primary, RoundedCornerShape(10.dp))
                                else -> Modifier
                            }
                            Box(Modifier.fillMaxSize().then(bg), contentAlignment = Alignment.Center) {
                                Text(
                                    "$day",
                                    fontSize = 14.sp,
                                    fontWeight = if (went) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        went && !minimum -> AppColors.Surface
                                        went -> AppColors.PrimaryDark
                                        else -> AppColors.Text
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Legend(AppColors.Primary, "行った")
            Legend(AppColors.PrimaryLight, "最小目標（着いたら成功）")
        }
    }
}

@Composable
private fun Legend(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(12.dp).background(color, RoundedCornerShape(3.dp)))
        Text(label, fontSize = 12.sp, color = AppColors.SubText)
    }
}

private enum class Range(val label: String, val days: Long?) { M1("1か月", 31), M3("3か月", 92), Y1("1年", 366), ALL("全期間", null) }

/** 体重・体脂肪率の推移。大きな画面を生かして広く描く。 */
@Composable
private fun ChartCard(title: String, unit: String, points: List<Pair<LocalDate, Float>>, today: LocalDate) {
    var range by remember { mutableStateOf(Range.M3) }
    val shown = points.filter { (d, _) -> range.days == null || !d.isBefore(today.minusDays(range.days!!)) }.sortedBy { it.first }
    Card(Modifier.fillMaxWidth(), spacing = 10) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 13.sp, color = AppColors.SubText)
                val latest = points.maxByOrNull { it.first }?.second
                Text(
                    buildAnnotatedString {
                        append(latest?.let { WorkoutFormat.weightInput(it) } ?: "—")
                        withStyle(SpanStyle(fontSize = 16.sp, color = AppColors.SubText)) { append(" $unit") }
                    },
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            Row(
                Modifier
                    .background(AppColors.Neutral, RoundedCornerShape(12.dp))
                    .padding(4.dp),
            ) {
                Range.entries.forEach { r ->
                    Text(
                        r.label,
                        fontSize = 13.sp,
                        fontWeight = if (r == range) FontWeight.Bold else FontWeight.Normal,
                        color = if (r == range) AppColors.Text else AppColors.SubText,
                        modifier = Modifier
                            .then(if (r == range) Modifier.background(AppColors.Surface, RoundedCornerShape(9.dp)) else Modifier)
                            .clickable { range = r }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                Text("この期間の記録はありません", color = AppColors.SubText)
            }
        } else {
            LineChart(shown, Modifier.fillMaxWidth().height(240.dp))
        }
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun LineChart(points: List<Pair<LocalDate, Float>>, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 11.sp, color = AppColors.SubText)
    Canvas(modifier) {
        val minV = floor(points.minOf { it.second } - 0.5f).toFloat()
        val maxV = ceil(points.maxOf { it.second } + 0.5f).toFloat()
        val x0 = points.first().first.toEpochDay().toFloat()
        val x1 = points.last().first.toEpochDay().toFloat().let { if (it == x0) x0 + 1 else it }
        val left = 44.dp.toPx()
        val bottom = size.height - 24.dp.toPx()
        val top = 8.dp.toPx()
        val right = size.width - 12.dp.toPx()
        fun px(d: LocalDate) = if (points.size == 1) (left + right) / 2 else left + (d.toEpochDay() - x0) / (x1 - x0) * (right - left)
        fun py(v: Float) = bottom - (v - minV) / (maxV - minV) * (bottom - top)

        // 目盛り（4本）
        val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
        for (i in 0..3) {
            val v = minV + (maxV - minV) * i / 3
            val y = py(v)
            drawLine(AppColors.Neutral, Offset(left, y), Offset(right, y), strokeWidth = 2f, pathEffect = dash)
            val t = measurer.measure("%.1f".format(v), labelStyle)
            drawText(t, topLeft = Offset(left - t.size.width - 8.dp.toPx(), y - t.size.height / 2))
        }
        // 日付（最初・真ん中・最後）
        listOf(points.first(), points[points.size / 2], points.last()).distinctBy { it.first }.forEach { (d, _) ->
            val t = measurer.measure(d.format(MD), labelStyle)
            drawText(t, topLeft = Offset((px(d) - t.size.width / 2).coerceIn(left, right - t.size.width), bottom + 6.dp.toPx()))
        }
        // 線と点
        val path = Path()
        points.forEachIndexed { i, (d, v) -> if (i == 0) path.moveTo(px(d), py(v)) else path.lineTo(px(d), py(v)) }
        drawPath(path, AppColors.Primary, style = Stroke(width = 3.dp.toPx()))
        if (points.size <= 60) points.forEach { (d, v) -> drawCircle(AppColors.Primary, 4.dp.toPx(), Offset(px(d), py(v))) }
    }
}
