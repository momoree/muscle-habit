package com.oashobi.musclehabit.ui.settings

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oashobi.musclehabit.data.db.ExerciseEntity
import com.oashobi.musclehabit.ui.common.AppCard
import com.oashobi.musclehabit.ui.theme.AppColors
import com.oashobi.musclehabit.ui.workout.NameInputDialog
import java.time.DayOfWeek
import java.time.LocalDate

private val LEAD_OPTIONS = listOf(0, 15, 30, 45, 60, 90, 120)

@SuppressLint("BatteryLife")
@Composable
fun SettingsScreen(onOpenExercises: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.settingsState.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val calendars by vm.calendars.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleResumeEffect(Unit) {
        vm.refreshStatus()
        onPauseOrDispose { }
    }
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            vm.clearMessage()
        }
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshStatus() }
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        vm.refreshStatus()
        if (result.values.all { it }) vm.startCalendarSetup()
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.export(uri)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("設定", fontSize = 24.sp, fontWeight = FontWeight.Bold)

        Section("目標と通知") {
            SettingRow("週の目標回数") {
                OutlinedButton(onClick = { vm.setGoal(s.weeklyGoal - 1) }, enabled = s.weeklyGoal > 1) { Text("−") }
                Text("${s.weeklyGoal}回", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp))
                OutlinedButton(onClick = { vm.setGoal(s.weeklyGoal + 1) }, enabled = s.weeklyGoal < 7) { Text("＋") }
            }
            HorizontalDivider(color = AppColors.Neutral)
            var leadMenu by remember { mutableStateOf(false) }
            SettingRow("通知のタイミング") {
                TextButton(onClick = { leadMenu = true }) {
                    Text(if (s.leadMinutes == 0) "予定の時刻ちょうど" else "予定の${s.leadMinutes}分前", fontWeight = FontWeight.Bold)
                }
                DropdownMenu(expanded = leadMenu, onDismissRequest = { leadMenu = false }) {
                    LEAD_OPTIONS.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(if (m == 0) "予定の時刻ちょうど" else "${m}分前") },
                            onClick = { leadMenu = false; vm.setLead(m) },
                        )
                    }
                }
            }
            HorizontalDivider(color = AppColors.Neutral)
            SettingRow("週の始まり") {
                listOf(DayOfWeek.MONDAY to "月曜", DayOfWeek.SUNDAY to "日曜").forEach { (d, label) ->
                    if (s.weekStart == d) {
                        FilledTonalButton(onClick = {}) { Text(label) }
                    } else {
                        TextButton(onClick = { vm.setWeekStart(d) }) { Text(label, color = AppColors.SubText) }
                    }
                }
            }
        }

        Section("通知が届くように") {
            StatusRow(
                label = "通知の許可",
                ok = status.notificationsAllowed,
                actionLabel = "許可する",
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            )
            HorizontalDivider(color = AppColors.Neutral)
            StatusRow(
                label = "時刻どおりのアラーム",
                ok = status.exactAlarmAllowed,
                actionLabel = "許可する",
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                    }
                },
            )
            HorizontalDivider(color = AppColors.Neutral)
            StatusRow(
                label = "バッテリー最適化の対象外",
                ok = status.ignoringBatteryOptimizations,
                okText = "対象外",
                ngText = "通知が遅れることがあります",
                actionLabel = "対象外にする",
                onAction = {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))
                },
            )
            HorizontalDivider(color = AppColors.Neutral)
            SettingRow("通知を試す") {
                TextButton(onClick = vm::testNotification) { Text("いま出す", fontWeight = FontWeight.Bold) }
            }
        }

        Section("Googleカレンダー連携") {
            SettingRow("ジムの予定をカレンダーに登録") {
                Switch(
                    checked = s.calendarEnabled,
                    onCheckedChange = { on ->
                        if (!on) {
                            vm.disableCalendar()
                        } else if (status.calendarPermission) {
                            vm.startCalendarSetup()
                        } else {
                            calendarPermission.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
                        }
                    },
                )
            }
            Text(
                "端末のカレンダーに「ジム」の繰り返し予定を書き込み、Googleアカウントの同期でGoogleカレンダーに反映されます。通知はこのアプリから出すので、カレンダーの予定には通知を付けません。",
                fontSize = 12.sp,
                color = AppColors.SubText,
            )
            if (s.calendarEnabled) {
                TextButton(onClick = {
                    if (status.calendarPermission) vm.startCalendarSetup()
                    else calendarPermission.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
                }) { Text("書き込み先のカレンダーを変える") }
            }
        }

        Section("記録") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenExercises)
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("種目の管理（名前の変更・表示/非表示）", fontSize = 15.sp, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = AppColors.SubText)
            }
            HorizontalDivider(color = AppColors.Neutral)
            SettingRow("データの書き出し（CSV）") {
                TextButton(onClick = { exportLauncher.launch("muscle-habit-${LocalDate.now()}.csv") }) {
                    Text("書き出す", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    calendars?.let { list ->
        AlertDialog(
            onDismissRequest = vm::cancelCalendarSetup,
            title = { Text("書き込み先のカレンダー") },
            text = {
                Column {
                    list.forEach { c ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { vm.chooseCalendar(c) }
                                .padding(vertical = 10.dp),
                        ) {
                            Text(c.name, fontSize = 15.sp, fontWeight = if (c.id == s.calendarId) FontWeight.Bold else FontWeight.Normal)
                            Text(c.account, fontSize = 12.sp, color = AppColors.SubText)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = vm::cancelCalendarSetup) { Text("やめる") } },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontSize = 13.sp, color = AppColors.SubText, modifier = Modifier.padding(start = 4.dp))
        AppCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), verticalSpacing = 0) { content() }
    }
}

@Composable
private fun SettingRow(label: String, trailing: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) { trailing() }
    }
}

@Composable
private fun StatusRow(
    label: String,
    ok: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    okText: String = "OK",
    ngText: String = "未許可",
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp)
            Text(if (ok) okText else ngText, fontSize = 12.sp, color = if (ok) AppColors.PrimaryDark else AppColors.Danger)
        }
        if (!ok) TextButton(onClick = onAction) { Text(actionLabel, fontWeight = FontWeight.Bold) }
    }
}

/** 種目の管理：名前の変更と、チップに出すかどうか */
@Composable
fun ExercisesScreen(vm: SettingsViewModel = hiltViewModel()) {
    val list by vm.exercises.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf<ExerciseEntity?>(null) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("種目の管理", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "オフにするとトレーニング記録のチップに出なくなります。過去の記録は残ります。並び順は記録を保存するたびに「よく使う順」になります。種目の追加は記録画面の「＋ 種目」から。",
            fontSize = 13.sp,
            color = AppColors.SubText,
        )
        AppCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalSpacing = 0) {
            list.forEachIndexed { i, e ->
                if (i > 0) HorizontalDivider(color = AppColors.Neutral)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        Modifier
                            .weight(1f)
                            .clickable { renaming = e }
                            .padding(vertical = 8.dp),
                    ) {
                        Text(e.name, fontSize = 15.sp, color = if (e.hidden) AppColors.SubText else AppColors.Text)
                        Text("${e.bodyPart.label}・タップで名前を変更", fontSize = 12.sp, color = AppColors.SubText)
                    }
                    Switch(checked = !e.hidden, onCheckedChange = { vm.setHidden(e, !it) })
                }
            }
        }
    }
    renaming?.let { e ->
        NameInputDialog(
            title = "種目名を変更",
            initial = e.name,
            onDismiss = { renaming = null },
            onSave = { vm.rename(e, it); renaming = null },
        )
    }
}
