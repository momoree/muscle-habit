package com.oashobi.musclehabit.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oashobi.musclehabit.data.db.BodyRecordEntity
import com.oashobi.musclehabit.shared.usecase.WorkoutFormat
import com.oashobi.musclehabit.ui.common.AppCard
import com.oashobi.musclehabit.ui.photos.PhotoEntryCard
import com.oashobi.musclehabit.ui.common.lines
import com.oashobi.musclehabit.ui.common.longJp
import com.oashobi.musclehabit.ui.theme.AppColors
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.layer.point
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.common.shape.CorneredShape
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.floor

@Composable
fun ProgressScreen(
    onRecordBody: () -> Unit,
    onEditWorkout: (visitId: String, date: LocalDate) -> Unit,
    onOpenPhotos: () -> Unit,
    vm: ProgressViewModel = hiltViewModel(),
) {
    val u by vm.ui.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("推移", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Button(onClick = onRecordBody, shape = RoundedCornerShape(12.dp), modifier = Modifier.height(44.dp)) {
                Text("＋ 体重を記録", fontWeight = FontWeight.Bold)
            }
        }

        BodyCard(
            title = "体重",
            unit = "kg",
            u = u,
            value = { it.weightKg },
            onRange = vm::setRange,
        )
        if (u.bodies.any { it.bodyFatPercent != null }) {
            BodyCard(title = "体脂肪率", unit = "%", u = u, value = { it.bodyFatPercent }, onRange = vm::setRange)
        }

        CalendarCard(u, onMove = vm::moveMonth, onSelect = vm::selectDate)

        AppCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Stat("連続達成", "${u.streakWeeks}週", AppColors.Streak, Modifier.weight(1f))
                Stat("${u.month.monthValue}月に通った日", "${u.visitedInMonth.size}日", AppColors.Text, Modifier.weight(1f))
            }
        }

        MonthlyCard(u.monthly, u.monthRange, vm::setMonthRange)

        PhotoEntryCard(onOpen = onOpenPhotos)
    }

    u.selectedDate?.let { date ->
        val workout = u.selectedWorkout
        AlertDialog(
            onDismissRequest = { vm.selectDate(null) },
            title = { Text(date.longJp()) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (u.selectedMinimum) Text("最小目標で達成（着いたら成功の日）", fontSize = 13.sp, color = AppColors.Primary, fontWeight = FontWeight.Bold)
                    when {
                        workout != null -> workout.lines(u.exercises).forEach { (name, detail) ->
                            Row(Modifier.fillMaxWidth()) {
                                Text(name, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                Text(detail, fontSize = 14.sp, color = AppColors.SubText)
                            }
                        }
                        u.selectedVisitId != null -> Text(if (u.selectedMinimum) "トレーニングの記録はありません" else "チェックインのみ（トレーニングの記録はありません）")
                        else -> Text("この日は行っていません")
                    }
                }
            },
            confirmButton = {
                val visitId = u.selectedVisitId
                if (visitId != null) {
                    TextButton(onClick = { vm.selectDate(null); onEditWorkout(visitId, date) }) { Text("記録を編集") }
                }
            },
            dismissButton = { TextButton(onClick = { vm.selectDate(null) }) { Text("閉じる") } },
        )
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = 12.sp, color = AppColors.SubText)
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Black, color = color)
    }
}

@Composable
private fun BodyCard(
    title: String,
    unit: String,
    u: ProgressUi,
    value: (BodyRecordEntity) -> Float?,
    onRange: (Range) -> Unit,
) {
    val points = u.bodies.mapNotNull { r -> value(r)?.let { r.date to it } }
    AppCard(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp), verticalSpacing = 10) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontSize = 13.sp, color = AppColors.SubText)
                val latest = u.latest?.let(value)
                Text(
                    buildAnnotatedString {
                        append(latest?.let(WorkoutFormat::weightInput) ?: "—")
                        withStyle(SpanStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AppColors.SubText)) { append(" $unit") }
                    },
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                )
                if (points.size >= 2) {
                    val diff = points.last().second - points.first().second
                    val text = WorkoutFormat.weightDiff(diff).let { if (unit == "%") it.replace("kg", "%") else it }
                    Text("${u.range.label}で $text", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AppColors.PrimaryDark)
                }
            }
            RangeToggle(u.range, onRange)
        }
        if (points.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(110.dp), contentAlignment = Alignment.Center) {
                Text("この期間の記録はまだありません", fontSize = 13.sp, color = AppColors.SubText)
            }
        } else {
            LineChart(points)
        }
    }
}

@Composable
private fun RangeToggle(current: Range, onRange: (Range) -> Unit) {
    Row(
        Modifier
            .background(AppColors.Neutral, RoundedCornerShape(10.dp))
            .padding(3.dp),
    ) {
        Range.entries.forEach { r ->
            val selected = r == current
            Box(
                Modifier
                    .height(32.dp)
                    .background(if (selected) AppColors.Surface else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable { onRange(r) }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    r.label,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) AppColors.Text else AppColors.SubText,
                )
            }
        }
    }
}

/** 日付ごとの値の折れ線グラフ（Vico）。x は日付（エポック日）。 */
@Composable
private fun LineChart(points: List<Pair<LocalDate, Float>>) {
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(points) {
        producer.runTransaction {
            lineSeries { series(x = points.map { it.first.toEpochDay() }, y = points.map { it.second }) }
        }
    }
    val min = floor(points.minOf { it.second } - 1.0)
    val max = ceil(points.maxOf { it.second } + 1.0)
    // 記録が1件でも見えるよう、各点に丸を付ける
    val dot = rememberShapeComponent(fill = fill(AppColors.Primary), shape = CorneredShape.Pill)
    val line = LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(fill(AppColors.Primary)),
        pointProvider = LineCartesianLayer.PointProvider.single(LineCartesianLayer.point(dot, 8.dp)),
    )
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(line),
                rangeProvider = CartesianLayerRangeProvider.fixed(minY = min, maxY = max),
            ),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(
                valueFormatter = CartesianValueFormatter { _, x, _ ->
                    val d = LocalDate.ofEpochDay(x.toLong())
                    "${d.monthValue}/${d.dayOfMonth}"
                },
            ),
        ),
        modelProducer = producer,
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
    )
}

@Composable
private fun CalendarCard(u: ProgressUi, onMove: (Long) -> Unit, onSelect: (LocalDate) -> Unit) {
    AppCard(contentPadding = PaddingValues(16.dp), verticalSpacing = 10) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMove(-1) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "前の月") }
            Text("${u.month.year}年${u.month.monthValue}月", fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(
                buildAnnotatedString {
                    append("通った日 ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = AppColors.Text)) { append("${u.visitedInMonth.size}日") }
                },
                fontSize = 13.sp,
                color = AppColors.SubText,
            )
            IconButton(onClick = { onMove(1) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "次の月") }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("月", "火", "水", "木", "金", "土", "日").forEach {
                Text(it, fontSize = 11.sp, color = AppColors.SubText, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        val first = u.month.atDay(1)
        val blanks = first.dayOfWeek.value - 1 // 月曜始まり
        val days = u.month.lengthOfMonth()
        val cells = List(blanks) { null } + (1..days).toList()
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 0 until 7) {
                    val day = week.getOrNull(i)
                    Box(Modifier.weight(1f).height(34.dp), contentAlignment = Alignment.Center) {
                        if (day != null) {
                            val date = u.month.atDay(day)
                            val visited = day in u.visitedInMonth
                            val today = date == u.today
                            val shape = RoundedCornerShape(10.dp)
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .then(
                                        when {
                                            visited -> Modifier.background(AppColors.Primary, shape)
                                            today -> Modifier.border(2.dp, AppColors.Primary, shape)
                                            else -> Modifier
                                        },
                                    )
                                    .clickable { onSelect(date) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "$day",
                                    fontSize = 13.sp,
                                    fontWeight = if (visited || today) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        visited -> Color.White
                                        today -> AppColors.PrimaryDark
                                        else -> AppColors.Text
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
