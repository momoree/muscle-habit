package com.oashobi.musclehabit.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.DayOfWeek
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class AppSettings(
    /** 週の目標回数 */
    val weeklyGoal: Int = 3,
    /** 予定の何分前に通知するか */
    val leadMinutes: Int = 30,
    /** 週の始まりの曜日 */
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    /** Googleカレンダー連携 */
    val calendarEnabled: Boolean = false,
    /** 書き込み先のカレンダー（CalendarContract.Calendars._ID） */
    val calendarId: Long? = null,
    /** 初回の通知許可を求めたか */
    val askedNotificationPermission: Boolean = false,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val weeklyGoal = intPreferencesKey("weekly_goal")
        val leadMinutes = intPreferencesKey("lead_minutes")
        val weekStart = intPreferencesKey("week_start")
        val calendarEnabled = booleanPreferencesKey("calendar_enabled")
        val calendarId = longPreferencesKey("calendar_id")
        val askedNotification = booleanPreferencesKey("asked_notification")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    private fun Preferences.toSettings() = AppSettings(
        weeklyGoal = this[Keys.weeklyGoal] ?: 3,
        leadMinutes = this[Keys.leadMinutes] ?: 30,
        weekStart = DayOfWeek.of(this[Keys.weekStart] ?: 1),
        calendarEnabled = this[Keys.calendarEnabled] ?: false,
        calendarId = this[Keys.calendarId],
        askedNotificationPermission = this[Keys.askedNotification] ?: false,
    )

    suspend fun setWeeklyGoal(v: Int) = context.dataStore.edit { it[Keys.weeklyGoal] = v.coerceIn(1, 7) }
    suspend fun setLeadMinutes(v: Int) = context.dataStore.edit { it[Keys.leadMinutes] = v.coerceIn(0, 180) }
    suspend fun setWeekStart(v: DayOfWeek) = context.dataStore.edit { it[Keys.weekStart] = v.value }
    suspend fun setAskedNotificationPermission() = context.dataStore.edit { it[Keys.askedNotification] = true }

    suspend fun setCalendar(enabled: Boolean, calendarId: Long?) = context.dataStore.edit {
        it[Keys.calendarEnabled] = enabled
        if (calendarId != null) it[Keys.calendarId] = calendarId else it.remove(Keys.calendarId)
    }
}
