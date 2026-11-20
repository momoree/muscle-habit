package com.oashobi.musclehabit.shared.sync

import kotlinx.serialization.Serializable

/*
 * スマホ → PC の同期で送る JSON の型（フェーズ2）。スマホとPCの両方で使う。
 * 日付は "2026-11-20"、時刻は "19:00"、日時はエポックミリ秒。
 * 項目は Room のエンティティと同じで、updatedAt と deleted を持つ。同じ id が届いたら PC 側で上書きする。
 */

const val SYNC_DEFAULT_PORT = 8765

@Serializable
data class ScheduleDto(
    val id: String,
    /** 1=月 … 7=日 */
    val dayOfWeek: Int,
    val time: String,
    val enabled: Boolean,
    val updatedAt: Long,
    val deleted: Boolean,
)

@Serializable
data class VisitDto(
    val id: String,
    val date: String,
    val checkedInAt: Long,
    /** NORMAL / MINIMUM / PASS */
    val type: String,
    val memo: String? = null,
    val updatedAt: Long,
    val deleted: Boolean,
)

@Serializable
data class BodyRecordDto(
    val id: String,
    val date: String,
    val weightKg: Float,
    val bodyFatPercent: Float? = null,
    val updatedAt: Long,
    val deleted: Boolean,
)

/** PC の通知と閲覧画面に要る設定。毎回送る。 */
@Serializable
data class SettingsDto(
    val weeklyGoal: Int,
    val leadMinutes: Int,
    /** 週の始まり（1=月, 7=日） */
    val weekStart: Int,
)

@Serializable
data class SyncRequest(
    val schedules: List<ScheduleDto> = emptyList(),
    val visits: List<VisitDto> = emptyList(),
    val bodyRecords: List<BodyRecordDto> = emptyList(),
    val settings: SettingsDto? = null,
)

@Serializable
data class SyncResponse(val saved: Int)

@Serializable
data class PairRequest(val code: String, val deviceName: String)

@Serializable
data class PairResponse(val token: String, val pcName: String)

@Serializable
data class HealthResponse(val app: String = "MuscleHabit", val pcName: String, val database: Boolean)
