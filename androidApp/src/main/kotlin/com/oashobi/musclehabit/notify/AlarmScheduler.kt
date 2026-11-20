package com.oashobi.musclehabit.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.oashobi.musclehabit.data.ScheduleRepository
import com.oashobi.musclehabit.data.SettingsRepository
import com.oashobi.musclehabit.data.VisitRepository
import com.oashobi.musclehabit.shared.usecase.Reminder
import com.oashobi.musclehabit.shared.usecase.ReminderMath
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 次回の当日通知を AlarmManager に登録する。登録するのは常に「次の1回」だけで、
 * 発火したら AlarmReceiver が次の回を登録し直す。
 * 予定・設定の変更、チェックイン、再起動、アプリ起動のたびに reschedule() を呼ぶ。
 */
@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val schedules: ScheduleRepository,
    private val visits: VisitRepository,
    private val settings: SettingsRepository,
) {
    companion object {
        const val EXTRA_GYM_AT = "gym_at"
        private const val PREFS = "alarm"
        private const val KEY_NOTIFIED_GYM_AT = "notified_gym_at"
    }

    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val mutex = Mutex()

    /** 正確なアラームが使えるか（Android 12 で許可されていないと false） */
    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /** 次の通知の予定（画面表示用。登録はしない） */
    suspend fun nextReminder(): Reminder? = compute()

    suspend fun reschedule() = mutex.withLock {
        val reminder = compute()
        val pi = pendingIntent(reminder?.gymAt)
        if (reminder == null) {
            alarmManager.cancel(pi)
            return@withLock
        }
        val at = reminder.triggerAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    /** 通知を出した予定を覚えておき、同じ予定でもう一度通知しないようにする。 */
    fun markNotified(gymAt: LocalDateTime) {
        prefs.edit().putString(KEY_NOTIFIED_GYM_AT, gymAt.toString()).apply()
    }

    private suspend fun compute(): Reminder? {
        val now = LocalDateTime.now()
        val notified = prefs.getString(KEY_NOTIFIED_GYM_AT, null)?.let(LocalDateTime::parse)
        // 通知済みの予定より後から探す（通知〜予定時刻の間に呼ばれても二重に通知しない）
        val base = if (notified != null && notified.isAfter(now)) notified else now
        // すでにチェックイン（またはパス）した日は通知しない
        val skip = setOfNotNull(visits.getByDate(now.toLocalDate())?.date)
        return ReminderMath.next(schedules.slots(), base, settings.current().leadMinutes, skip)
    }

    private fun pendingIntent(gymAt: LocalDateTime?): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, AlarmReceiver::class.java).apply {
            if (gymAt != null) putExtra(EXTRA_GYM_AT, gymAt.toString())
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
