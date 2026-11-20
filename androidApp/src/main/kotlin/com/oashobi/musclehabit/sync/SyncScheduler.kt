package com.oashobi.musclehabit.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.oashobi.musclehabit.data.SettingsRepository
import com.oashobi.musclehabit.data.db.AppDatabase
import com.oashobi.musclehabit.di.AppScope
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 同期の予約。Wi-Fi（従量制でない回線）につながっているときだけ動く。
 * - 記録が増えたり設定が変わったりしたら、すぐに1回
 * - それとは別に1時間ごと（送れなかった分は次回まとめて送る）
 */
@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    @AppScope private val scope: CoroutineScope,
) {
    companion object {
        private const val WORK_NOW = "sync_now"
        private const val WORK_PERIODIC = "sync_periodic"
    }

    private val wifi = Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build()

    /** アプリ起動時に1回呼ぶ。つながっている間、変更を見張って同期を予約する。 */
    fun start() {
        scope.launch {
            var lastSettings: Any? = null
            combine(db.syncDao().observePendingCount(), settings.settings) { pending, s ->
                // PC に送る設定（目標・通知の時間・週の始まり）
                Triple(pending, s.syncPaired, Triple(s.weeklyGoal, s.leadMinutes, s.weekStart))
            }
                .distinctUntilChanged()
                .collect { (pending, paired, pcSettings) ->
                    val settingsChanged = lastSettings != null && lastSettings != pcSettings
                    lastSettings = pcSettings
                    if (!paired) {
                        cancelAll()
                        return@collect
                    }
                    schedulePeriodic()
                    if (pending > 0 || settingsChanged) requestNow()
                }
        }
    }

    fun requestNow() {
        val req = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(wifi)
            .setInitialDelay(3, TimeUnit.SECONDS) // 続けて記録したときにまとめる
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NOW, ExistingWorkPolicy.REPLACE, req)
    }

    private fun schedulePeriodic() {
        val req = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS).setConstraints(wifi).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    private fun cancelAll() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NOW)
        WorkManager.getInstance(context).cancelUniqueWork(WORK_PERIODIC)
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SyncEntryPoint {
    fun syncRepository(): SyncRepository
}

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = EntryPointAccessors.fromApplication(applicationContext, SyncEntryPoint::class.java).syncRepository()
        return when (val r = repo.syncNow()) {
            is SyncResult.Ok, SyncResult.NotPaired -> Result.success()
            is SyncResult.Failed -> if (r.retry && runAttemptCount < 3) Result.retry() else Result.success()
        }
    }
}
