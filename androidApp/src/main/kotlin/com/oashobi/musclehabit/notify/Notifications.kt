package com.oashobi.musclehabit.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.oashobi.musclehabit.MainActivity
import com.oashobi.musclehabit.R
import com.oashobi.musclehabit.shared.usecase.PrepText
import com.oashobi.musclehabit.shared.usecase.ReminderText
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Notifications @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val CHANNEL_REMINDER = "reminder"
        const val ID_REMINDER = 1
        const val ID_DONE = 2
        const val ID_PREP = 3
        const val CHANNEL_PREP = "prep"
        private const val PRIMARY = 0xFF0E7C6B.toInt()
    }

    private val manager = NotificationManagerCompat.from(context)

    fun createChannels() {
        val reminder = NotificationChannel(CHANNEL_REMINDER, "ジムの予定", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "予定時刻の前に届く当日通知"
        }
        val prep = NotificationChannel(CHANNEL_PREP, "前日の準備", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "ジムの日の前の晩に届く持ち物のリスト"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(reminder, prep))
    }

    /** 前日の準備リスト。開くとホームに持ち物のチェックリストが出る。 */
    fun showPrep(gymDate: LocalDate, items: List<String>) {
        if (!hasPermission()) return
        val list = items.joinToString("\n") { "□ $it" }
        val notification = NotificationCompat.Builder(context, CHANNEL_PREP)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(PRIMARY)
            .setContentTitle(PrepText.title())
            .setContentText(PrepText.body(items))
            .setStyle(NotificationCompat.BigTextStyle().bigText(if (items.isEmpty()) PrepText.body(items) else list))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openApp())
            .setAutoCancel(true)
            // ジムの日が終わったら自然に消えるように
            .setTimeoutAfter(Duration.between(LocalDateTime.now(), gymDate.plusDays(1).atStartOfDay()).toMillis().coerceAtLeast(60_000))
            .build()
        manager.notify(ID_PREP, notification)
    }

    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** 当日通知。通知から「行った」「今日はパス」を選べる。 */
    fun showReminder(gymAt: LocalDateTime, remaining: Int, daysLeft: Int = 7) {
        if (!hasPermission()) return
        val date = gymAt.toLocalDate()
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(PRIMARY)
            .setContentTitle(ReminderText.title())
            .setContentText(ReminderText.body(gymAt, remaining, daysLeft))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .addAction(0, "行った", action(NotificationActionReceiver.ACTION_CHECK_IN, date, 10))
            .addAction(0, "今日はパス", action(NotificationActionReceiver.ACTION_PASS, date, 11))
            .build()
        manager.notify(ID_REMINDER, notification)
    }

    /** 通知から記録したときの確認（数秒で消える）。 */
    fun showDone(text: String) {
        if (!hasPermission()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(PRIMARY)
            .setContentTitle(text)
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .setTimeoutAfter(6_000)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        manager.notify(ID_DONE, notification)
    }

    fun cancelReminder() = manager.cancel(ID_REMINDER)

    fun cancelPrep() = manager.cancel(ID_PREP)

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun action(action: String, date: LocalDate, requestCode: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode,
        Intent(context, NotificationActionReceiver::class.java)
            .setAction(action)
            .putExtra(NotificationActionReceiver.EXTRA_DATE, date.toString()),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
