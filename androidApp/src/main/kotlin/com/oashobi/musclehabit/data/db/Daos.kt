package com.oashobi.musclehabit.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules WHERE deleted = 0 ORDER BY dayOfWeek")
    fun observeAll(): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules WHERE deleted = 0 ORDER BY dayOfWeek")
    suspend fun getAll(): List<ScheduleEntity>

    @Query("SELECT * FROM schedules WHERE dayOfWeek = :day LIMIT 1")
    suspend fun getByDay(day: Int): ScheduleEntity?

    @Upsert
    suspend fun upsert(entity: ScheduleEntity)
}

@Dao
interface VisitDao {
    @Query("SELECT * FROM visits WHERE deleted = 0 ORDER BY date DESC")
    fun observeAll(): Flow<List<VisitEntity>>

    @Query("SELECT * FROM visits WHERE deleted = 0 ORDER BY date DESC")
    suspend fun getAll(): List<VisitEntity>

    @Query("SELECT * FROM visits WHERE deleted = 0 AND date = :date LIMIT 1")
    suspend fun getByDate(date: LocalDate): VisitEntity?

    @Upsert
    suspend fun upsert(entity: VisitEntity)
}

@Dao
interface BodyRecordDao {
    @Query("SELECT * FROM body_records WHERE deleted = 0 ORDER BY date")
    fun observeAll(): Flow<List<BodyRecordEntity>>

    @Query("SELECT * FROM body_records WHERE deleted = 0 ORDER BY date")
    suspend fun getAll(): List<BodyRecordEntity>

    @Query("SELECT * FROM body_records WHERE deleted = 0 AND date = :date LIMIT 1")
    suspend fun getByDate(date: LocalDate): BodyRecordEntity?

    @Query("SELECT * FROM body_records WHERE deleted = 0 ORDER BY date DESC LIMIT 1")
    suspend fun latest(): BodyRecordEntity?

    @Upsert
    suspend fun upsert(entity: BodyRecordEntity)
}

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises WHERE deleted = 0 ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE deleted = 0 ORDER BY sortOrder, name")
    suspend fun getAll(): List<ExerciseEntity>

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun countIncludingDeleted(): Int

    @Upsert
    suspend fun upsert(entity: ExerciseEntity)

    @Upsert
    suspend fun upsertAll(entities: List<ExerciseEntity>)

    @Query("SELECT exerciseId, COUNT(*) AS uses FROM workout_entries WHERE deleted = 0 GROUP BY exerciseId")
    suspend fun usage(): List<ExerciseUsage>
}

data class ExerciseUsage(val exerciseId: String, val uses: Int)

/** 種目の記録に、その日の日付を付けたもの。 */
data class EntryWithDate(
    val entryId: String,
    val exerciseId: String,
    val minutes: Int?,
    val position: Int,
    val visitId: String,
    val date: LocalDate,
    val checkedInAt: Instant,
)

@Dao
interface WorkoutDao {
    @Query(
        """
        SELECT e.id AS entryId, e.exerciseId, e.minutes, e.position, v.id AS visitId, v.date AS date, v.checkedInAt AS checkedInAt
        FROM workout_entries e JOIN visits v ON e.visitId = v.id
        WHERE e.deleted = 0 AND v.deleted = 0
        ORDER BY v.date DESC, v.checkedInAt DESC, e.position
        """,
    )
    fun observeEntries(): Flow<List<EntryWithDate>>

    @Query("SELECT * FROM workout_sets WHERE deleted = 0 ORDER BY workoutEntryId, setNumber")
    fun observeSets(): Flow<List<WorkoutSetEntity>>

    @Upsert
    suspend fun upsertEntries(entities: List<WorkoutEntryEntity>)

    @Upsert
    suspend fun upsertSets(entities: List<WorkoutSetEntity>)

    @Query("UPDATE workout_sets SET deleted = 1, synced = 0, updatedAt = :now WHERE deleted = 0 AND workoutEntryId IN (SELECT id FROM workout_entries WHERE visitId = :visitId)")
    suspend fun softDeleteSetsOfVisit(visitId: String, now: Instant)

    @Query("UPDATE workout_entries SET deleted = 1, synced = 0, updatedAt = :now WHERE deleted = 0 AND visitId = :visitId")
    suspend fun softDeleteEntriesOfVisit(visitId: String, now: Instant)
}

@Dao
interface WorkoutGroupDao {
    @Query("SELECT * FROM workout_groups WHERE deleted = 0 ORDER BY sortOrder")
    fun observeGroups(): Flow<List<WorkoutGroupEntity>>

    @Query("SELECT * FROM workout_group_items WHERE deleted = 0 ORDER BY groupId, position")
    fun observeItems(): Flow<List<WorkoutGroupItemEntity>>

    @Query("SELECT * FROM workout_groups WHERE deleted = 0 ORDER BY sortOrder")
    suspend fun getGroups(): List<WorkoutGroupEntity>

    @Upsert
    suspend fun upsertGroups(entities: List<WorkoutGroupEntity>)

    @Upsert
    suspend fun upsertItems(entities: List<WorkoutGroupItemEntity>)

    @Query("UPDATE workout_group_items SET deleted = 1, synced = 0, updatedAt = :now WHERE deleted = 0 AND groupId = :groupId")
    suspend fun softDeleteItems(groupId: String, now: Instant)
}

/**
 * PC 同期（フェーズ2）。PC に送るのは予定・チェックイン・体重の3つ。
 * synced = 0 の行（削除の印が付いた行も含む）が未送信。
 */
@Dao
interface SyncDao {
    @Query("SELECT * FROM schedules WHERE synced = 0")
    suspend fun pendingSchedules(): List<ScheduleEntity>

    @Query("SELECT * FROM visits WHERE synced = 0")
    suspend fun pendingVisits(): List<VisitEntity>

    @Query("SELECT * FROM body_records WHERE synced = 0")
    suspend fun pendingBodyRecords(): List<BodyRecordEntity>

    @Query(
        """
        SELECT (SELECT COUNT(*) FROM schedules WHERE synced = 0)
             + (SELECT COUNT(*) FROM visits WHERE synced = 0)
             + (SELECT COUNT(*) FROM body_records WHERE synced = 0)
        """,
    )
    fun observePendingCount(): Flow<Int>

    // 送ったあとで書き換えられた行は送り直すため、updatedAt が同じときだけ送信済みにする
    @Query("UPDATE schedules SET synced = 1 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markScheduleSynced(id: String, updatedAt: Instant)

    @Query("UPDATE visits SET synced = 1 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markVisitSynced(id: String, updatedAt: Instant)

    @Query("UPDATE body_records SET synced = 1 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markBodyRecordSynced(id: String, updatedAt: Instant)

    // 別の PC とつなぎ直したときは全部送り直す
    @Query("UPDATE schedules SET synced = 0")
    suspend fun resetSchedules()

    @Query("UPDATE visits SET synced = 0")
    suspend fun resetVisits()

    @Query("UPDATE body_records SET synced = 0")
    suspend fun resetBodyRecords()
}

@Dao
interface BodyPhotoDao {
    @Query("SELECT * FROM body_photos WHERE deleted = 0 ORDER BY date DESC, updatedAt DESC")
    fun observeAll(): Flow<List<BodyPhotoEntity>>

    @Upsert
    suspend fun upsert(entity: BodyPhotoEntity)
}
