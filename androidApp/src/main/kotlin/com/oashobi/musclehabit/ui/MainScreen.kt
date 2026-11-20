package com.oashobi.musclehabit.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.oashobi.musclehabit.data.SettingsRepository
import com.oashobi.musclehabit.notify.Notifications
import com.oashobi.musclehabit.ui.body.BodyRecordDialog
import com.oashobi.musclehabit.ui.home.HomeScreen
import com.oashobi.musclehabit.ui.photos.PhotosScreen
import com.oashobi.musclehabit.ui.progress.ProgressScreen
import com.oashobi.musclehabit.ui.schedule.ScheduleScreen
import com.oashobi.musclehabit.ui.settings.ExercisesScreen
import com.oashobi.musclehabit.ui.settings.SettingsScreen
import com.oashobi.musclehabit.ui.theme.AppColors
import com.oashobi.musclehabit.ui.workout.WorkoutSheet
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "ホーム", Icons.Outlined.Home),
    SCHEDULE("schedule", "予定", Icons.Outlined.CalendarMonth),
    PROGRESS("progress", "推移", Icons.AutoMirrored.Outlined.ShowChart),
    SETTINGS("settings", "設定", Icons.Outlined.Settings),
}

private const val ROUTE_EXERCISES = "exercises"
private const val ROUTE_PHOTOS = "photos"

@HiltViewModel
class MainViewModel @Inject constructor(
    val settings: SettingsRepository,
    val notifications: Notifications,
) : ViewModel()

@Composable
fun MainScreen(vm: MainViewModel = hiltViewModel()) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    // 開いているトレーニング記録（visitId と日付）
    var workout by rememberSaveable { mutableStateOf<Pair<String, String>?>(null) }
    var askBodyAfterWorkout by rememberSaveable { mutableStateOf(false) }
    var bodyDialog by rememberSaveable { mutableStateOf<Boolean?>(null) } // true=チェックイン後に聞く

    // 初回起動時に通知の許可を求める（Android 13 以降）
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        val s = vm.settings.current()
        if (!s.askedNotificationPermission) {
            vm.settings.setAskedNotificationPermission()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !vm.notifications.hasPermission()) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    fun go(r: String) = nav.navigate(r) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    Scaffold(
        containerColor = AppColors.Background,
        bottomBar = {
            NavigationBar(containerColor = AppColors.Surface) {
                Tab.entries.forEach { tab ->
                    val selected = route == tab.route || (tab == Tab.SETTINGS && route == ROUTE_EXERCISES) || (tab == Tab.PROGRESS && route == ROUTE_PHOTOS)
                    NavigationBarItem(
                        selected = selected,
                        onClick = { go(tab.route) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AppColors.Primary,
                            selectedTextColor = AppColors.Primary,
                            indicatorColor = AppColors.PrimaryLight,
                            unselectedIconColor = AppColors.SubText,
                            unselectedTextColor = AppColors.SubText,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Tab.HOME.route, modifier = Modifier.padding(padding)) {
            composable(Tab.HOME.route) {
                HomeScreen(
                    onOpenWorkout = { visitId, afterCheckIn ->
                        workout = visitId to LocalDate.now().toString()
                        askBodyAfterWorkout = afterCheckIn
                    },
                    onRecordBody = { bodyDialog = false },
                    onOpenSchedule = { go(Tab.SCHEDULE.route) },
                )
            }
            composable(Tab.SCHEDULE.route) { ScheduleScreen() }
            composable(Tab.PROGRESS.route) {
                ProgressScreen(
                    onRecordBody = { bodyDialog = false },
                    onEditWorkout = { visitId, date ->
                        workout = visitId to date.toString()
                        askBodyAfterWorkout = false
                    },
                    onOpenPhotos = { nav.navigate(ROUTE_PHOTOS) },
                )
            }
            composable(Tab.SETTINGS.route) { SettingsScreen(onOpenExercises = { nav.navigate(ROUTE_EXERCISES) }) }
            composable(ROUTE_EXERCISES) { ExercisesScreen() }
            composable(ROUTE_PHOTOS) { PhotosScreen() }
        }
    }

    workout?.let { (visitId, date) ->
        WorkoutSheet(
            visitId = visitId,
            date = LocalDate.parse(date),
            onDismiss = {
                workout = null
                // チェックイン後の流れなら「体重も記録する？」と一緒に聞く
                if (askBodyAfterWorkout) bodyDialog = true
                askBodyAfterWorkout = false
            },
        )
    }

    bodyDialog?.let { afterCheckIn ->
        BodyRecordDialog(onDismiss = { bodyDialog = null }, askAfterCheckIn = afterCheckIn)
    }
}
