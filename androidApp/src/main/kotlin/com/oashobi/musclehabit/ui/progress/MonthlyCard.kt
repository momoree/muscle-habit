package com.oashobi.musclehabit.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oashobi.musclehabit.shared.usecase.MonthSummary
import com.oashobi.musclehabit.shared.usecase.WorkoutFormat
import com.oashobi.musclehabit.ui.common.AppCard
import com.oashobi.musclehabit.ui.theme.AppColors
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberEnd
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.point
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.Axis
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.shape.CorneredShape
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/** 体重の線の色（棒の緑と見分けやすいオレンジ） */
private val WeightColor = Color(0xFFE07B1F)

/** フェーズ3：月ごとの通った日数（棒）と体重（線）を重ねて見る */
@Composable
fun MonthlyCard(monthly: List<MonthSummary>, range: MonthRange, onRange: (MonthRange) -> Unit) {
    AppCard(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp), verticalSpacing = 10) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("通った日と体重", fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Row(
                Modifier
                    .background(AppColors.Neutral, RoundedCornerShape(10.dp))
                    .padding(3.dp),
            ) {
                MonthRange.entries.forEach { r ->
                    val selected = r == range
                    Box(
                        Modifier
                            .height(30.dp)
                            .background(if (selected) AppColors.Surface else Color.Transparent, RoundedCornerShape(8.dp))
                            .clickable { onRange(r) }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(r.label, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = if (selected) AppColors.Text else AppColors.SubText)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Legend(AppColors.Primary, "通った日（左の目盛り）")
            Legend(WeightColor, "体重 kg（右）")
        }
        if (monthly.all { it.visits == 0 && it.weightKg == null }) {
            Box(Modifier.fillMaxWidth().height(110.dp), contentAlignment = Alignment.Center) {
                Text("記録がたまると、ここに月ごとの変化が出ます", fontSize = 13.sp, color = AppColors.SubText)
            }
        } else if (monthly.any { it.weightKg != null }) {
            ColumnsAndLine(monthly)
        } else {
            ColumnsOnly(monthly)
        }
        // 直近の月から並べる
        Column {
            monthly.reversed().filter { it.visits > 0 || it.weightKg != null }.take(range.months).forEachIndexed { i, m ->
                if (i > 0) HorizontalDivider(color = AppColors.Neutral)
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${m.month.year % 100}年${m.month.monthValue}月", fontSize = 13.sp, color = AppColors.SubText, modifier = Modifier.width(76.dp))
                    Text("${m.visits}日", fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(56.dp), textAlign = TextAlign.End)
                    Text(
                        m.weightKg?.let { WorkoutFormat.weightInput(it) + "kg" } ?: "—",
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.End,
                    )
                    Text(
                        m.weightChange?.let(WorkoutFormat::weightDiff) ?: "",
                        fontSize = 13.sp,
                        color = AppColors.PrimaryDark,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(72.dp),
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(3.dp)))
        Text(label, fontSize = 11.sp, color = AppColors.SubText)
    }
}

private fun monthLabel(monthly: List<MonthSummary>) = CartesianValueFormatter { _, x, _ ->
    monthly.getOrNull(x.toInt())?.month?.monthValue?.let { "${it}月" } ?: ""
}

@Composable
private fun columnLayer(monthly: List<MonthSummary>) = rememberColumnCartesianLayer(
    columnProvider = ColumnCartesianLayer.ColumnProvider.series(
        rememberLineComponent(fill = fill(AppColors.Primary), thickness = 14.dp, shape = CorneredShape.rounded(topLeftPercent = 30, topRightPercent = 30)),
    ),
    rangeProvider = CartesianLayerRangeProvider.fixed(minY = 0.0, maxY = max(4, monthly.maxOf { it.visits } + 1).toDouble()),
    verticalAxisPosition = Axis.Position.Vertical.Start,
)

@Composable
private fun ColumnsAndLine(monthly: List<MonthSummary>) {
    val producer = remember { CartesianChartModelProducer() }
    val withWeight = monthly.withIndex().filter { it.value.weightKg != null }
    LaunchedEffect(monthly) {
        producer.runTransaction {
            columnSeries { series(x = monthly.indices.toList(), y = monthly.map { it.visits }) }
            lineSeries { series(x = withWeight.map { it.index }, y = withWeight.map { it.value.weightKg!! }) }
        }
    }
    val weights = withWeight.map { it.value.weightKg!! }
    val dot = rememberShapeComponent(fill = fill(WeightColor), shape = CorneredShape.Pill)
    val line = LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(fill(WeightColor)),
        pointProvider = LineCartesianLayer.PointProvider.single(LineCartesianLayer.point(dot, 7.dp)),
    )
    CartesianChartHost(
        chart = rememberCartesianChart(
            columnLayer(monthly),
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(line),
                rangeProvider = CartesianLayerRangeProvider.fixed(minY = floor(weights.min() - 1.0), maxY = ceil(weights.max() + 1.0)),
                verticalAxisPosition = Axis.Position.Vertical.End,
            ),
            startAxis = VerticalAxis.rememberStart(),
            endAxis = VerticalAxis.rememberEnd(),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = monthLabel(monthly)),
        ),
        modelProducer = producer,
        modifier = Modifier.fillMaxWidth().height(180.dp),
    )
}

@Composable
private fun ColumnsOnly(monthly: List<MonthSummary>) {
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(monthly) {
        producer.runTransaction { columnSeries { series(x = monthly.indices.toList(), y = monthly.map { it.visits }) } }
    }
    CartesianChartHost(
        chart = rememberCartesianChart(
            columnLayer(monthly),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = monthLabel(monthly)),
        ),
        modelProducer = producer,
        modifier = Modifier.fillMaxWidth().height(180.dp),
    )
}
