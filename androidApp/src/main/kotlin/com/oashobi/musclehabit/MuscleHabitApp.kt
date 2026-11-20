package com.oashobi.musclehabit

import android.app.Application
import com.oashobi.musclehabit.data.WorkoutRepository
import com.oashobi.musclehabit.di.AppScope
import com.oashobi.musclehabit.notify.AlarmScheduler
import com.oashobi.musclehabit.notify.Notifications
import com.oashobi.musclehabit.sync.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class MuscleHabitApp : Application() {
    @Inject lateinit var workouts: WorkoutRepository
    @Inject lateinit var scheduler: AlarmScheduler
    @Inject lateinit var notifications: Notifications
    @Inject @AppScope lateinit var scope: CoroutineScope
    @Inject lateinit var sync: SyncScheduler

    override fun onCreate() {
        super.onCreate()
        notifications.createChannels()
        // PC とつながっていれば、記録の変更を見張って同期を予約する
        sync.start()
        scope.launch {
            workouts.ensureSeeded()
            // 起動のたびに次回の通知を登録し直す（アプリ更新後などの取りこぼし対策）
            scheduler.reschedule()
        }
    }
}
