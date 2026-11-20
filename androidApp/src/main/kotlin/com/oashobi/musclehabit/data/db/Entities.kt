package com.oashobi.musclehabit.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.oashobi.musclehabit.shared.model.BodyPart
import com.oashobi.musclehabit.shared.model.ExerciseCategory
import com.oashobi.musclehabit.shared.model.VisitType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/*
 * PC との同期に備えて、すべてのエンティティが次を持つ。
 * - id: UUID（スマホとPCで同じIDを使う）
 * - updatedAt: 最後に変更した日時
 * - deleted: 削除の印（行は消さない。削除も同期できるように）
 * - synced: PC に送ったか
 */

fun newId(): String = UUID.randomUUID().toString()

/** 通う予定。1曜日につき1件。 */
@Entity(tableName = "schedules", indices = [Index(value = ["dayOfWeek"], unique = true)])
data class ScheduleEntity(
    @PrimaryKey val id: String = newId(),
    /** 1=月 … 7=日 */
    val dayOfWeek: Int,
    val time: LocalTime,
    val enabled: Boolean,
    /** Googleカレンダー（端末のカレンダー）の予定ID。更新・削除に使う */
    val calendarEventId: Long? = null,
    val updatedAt: Instant = Instant.now(),
    val deleted: Boolean = false,
    val synced: Boolean = false,
)

/** チェックイン記録（「今日はパス」も type=PASS で残す）。1日1件。 */
@Entity(tableName = "visits", indices = [Index("date")])
data class VisitEntity(
    @PrimaryKey val id: String = newId(),
    val date: LocalDate,
    val checkedInAt: Instant,
    val type: VisitType,
    val memo: String? = null,
    val updatedAt: Instant = Instant.now(),
    val deleted: Boolean = false,
    val synced: Boolean = false,
)

/** 体重・体脂肪。1日1件（同じ日は上書き）。 */
@Entity(tableName = "body_records", indices = [Index("date")])
data class BodyRecordEntity(
    @PrimaryKey val id: String = newId(),
    val date: LocalDate,
    val weightKg: Float,
    val bodyFatPercent: Float? = null,
    val updatedAt: Instant = Instant.now(),
    val deleted: Boolean = false,
    val synced: Boolean = false,
)

/** 種目。チップに並ぶのは hidden=false のもの。 */
@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val category: ExerciseCategory,
    val bodyPart: BodyPart,
    val hidden: Boolean = false,
    /** 並び順（小さいほど先）。保存のたびに使用回数の多い順に付け直す */
    val sortOrder: Int = 0,
    val updatedAt: Instant = Instant.now(),
    val deleted: Boolean = false,
    val synced: Boolean = false,
)

/** その日にやった種目。 */
@Entity(tableName = "workout_entries", indices = [Index("visitId"), Index("exerciseId")])
data class WorkoutEntryEntity(
    @PrimaryKey val id: String = newId(),
    val visitId: String,
    val exerciseId: String,
    /** 有酸素のみ（分） */
    val minutes: Int? = null,
    /** その日の中での並び順 */
    val position: Int = 0,
    val updatedAt: Instant = Instant.now(),
    val deleted: Boolean = false,
    val synced: Boolean = false,
)

/** 筋トレの1セット。重量0kgは自重。 */
@Entity(tableName = "workout_sets", indices = [Index("workoutEntryId")])
data class WorkoutSetEntity(
    @PrimaryKey val id: String = newId(),
    val workoutEntryId: String,
    val setNumber: Int,
    val weightKg: Float,
    val reps: Int,
    val updatedAt: Instant = Instant.now(),
    val deleted: Boolean = false,
    val synced: Boolean = false,
)

/** マイメニュー（例：上半身の日）。 */
@Entity(tableName = "workout_groups")
data class WorkoutGroupEntity(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val sortOrder: Int,
    val updatedAt: Instant = Instant.now(),
    val deleted: Boolean = false,
    val synced: Boolean = false,
)

/** マイメニューに入っている種目。 */
@Entity(tableName = "workout_group_items", indices = [Index("groupId")])
data class WorkoutGroupItemEntity(
    @PrimaryKey val id: String = newId(),
    val groupId: String,
    val exerciseId: String,
    /** メニュー内の順番（0 の種目が最初に選ばれる）。order は SQL の予約語なので position にした */
    val position: Int,
    val updatedAt: Instant = Instant.now(),
    val deleted: Boolean = false,
    val synced: Boolean = false,
)

/**
 * 体型写真（フェーズ3）。画像はアプリ専用領域（filesDir/photos）に置き、PC には送らない。
 * filePath は filesDir からの相対パス（例：photos/xxxx.jpg）。
 */
@Entity(tableName = "body_photos", indices = [Index("date")])
data class BodyPhotoEntity(
    @PrimaryKey val id: String = newId(),
    val date: LocalDate,
    val filePath: String,
    val updatedAt: Instant = Instant.now(),
    val deleted: Boolean = false,
    val synced: Boolean = false,
)
