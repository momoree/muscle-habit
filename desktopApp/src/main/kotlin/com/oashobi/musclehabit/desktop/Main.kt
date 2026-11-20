package com.oashobi.musclehabit.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.decodeToSvgPainter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import com.oashobi.musclehabit.desktop.ui.AppColors
import com.oashobi.musclehabit.desktop.ui.ConnectScreen
import com.oashobi.musclehabit.desktop.ui.MuscleHabitTheme
import com.oashobi.musclehabit.desktop.ui.RecordsScreen
import com.oashobi.musclehabit.desktop.ui.SettingsScreen
import java.time.LocalDate
import kotlinx.coroutines.delay

private enum class Page(val label: String, val icon: ImageVector) {
    RECORDS("記録", Icons.AutoMirrored.Outlined.ShowChart),
    CONNECT("スマホと\nつなぐ", Icons.Outlined.PhoneAndroid),
    SETTINGS("設定", Icons.Outlined.Settings),
}

private fun appIcon(): Painter =
    Thread.currentThread().contextClassLoader.getResourceAsStream("icon.svg")!!.use { it.readBytes() }.decodeToSvgPainter(Density(1f))

/**
 * MuscleHabit PC 版。タスクトレイに常駐し、中で同期サーバーと通知を動かす。
 * ウィンドウを閉じてもトレイに残る。終了はトレイのメニューから。
 * ログイン時の自動起動（--minimized）ではウィンドウを出さずにトレイだけで起動する。
 */
fun main(args: Array<String>) {
    // 2つ目は起動しない（同期のポートを取り合ったり、通知が二重に出たりするため）
    if (!SingleInstance.acquire()) {
        System.err.println("MuscleHabit はもう起動しています")
        return
    }
    AppLog.enabled = true
    val engine = AppEngine()
    application {
        val icon = remember { appIcon() }
        val trayState = rememberTrayState()
        var windowVisible by remember { mutableStateOf(AutoStart.MINIMIZED_ARG !in args) }

        DisposableEffect(Unit) {
            engine.start()
            onDispose { engine.stop() }
        }
        // 予定の前の通知は画面のスレッドで出す（TrayState は受け手がいないと捨てるため）
        LaunchedEffect(Unit) {
            engine.notifications.collect { (title, body) ->
                trayState.sendNotification(Notification(title, body, Notification.Type.Info))
            }
        }

        Tray(
            icon = icon,
            state = trayState,
            tooltip = "MuscleHabit",
            onAction = { windowVisible = true },
            menu = {
                Item("開く", onClick = { windowVisible = true })
                Item("終了", onClick = { engine.stop(); exitApplication() })
            },
        )

        Window(
            onCloseRequest = { windowVisible = false },
            visible = windowVisible,
            title = "MuscleHabit",
            icon = icon,
            state = rememberWindowState(size = DpSize(1080.dp, 800.dp)),
        ) {
            MuscleHabitTheme {
                MainContent(engine, onTestNotification = {
                    trayState.sendNotification(Notification("今日はジムの日", "通知のテストです。予定の前にこのように届きます", Notification.Type.Info))
                })
            }
        }
    }
}

@Composable
private fun MainContent(engine: AppEngine, onTestNotification: () -> Unit) {
    val state by engine.state.collectAsState()
    var page by remember { mutableStateOf(Page.RECORDS) }
    var today by remember { mutableStateOf(LocalDate.now()) }

    // 日付が変わったときと、DB の中身の取りこぼしに備えて1分ごとに読み直す
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            today = LocalDate.now()
            engine.refresh()
        }
    }

    Row(Modifier.fillMaxSize().background(AppColors.Background)) {
        NavigationRail(containerColor = AppColors.Surface, modifier = Modifier.fillMaxHeight()) {
            Page.entries.forEach { p ->
                NavigationRailItem(
                    selected = page == p,
                    onClick = { page = p; if (p == Page.RECORDS) engine.refresh() },
                    icon = { Icon(p.icon, null) },
                    label = { Text(p.label, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center) },
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = AppColors.Primary,
                        selectedTextColor = AppColors.Primary,
                        indicatorColor = AppColors.PrimaryLight,
                        unselectedIconColor = AppColors.SubText,
                        unselectedTextColor = AppColors.SubText,
                    ),
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }
        Column(Modifier.fillMaxSize()) {
            if (!state.dbConnected && page == Page.RECORDS) {
                Box(Modifier.fillMaxWidth().background(AppColors.StreakBg).padding(horizontal = 28.dp, vertical = 12.dp)) {
                    Text(
                        (state.dbError ?: "MySQL につながっていません") + "（「設定」で接続先を確かめてください）",
                        color = AppColors.Streak,
                        fontSize = 13.sp,
                    )
                }
            }
            when (page) {
                Page.RECORDS -> RecordsScreen(state.snapshot, today)
                Page.CONNECT -> ConnectScreen(state, onRegenerate = engine::regeneratePairingCode, onForget = engine::forgetPhones)
                Page.SETTINGS -> SettingsScreen(state, onSaveDb = engine::saveDatabaseSettings, onTestNotification = onTestNotification)
            }
        }
    }
}
