package com.oashobi.musclehabit.ui.settings

import android.content.Context
import android.net.Uri
import android.os.PowerManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oashobi.musclehabit.calendar.CalendarInfo
import com.oashobi.musclehabit.calendar.CalendarSync
import com.oashobi.musclehabit.data.AppSettings
import com.oashobi.musclehabit.data.BodyRepository
import com.oashobi.musclehabit.data.SettingsRepository
import com.oashobi.musclehabit.data.VisitRepository
import com.oashobi.musclehabit.data.WorkoutRepository
import com.oashobi.musclehabit.data.db.ExerciseEntity
import com.oashobi.musclehabit.domain.CheckInActions
import com.oashobi.musclehabit.notify.AlarmScheduler
import com.oashobi.musclehabit.notify.Notifications
import com.oashobi.musclehabit.shared.model.VisitType
import com.oashobi.musclehabit.sync.SyncRepository
import com.oashobi.musclehabit.sync.SyncResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 通知が届くための条件の状態 */
data class DeviceStatus(
    val notificationsAllowed: Boolean = true,
    val exactAlarmAllowed: Boolean = true,
    val ignoringBatteryOptimizations: Boolean = false,
    val calendarPermission: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val scheduler: AlarmScheduler,
    private val notifications: Notifications,
    private val calendar: CalendarSync,
    private val actions: CheckInActions,
    private val visits: VisitRepository,
    private val body: BodyRepository,
    private val workouts: WorkoutRepository,
    private val sync: SyncRepository,
) : ViewModel() {

    val settingsState: StateFlow<AppSettings> =
        settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val exercises: StateFlow<List<ExerciseEntity>> =
        workouts.observeExercises().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _status = MutableStateFlow(DeviceStatus())
    val status: StateFlow<DeviceStatus> = _status

    /** 連携をオンにするときに選ぶカレンダーの一覧（null なら選択ダイアログを出さない） */
    private val _calendars = MutableStateFlow<List<CalendarInfo>?>(null)
    val calendars: StateFlow<List<CalendarInfo>?> = _calendars

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun refreshStatus() {
        val pm = context.getSystemService(PowerManager::class.java)
        _status.value = DeviceStatus(
            notificationsAllowed = notifications.hasPermission(),
            exactAlarmAllowed = scheduler.canScheduleExact(),
            ignoringBatteryOptimizations = pm.isIgnoringBatteryOptimizations(context.packageName),
            calendarPermission = calendar.hasPermission(),
        )
        // 許可が変わったかもしれないので、次回の通知を登録し直す
        viewModelScope.launch { scheduler.reschedule() }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun setGoal(v: Int) = viewModelScope.launch { settings.setWeeklyGoal(v) }

    fun setLead(v: Int) = viewModelScope.launch {
        settings.setLeadMinutes(v)
        scheduler.reschedule()
    }

    fun setWeekStart(d: DayOfWeek) = viewModelScope.launch { settings.setWeekStart(d) }

    /** 通知を試す（30分後の予定として、いますぐ出す） */
    fun testNotification() = viewModelScope.launch {
        val remaining = actions.weekProgress().remaining
        notifications.showReminder(LocalDateTime.now().plusMinutes(30).withSecond(0).withNano(0), remaining)
        if (!notifications.hasPermission()) _message.value = "通知が許可されていません"
    }

    // ---- 前日の準備リスト ----

    fun setPrepEnabled(v: Boolean) = viewModelScope.launch {
        settings.setPrepEnabled(v)
        scheduler.reschedule()
    }

    fun setPrepTime(v: LocalTime) = viewModelScope.launch {
        settings.setPrepTime(v)
        scheduler.reschedule()
    }

    fun setPrepItems(text: String) = viewModelScope.launch { settings.setPrepItems(text.lines()) }

    // ---- PC と同期 ----

    private val _syncBusy = MutableStateFlow(false)
    val syncBusy: StateFlow<Boolean> = _syncBusy

    /** ペアリング。成功したら true を返してダイアログを閉じる */
    fun pair(host: String, code: String, onDone: (Boolean) -> Unit) = viewModelScope.launch {
        _syncBusy.value = true
        sync.pair(host, code)
            .onSuccess { pc ->
                _message.value = "「$pc」とつながりました。記録を送ります"
                sync.syncNow()
                onDone(true)
            }
            .onFailure { _message.value = it.message ?: "つなげませんでした"; onDone(false) }
        _syncBusy.value = false
    }

    fun syncNow() = viewModelScope.launch {
        _syncBusy.value = true
        _message.value = when (val r = sync.syncNow()) {
            is SyncResult.Ok -> if (r.sent == 0) "PC と同期しました（新しい記録はありません）" else "PC に ${r.sent}件送りました"
            is SyncResult.Failed -> r.message
            SyncResult.NotPaired -> "PC とつながっていません"
        }
        _syncBusy.value = false
    }

    fun unpair() = viewModelScope.launch {
        sync.unpair()
        _message.value = "PC との接続を解除しました"
    }

    // ---- Googleカレンダー連携 ----

    /** 権限があるときに呼ぶ。書き込み先を選ぶダイアログを出す */
    fun startCalendarSetup() = viewModelScope.launch {
        val list = calendar.listCalendars()
        if (list.isEmpty()) {
            _message.value = "書き込めるカレンダーが見つかりません。端末にGoogleアカウントを追加してください"
        } else {
            _calendars.value = list
        }
    }

    fun chooseCalendar(info: CalendarInfo) = viewModelScope.launch {
        _calendars.value = null
        val current = settings.current()
        if (current.calendarEnabled && current.calendarId != info.id) calendar.removeAll()
        settings.setCalendar(true, info.id)
        calendar.syncAll()
        _message.value = "「${info.name}」にジムの予定を登録しました"
    }

    fun cancelCalendarSetup() {
        _calendars.value = null
    }

    fun disableCalendar() = viewModelScope.launch {
        calendar.removeAll()
        settings.setCalendar(false, null)
        _message.value = "カレンダーからジムの予定を消しました"
    }

    // ---- 種目の管理 ----

    fun setHidden(e: ExerciseEntity, hidden: Boolean) = viewModelScope.launch { workouts.setHidden(e, hidden) }

    fun rename(e: ExerciseEntity, name: String) = viewModelScope.launch { workouts.rename(e, name) }

    // ---- データの書き出し ----

    fun export(uri: Uri) = viewModelScope.launch {
        val csv = buildCsv()
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())) // Excel で文字化けしないよう BOM を付ける
                out.write(csv.toByteArray(Charsets.UTF_8))
            }
        }
        _message.value = "書き出しました"
    }

    private suspend fun buildCsv(): String {
        val names = workouts.observeExercises().first().associate { it.id to it.name }
        val history = workouts.observeHistory().first()
        return buildString {
            appendLine("種類,日付,項目,値1,値2")
            visits.getAll().sortedBy { it.date }.forEach { v ->
                appendLine("チェックイン,${v.date},${when (v.type) { VisitType.NORMAL -> "行った"; VisitType.MINIMUM -> "行った（最小目標）"; VisitType.PASS -> "パス" }},,")
            }
            body.getAll().forEach { b ->
                appendLine("体重,${b.date},体重kg/体脂肪%,${b.weightKg},${b.bodyFatPercent ?: ""}")
            }
            history.sortedBy { it.date }.forEach { day ->
                day.items.forEach { item ->
                    val name = (names[item.exerciseId] ?: "").replace(",", "，")
                    if (item.sets.isEmpty()) {
                        appendLine("トレーニング,${day.date},$name,${item.minutes?.let { "${it}分" } ?: ""},")
                    } else {
                        item.sets.forEach { s -> appendLine("トレーニング,${day.date},$name,${s.weightKg}kg,${s.reps}回") }
                    }
                }
            }
        }
    }
}
