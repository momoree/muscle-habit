package com.oashobi.musclehabit.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.oashobi.musclehabit.shared.usecase.PrepMath
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
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
    /** 前日の準備リストの通知 */
    val prepEnabled: Boolean = true,
    val prepTime: LocalTime = PrepMath.DEFAULT_TIME,
    val prepItems: List<String> = PrepMath.DEFAULT_ITEMS,
    /** 準備リストでチェックした持ち物（prepCheckedFor の日の分だけ有効） */
    val prepCheckedFor: LocalDate? = null,
    val prepChecked: Set<String> = emptySet(),
    /** PC 同期の接続先（"192.168.1.10:8765"）。null なら未接続 */
    val syncHost: String? = null,
    val syncToken: String? = null,
    val syncPcName: String? = null,
    /** 最後に同期できた日時（エポックミリ秒） */
    val lastSyncAt: Long? = null,
    /** 最後の同期の失敗理由（成功したら消す） */
    val lastSyncError: String? = null,
) {
    val syncPaired: Boolean get() = syncHost != null && syncToken != null
}

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
        val prepEnabled = booleanPreferencesKey("prep_enabled")
        val prepTime = intPreferencesKey("prep_time_minutes")
        val prepItems = stringPreferencesKey("prep_items")
        val prepCheckedFor = stringPreferencesKey("prep_checked_for")
        val prepChecked = stringSetPreferencesKey("prep_checked")
        val syncHost = stringPreferencesKey("sync_host")
        val syncToken = stringPreferencesKey("sync_token")
        val syncPcName = stringPreferencesKey("sync_pc_name")
        val lastSyncAt = longPreferencesKey("last_sync_at")
        val lastSyncError = stringPreferencesKey("last_sync_error")
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
        prepEnabled = this[Keys.prepEnabled] ?: true,
        prepTime = this[Keys.prepTime]?.let { LocalTime.of(it / 60, it % 60) } ?: PrepMath.DEFAULT_TIME,
        prepItems = this[Keys.prepItems]?.split('\n')?.filter { it.isNotBlank() } ?: PrepMath.DEFAULT_ITEMS,
        prepCheckedFor = this[Keys.prepCheckedFor]?.let(LocalDate::parse),
        prepChecked = this[Keys.prepChecked] ?: emptySet(),
        syncHost = this[Keys.syncHost],
        syncToken = this[Keys.syncToken],
        syncPcName = this[Keys.syncPcName],
        lastSyncAt = this[Keys.lastSyncAt],
        lastSyncError = this[Keys.lastSyncError],
    )

    suspend fun setWeeklyGoal(v: Int) = context.dataStore.edit { it[Keys.weeklyGoal] = v.coerceIn(1, 7) }
    suspend fun setLeadMinutes(v: Int) = context.dataStore.edit { it[Keys.leadMinutes] = v.coerceIn(0, 180) }
    suspend fun setWeekStart(v: DayOfWeek) = context.dataStore.edit { it[Keys.weekStart] = v.value }
    suspend fun setAskedNotificationPermission() = context.dataStore.edit { it[Keys.askedNotification] = true }

    suspend fun setCalendar(enabled: Boolean, calendarId: Long?) = context.dataStore.edit {
        it[Keys.calendarEnabled] = enabled
        if (calendarId != null) it[Keys.calendarId] = calendarId else it.remove(Keys.calendarId)
    }

    // ---- 前日の準備リスト ----

    suspend fun setPrepEnabled(v: Boolean) = context.dataStore.edit { it[Keys.prepEnabled] = v }
    suspend fun setPrepTime(v: LocalTime) = context.dataStore.edit { it[Keys.prepTime] = v.hour * 60 + v.minute }
    suspend fun setPrepItems(items: List<String>) = context.dataStore.edit {
        it[Keys.prepItems] = items.map { s -> s.trim() }.filter { s -> s.isNotEmpty() }.joinToString("\n")
    }

    /** 持ち物のチェックを切り替える。別の日の分が残っていたら消してから付ける。 */
    suspend fun togglePrepItem(gymDate: LocalDate, item: String) = context.dataStore.edit {
        val sameDay = it[Keys.prepCheckedFor] == gymDate.toString()
        val current = if (sameDay) it[Keys.prepChecked] ?: emptySet() else emptySet()
        it[Keys.prepCheckedFor] = gymDate.toString()
        it[Keys.prepChecked] = if (item in current) current - item else current + item
    }

    // ---- PC 同期 ----

    suspend fun setSyncPairing(host: String, token: String, pcName: String) = context.dataStore.edit {
        it[Keys.syncHost] = host
        it[Keys.syncToken] = token
        it[Keys.syncPcName] = pcName
        it.remove(Keys.lastSyncAt)
        it.remove(Keys.lastSyncError)
    }

    suspend fun clearSyncPairing() = context.dataStore.edit {
        it.remove(Keys.syncHost)
        it.remove(Keys.syncToken)
        it.remove(Keys.syncPcName)
        it.remove(Keys.lastSyncAt)
        it.remove(Keys.lastSyncError)
    }

    suspend fun setSyncResult(at: Long?, error: String?) = context.dataStore.edit {
        if (at != null) it[Keys.lastSyncAt] = at
        if (error != null) it[Keys.lastSyncError] = error else it.remove(Keys.lastSyncError)
    }
}
