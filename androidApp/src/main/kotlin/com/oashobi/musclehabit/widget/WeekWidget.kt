package com.oashobi.musclehabit.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.oashobi.musclehabit.MainActivity
import com.oashobi.musclehabit.data.ScheduleRepository
import com.oashobi.musclehabit.data.SettingsRepository
import com.oashobi.musclehabit.data.VisitRepository
import com.oashobi.musclehabit.data.db.AppDatabase
import com.oashobi.musclehabit.di.AppScope
import com.oashobi.musclehabit.domain.CheckInActions
import com.oashobi.musclehabit.shared.usecase.ReminderMath
import com.oashobi.musclehabit.shared.usecase.WeekMath
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/*
 * フェーズ3：ホーム画面ウィジェット（Jetpack Glance）。
 * 今週の達成状況を出し、アプリを開かずにチェックインできる。
 */

private val Primary = Color(0xFF0E7C6B)
private val PrimaryLight = Color(0xFFE3F3EF)
private val Neutral = Color(0xFFEEF0F3)
private val TextColor = Color(0xFF16181D)
private val SubText = Color(0xFF5B616B)
private val DAY = "月火水木金土日"
private val HM = DateTimeFormatter.ofPattern("H:mm")

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun visits(): VisitRepository
    fun schedules(): ScheduleRepository
    fun settings(): SettingsRepository
    fun actions(): CheckInActions
}

private fun Context.widgetEntryPoint() = EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)

/** ウィジェットに出す内容 */
private data class WidgetState(
    val count: Int,
    val goal: Int,
    val visited: List<Boolean>,
    val dayLabels: List<String>,
    val todayIndex: Int,
    val checkedIn: Boolean,
    val passed: Boolean,
    val plan: String,
)

class WeekWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = load(context)
        provideContent { Content(state) }
    }

    private suspend fun load(context: Context): WidgetState {
        val ep = context.widgetEntryPoint()
        val today = LocalDate.now()
        val s = ep.settings().current()
        val visits = ep.visits().getAll()
        val dates = visits.filter { it.type.countsAsVisit }.map { it.date }.toSet()
        val p = WeekMath.progress(dates, today, s.weekStart, s.weeklyGoal)
        val todayVisit = visits.firstOrNull { it.date == today }
        val slots = ep.schedules().slots()
        val todaySlot = ReminderMath.todaySlot(slots, today)
        val next = ReminderMath.nextAfterToday(slots, today)
        return WidgetState(
            count = p.count,
            goal = p.goal,
            visited = p.visitedDays,
            dayLabels = (0L until 7L).map { DAY[p.weekStart.plusDays(it).dayOfWeek.value - 1].toString() },
            todayIndex = (today.toEpochDay() - p.weekStart.toEpochDay()).toInt(),
            checkedIn = todayVisit?.type?.countsAsVisit == true,
            passed = todayVisit != null && todayVisit.type.countsAsVisit.not(),
            plan = when {
                todaySlot != null -> "今日 ${todaySlot.time.format(HM)}"
                next != null -> "次 ${next.monthValue}/${next.dayOfMonth} ${next.format(HM)}"
                else -> "予定なし"
            },
        )
    }

    @Composable
    private fun Content(s: WidgetState) {
        Column(
            GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Color.White))
                .cornerRadius(20.dp)
                .padding(12.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${s.count}",
                    style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = ColorProvider(TextColor)),
                )
                Text(
                    " / ${s.goal}回",
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ColorProvider(SubText)),
                )
                Spacer(GlanceModifier.defaultWeight())
                Text(s.plan, style = TextStyle(fontSize = 12.sp, color = ColorProvider(SubText)))
            }
            Spacer(GlanceModifier.height(6.dp))
            Row(GlanceModifier.fillMaxWidth()) {
                s.visited.forEachIndexed { i, v ->
                    Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            GlanceModifier
                                .size(18.dp)
                                .cornerRadius(9.dp)
                                .background(ColorProvider(if (v) Primary else if (i == s.todayIndex) PrimaryLight else Neutral)),
                        ) {}
                        Text(
                            s.dayLabels[i],
                            style = TextStyle(
                                fontSize = 10.sp,
                                color = ColorProvider(if (i == s.todayIndex) Primary else SubText),
                                fontWeight = if (i == s.todayIndex) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                            ),
                        )
                    }
                }
            }
            Spacer(GlanceModifier.height(8.dp))
            if (s.checkedIn) {
                Box(
                    GlanceModifier.fillMaxWidth().height(40.dp).cornerRadius(12.dp).background(ColorProvider(PrimaryLight)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✓ チェックイン済み", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ColorProvider(Primary)))
                }
            } else {
                Box(
                    GlanceModifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .cornerRadius(12.dp)
                        .background(ColorProvider(Primary))
                        .clickable(actionRunCallback<CheckInCallback>()),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (s.passed) "やっぱり行った" else "チェックイン",
                        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ColorProvider(Color.White)),
                    )
                }
            }
        }
    }
}

/** ウィジェットの「チェックイン」。アプリを開かずに記録する */
class CheckInCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        context.widgetEntryPoint().actions().checkIn()
        WeekWidget().updateAll(context)
    }
}

class WeekWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeekWidget()
}

/** 記録や設定が変わったらウィジェットを描き直す（アプリ起動時に start する） */
@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    @AppScope private val scope: CoroutineScope,
) {
    @OptIn(FlowPreview::class)
    fun start() {
        scope.launch {
            combine(db.visitDao().observeAll(), db.scheduleDao().observeAll(), settings.settings) { v, s, st ->
                Triple(v, s, st.weeklyGoal to st.weekStart)
            }
                .debounce(500)
                .collect { runCatching { WeekWidget().updateAll(context) } }
        }
    }
}
