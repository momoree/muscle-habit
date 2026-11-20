package com.oashobi.musclehabit.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.oashobi.musclehabit.data.ScheduleRepository
import com.oashobi.musclehabit.data.SettingsRepository
import com.oashobi.musclehabit.data.db.ScheduleEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CalendarInfo(
    val id: Long,
    val name: String,
    val account: String,
)

/**
 * 端末のカレンダー（CalendarContract）にジムの予定を書き込む。
 * 端末の Google アカウント同期で Google カレンダーに反映される。ログインや API キーは不要。
 * 通知はアプリ側で出すので、カレンダーの予定にはリマインダーを付けない（二重通知を防ぐ）。
 */
@Singleton
class CalendarSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val schedules: ScheduleRepository,
    private val settings: SettingsRepository,
) {
    companion object {
        private const val TAG = "CalendarSync"
        const val TITLE = "ジム"
        const val DURATION_MINUTES = 60
    }

    private val resolver get() = context.contentResolver

    fun hasPermission(): Boolean = listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        .all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    /** 書き込めるカレンダーの一覧 */
    suspend fun listCalendars(): List<CalendarInfo> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
        )
        val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?"
        val args = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())
        resolver.query(CalendarContract.Calendars.CONTENT_URI, projection, selection, args, null)?.use { c ->
            buildList {
                while (c.moveToNext()) add(CalendarInfo(c.getLong(0), c.getString(1) ?: "", c.getString(2) ?: ""))
            }
        } ?: emptyList()
    }

    /** 1曜日分の予定をカレンダーに反映する（連携オフなら何もしない）。 */
    suspend fun syncOne(entity: ScheduleEntity) {
        val s = settings.current()
        val calendarId = s.calendarId
        if (!s.calendarEnabled || calendarId == null || !hasPermission()) return
        withContext(Dispatchers.IO) {
            try {
                if (entity.enabled) {
                    val id = upsertEvent(entity, calendarId)
                    if (id != entity.calendarEventId) schedules.setCalendarEventId(entity, id)
                } else if (entity.calendarEventId != null) {
                    deleteEvent(entity.calendarEventId)
                    schedules.setCalendarEventId(entity, null)
                }
            } catch (e: Exception) {
                Log.w(TAG, "カレンダーへの書き込みに失敗", e)
            }
        }
    }

    /** すべての予定を反映し直す（連携をオンにしたとき・カレンダーを変えたとき）。 */
    suspend fun syncAll() {
        schedules.getAll().forEach { syncOne(it) }
    }

    /** カレンダーからジムの予定をすべて消す（連携をオフにしたとき・カレンダーを変える前）。 */
    suspend fun removeAll() = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext
        schedules.getAll().forEach { e ->
            val id = e.calendarEventId ?: return@forEach
            try {
                deleteEvent(id)
            } catch (ex: Exception) {
                Log.w(TAG, "カレンダーの予定を消せませんでした", ex)
            }
            schedules.setCalendarEventId(e, null)
        }
    }

    private fun upsertEvent(entity: ScheduleEntity, calendarId: Long): Long {
        val zone = ZoneId.systemDefault()
        val day = DayOfWeek.of(entity.dayOfWeek)
        val firstDate = LocalDate.now().with(TemporalAdjusters.nextOrSame(day))
        val start = firstDate.atTime(entity.time).atZone(zone).toInstant().toEpochMilli()
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, TITLE)
            put(CalendarContract.Events.DTSTART, start)
            // 繰り返し予定は DTEND ではなく DURATION で長さを指定する
            put(CalendarContract.Events.DURATION, "PT${DURATION_MINUTES}M")
            put(CalendarContract.Events.RRULE, "FREQ=WEEKLY;BYDAY=${rruleDay(day)}")
            put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
            put(CalendarContract.Events.HAS_ALARM, 0)
        }
        entity.calendarEventId?.let { id ->
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
            // カレンダーアプリ側で消されていたら更新は0件になるので、作り直す
            if (resolver.update(uri, values, null, null) > 0) return id
        }
        val uri = resolver.insert(CalendarContract.Events.CONTENT_URI, values)
            ?: error("予定を作成できませんでした")
        return ContentUris.parseId(uri)
    }

    private fun deleteEvent(id: Long) {
        resolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), null, null)
    }

    private fun rruleDay(day: DayOfWeek) = when (day) {
        DayOfWeek.MONDAY -> "MO"
        DayOfWeek.TUESDAY -> "TU"
        DayOfWeek.WEDNESDAY -> "WE"
        DayOfWeek.THURSDAY -> "TH"
        DayOfWeek.FRIDAY -> "FR"
        DayOfWeek.SATURDAY -> "SA"
        DayOfWeek.SUNDAY -> "SU"
    }
}
