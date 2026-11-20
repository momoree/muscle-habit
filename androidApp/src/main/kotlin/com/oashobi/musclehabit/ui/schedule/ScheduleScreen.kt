@file:OptIn(ExperimentalMaterial3Api::class)

package com.oashobi.musclehabit.ui.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.oashobi.musclehabit.calendar.CalendarSync
import com.oashobi.musclehabit.data.DEFAULT_GYM_TIME
import com.oashobi.musclehabit.data.ScheduleRepository
import com.oashobi.musclehabit.data.SettingsRepository
import com.oashobi.musclehabit.notify.AlarmScheduler
import com.oashobi.musclehabit.shared.usecase.Reminder
import com.oashobi.musclehabit.ui.common.AppCard
import com.oashobi.musclehabit.ui.common.jp
import com.oashobi.musclehabit.ui.common.shortJp
import com.oashobi.musclehabit.ui.theme.AppColors
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DayRow(val day: DayOfWeek, val enabled: Boolean, val time: LocalTime)

data class ScheduleUi(
    val days: List<DayRow> = emptyList(),
    val weeklyGoal: Int = 3,
    val calendarEnabled: Boolean = false,
    val next: Reminder? = null,
)

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val repo: ScheduleRepository,
    settings: SettingsRepository,
    private val calendar: CalendarSync,
    private val scheduler: AlarmScheduler,
) : ViewModel() {

    private val next = MutableStateFlow<Reminder?>(null)

    val ui: StateFlow<ScheduleUi> = combine(repo.observe(), settings.settings, next) { list, s, n ->
        val byDay = list.associateBy { it.dayOfWeek }
        // 週の始まりの曜日から並べる
        val order = (0L until 7L).map { s.weekStart.plus(it) }
        ScheduleUi(
            days = order.map { d -> byDay[d.value].let { DayRow(d, it?.enabled == true, it?.time ?: DEFAULT_GYM_TIME) } },
            weeklyGoal = s.weeklyGoal,
            calendarEnabled = s.calendarEnabled,
            next = n,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScheduleUi())

    init {
        viewModelScope.launch { next.value = scheduler.nextReminder() }
    }

    fun setEnabled(day: DayOfWeek, enabled: Boolean) = change { repo.set(day, enabled) }

    fun setTime(day: DayOfWeek, time: LocalTime) = change { repo.set(day, enabled = true, time = time) }

    private fun change(block: suspend () -> com.oashobi.musclehabit.data.db.ScheduleEntity) {
        viewModelScope.launch {
            val entity = block()
            calendar.syncOne(entity)
            scheduler.reschedule()
            next.value = scheduler.nextReminder()
        }
    }
}

private val HM = DateTimeFormatter.ofPattern("H:mm")

@Composable
fun ScheduleScreen(vm: ScheduleViewModel = hiltViewModel()) {
    val u by vm.ui.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<DayRow?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("予定", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        val count = u.days.count { it.enabled }
        Text(
            "通う曜日と時刻を選んでください。いま週${count}日（目標は週${u.weeklyGoal}回）",
            fontSize = 14.sp,
            color = AppColors.SubText,
        )

        AppCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalSpacing = 0) {
            u.days.forEachIndexed { i, row ->
                if (i > 0) HorizontalDivider(color = AppColors.Neutral)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${row.day.jp()}曜日",
                        fontSize = 16.sp,
                        fontWeight = if (row.enabled) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { editing = row }, enabled = row.enabled) {
                        Text(if (row.enabled) row.time.format(HM) else "—", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Switch(checked = row.enabled, onCheckedChange = { vm.setEnabled(row.day, it) })
                }
            }
        }

        u.next?.let { n ->
            Text(
                "次の通知：${n.triggerAt.toLocalDate().shortJp()} ${n.triggerAt.format(HM)}（${n.gymAt.format(HM)}の予定）",
                fontSize = 13.sp,
                color = AppColors.SubText,
            )
        }
        Text(
            if (u.calendarEnabled) "Googleカレンダーにも「ジム」の繰り返し予定として登録しています" else "設定でGoogleカレンダー連携をオンにすると、カレンダーにも登録されます",
            fontSize = 13.sp,
            color = AppColors.SubText,
        )
    }

    editing?.let { row ->
        TimeDialog(
            initial = row.time,
            title = "${row.day.jp()}曜日の時刻",
            onDismiss = { editing = null },
            onSave = { vm.setTime(row.day, it); editing = null },
        )
    }
}

@Composable
private fun TimeDialog(initial: LocalTime, title: String, onDismiss: () -> Unit, onSave: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onSave(LocalTime.of(state.hour, state.minute)) }) { Text("決定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("やめる") } },
    )
}
