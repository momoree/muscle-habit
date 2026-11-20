package com.oashobi.musclehabit.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.oashobi.musclehabit.data.VisitRepository
import com.oashobi.musclehabit.di.AppScope
import com.oashobi.musclehabit.domain.CheckInActions
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReceiverEntryPoint {
    fun scheduler(): AlarmScheduler
    fun notifications(): Notifications
    fun visits(): VisitRepository
    fun actions(): CheckInActions

    @AppScope
    fun scope(): CoroutineScope
}

private fun Context.entryPoint(): ReceiverEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, ReceiverEntryPoint::class.java)

/** BroadcastReceiver の中でコルーチンを動かす（終わるまでプロセスを生かしておく） */
private fun BroadcastReceiver.runAsync(context: Context, block: suspend (ReceiverEntryPoint) -> Unit) {
    val pending = goAsync()
    val ep = context.entryPoint()
    ep.scope().launch {
        try {
            block(ep)
        } finally {
            pending.finish()
        }
    }
}

/** 予定時刻の前に発火し、当日通知を出して次回のアラームを登録する。 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = runAsync(context) { ep ->
        val gymAt = intent.getStringExtra(AlarmScheduler.EXTRA_GYM_AT)?.let(LocalDateTime::parse)
        if (gymAt != null) {
            ep.scheduler().markNotified(gymAt)
            // 既にその日にチェックイン済み（またはパス済み）なら出さない
            val alreadyDone = ep.visits().getByDate(gymAt.toLocalDate()) != null
            if (!alreadyDone && gymAt.isAfter(LocalDateTime.now().minusHours(1))) {
                ep.notifications().showReminder(gymAt, ep.actions().weekProgress(gymAt.toLocalDate()).remaining)
            }
        }
        ep.scheduler().reschedule()
    }
}

/** 通知の「行った」「今日はパス」。アプリを開かずに記録する。 */
class NotificationActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_CHECK_IN = "com.oashobi.musclehabit.CHECK_IN"
        const val ACTION_PASS = "com.oashobi.musclehabit.PASS"
        const val EXTRA_DATE = "date"
    }

    override fun onReceive(context: Context, intent: Intent) = runAsync(context) { ep ->
        val date = intent.getStringExtra(EXTRA_DATE)?.let(LocalDate::parse) ?: LocalDate.now()
        when (intent.action) {
            ACTION_CHECK_IN -> {
                ep.actions().checkIn(date)
                val p = ep.actions().weekProgress(date)
                val text = if (p.achieved) "チェックインしました。今週の目標達成です" else "チェックインしました。今週 ${p.count}/${p.goal}回"
                ep.notifications().showDone(text)
            }
            ACTION_PASS -> {
                ep.actions().pass(date)
                ep.notifications().showDone("今日はお休み。また次の予定で")
            }
        }
    }
}

/** 再起動・時刻変更・アプリ更新のあとにアラームを登録し直す。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = runAsync(context) { ep ->
        ep.scheduler().reschedule()
    }
}
