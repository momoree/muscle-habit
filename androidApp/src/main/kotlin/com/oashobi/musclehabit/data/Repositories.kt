package com.oashobi.musclehabit.data

import androidx.room.withTransaction
import com.oashobi.musclehabit.data.db.AppDatabase
import com.oashobi.musclehabit.data.db.BodyRecordEntity
import com.oashobi.musclehabit.data.db.ExerciseEntity
import com.oashobi.musclehabit.data.db.ScheduleEntity
import com.oashobi.musclehabit.data.db.VisitEntity
import com.oashobi.musclehabit.data.db.WorkoutEntryEntity
import com.oashobi.musclehabit.data.db.WorkoutGroupEntity
import com.oashobi.musclehabit.data.db.WorkoutGroupItemEntity
import com.oashobi.musclehabit.data.db.WorkoutSetEntity
import com.oashobi.musclehabit.shared.model.BodyPart
import com.oashobi.musclehabit.shared.model.ExerciseCategory
import com.oashobi.musclehabit.shared.model.ScheduleSlot
import com.oashobi.musclehabit.shared.model.VisitType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

val DEFAULT_GYM_TIME: LocalTime = LocalTime.of(19, 0)

fun ScheduleEntity.toSlot() = ScheduleSlot(DayOfWeek.of(dayOfWeek), time, enabled)

@Singleton
class ScheduleRepository @Inject constructor(db: AppDatabase) {
    private val dao = db.scheduleDao()

    fun observe(): Flow<List<ScheduleEntity>> = dao.observeAll()
    suspend fun getAll(): List<ScheduleEntity> = dao.getAll()
    suspend fun slots(): List<ScheduleSlot> = dao.getAll().map { it.toSlot() }

    /** 曜日の予定を変える。変更後の行を返す（カレンダー連携に使う）。 */
    suspend fun set(day: DayOfWeek, enabled: Boolean, time: LocalTime? = null): ScheduleEntity {
        val old = dao.getByDay(day.value)
        val updated = old?.copy(
            enabled = enabled,
            time = time ?: old.time,
            deleted = false,
            updatedAt = Instant.now(),
            synced = false,
        ) ?: ScheduleEntity(dayOfWeek = day.value, time = time ?: DEFAULT_GYM_TIME, enabled = enabled)
        dao.upsert(updated)
        return updated
    }

    suspend fun setCalendarEventId(entity: ScheduleEntity, eventId: Long?) {
        val current = dao.getByDay(entity.dayOfWeek) ?: return
        dao.upsert(current.copy(calendarEventId = eventId, updatedAt = Instant.now(), synced = false))
    }
}

@Singleton
class VisitRepository @Inject constructor(db: AppDatabase) {
    private val dao = db.visitDao()

    fun observe(): Flow<List<VisitEntity>> = dao.observeAll()
    suspend fun getAll(): List<VisitEntity> = dao.getAll()
    suspend fun getByDate(date: LocalDate): VisitEntity? = dao.getByDate(date)

    /** チェックイン。同じ日は1件だけ。パスしていた日なら「行った」に変える。 */
    suspend fun checkIn(date: LocalDate, type: VisitType = VisitType.NORMAL): VisitEntity {
        val existing = dao.getByDate(date)
        if (existing != null && existing.type.countsAsVisit) return existing
        val visit = existing?.copy(type = type, checkedInAt = Instant.now(), updatedAt = Instant.now(), synced = false)
            ?: VisitEntity(date = date, checkedInAt = Instant.now(), type = type)
        dao.upsert(visit)
        return visit
    }

    /** 「今日はパス」。すでに行った日なら何もしない。 */
    suspend fun pass(date: LocalDate) {
        if (dao.getByDate(date) != null) return
        dao.upsert(VisitEntity(date = date, checkedInAt = Instant.now(), type = VisitType.PASS))
    }

    /** チェックインを取り消す（間違えて押したとき）。 */
    suspend fun cancel(date: LocalDate) {
        val existing = dao.getByDate(date) ?: return
        dao.upsert(existing.copy(deleted = true, updatedAt = Instant.now(), synced = false))
    }
}

@Singleton
class BodyRepository @Inject constructor(db: AppDatabase) {
    private val dao = db.bodyRecordDao()

    fun observe(): Flow<List<BodyRecordEntity>> = dao.observeAll()
    suspend fun getAll(): List<BodyRecordEntity> = dao.getAll()
    suspend fun latest(): BodyRecordEntity? = dao.latest()
    suspend fun getByDate(date: LocalDate): BodyRecordEntity? = dao.getByDate(date)

    /** 1日1件。同じ日に記録したら上書きする。 */
    suspend fun save(date: LocalDate, weightKg: Float, bodyFatPercent: Float?) {
        val existing = dao.getByDate(date)
        dao.upsert(
            existing?.copy(weightKg = weightKg, bodyFatPercent = bodyFatPercent, updatedAt = Instant.now(), synced = false)
                ?: BodyRecordEntity(date = date, weightKg = weightKg, bodyFatPercent = bodyFatPercent),
        )
    }
}

/** 1セット分 */
data class LoggedSet(val weightKg: Float, val reps: Int)

/** その日にやった1種目（筋トレは sets、有酸素は minutes） */
data class LoggedExercise(
    val exerciseId: String,
    val minutes: Int? = null,
    val sets: List<LoggedSet> = emptyList(),
)

/** ある日のトレーニング */
data class DayWorkout(
    val visitId: String,
    val date: LocalDate,
    val checkedInAt: Instant,
    val items: List<LoggedExercise>,
)

/** マイメニュー */
data class WorkoutGroup(
    val id: String,
    val name: String,
    val sortOrder: Int,
    val exerciseIds: List<String>,
)

@Singleton
class WorkoutRepository @Inject constructor(
    private val db: AppDatabase,
    private val catalog: ExerciseCatalog,
) {
    private val exerciseDao = db.exerciseDao()
    private val workoutDao = db.workoutDao()
    private val groupDao = db.workoutGroupDao()

    fun observeExercises(): Flow<List<ExerciseEntity>> = exerciseDao.observeAll()

    /** すべての日のトレーニング（新しい日が先）。 */
    fun observeHistory(): Flow<List<DayWorkout>> =
        combine(workoutDao.observeEntries(), workoutDao.observeSets()) { entries, sets ->
            val setsByEntry = sets.groupBy { it.workoutEntryId }
            entries.groupBy { it.visitId }.map { (visitId, rows) ->
                DayWorkout(
                    visitId = visitId,
                    date = rows.first().date,
                    checkedInAt = rows.first().checkedInAt,
                    items = rows.sortedBy { it.position }.map { row ->
                        LoggedExercise(
                            exerciseId = row.exerciseId,
                            minutes = row.minutes,
                            sets = setsByEntry[row.entryId].orEmpty().sortedBy { it.setNumber }
                                .map { LoggedSet(it.weightKg, it.reps) },
                        )
                    },
                )
            }.sortedWith(compareByDescending<DayWorkout> { it.date }.thenByDescending { it.checkedInAt })
        }

    fun observeGroups(): Flow<List<WorkoutGroup>> =
        combine(groupDao.observeGroups(), groupDao.observeItems()) { groups, items ->
            val byGroup = items.groupBy { it.groupId }
            groups.map { g ->
                WorkoutGroup(g.id, g.name, g.sortOrder, byGroup[g.id].orEmpty().sortedBy { it.position }.map { it.exerciseId })
            }
        }

    /** 初回起動時に、よく使う種目をチップに登録しておく。 */
    suspend fun ensureSeeded() {
        if (exerciseDao.countIncludingDeleted() > 0) return
        exerciseDao.upsertAll(
            catalog.initial.mapIndexed { i, c ->
                ExerciseEntity(name = c.name, category = c.category, bodyPart = c.bodyPart, sortOrder = i)
            },
        )
    }

    /**
     * 種目をチップに加える。同じ名前の種目があれば（外していたものも）それを戻す。
     * 過去の記録と「前回の重量」はそのまま残る。
     */
    suspend fun addExercise(name: String, category: ExerciseCategory, bodyPart: BodyPart): ExerciseEntity {
        val trimmed = name.trim()
        val all = exerciseDao.getAll()
        all.firstOrNull { it.name == trimmed }?.let { found ->
            val shown = found.copy(hidden = false, updatedAt = Instant.now(), synced = false)
            exerciseDao.upsert(shown)
            return shown
        }
        val entity = ExerciseEntity(
            name = trimmed,
            category = category,
            bodyPart = bodyPart,
            sortOrder = (all.maxOfOrNull { it.sortOrder } ?: -1) + 1,
        )
        exerciseDao.upsert(entity)
        return entity
    }

    suspend fun setHidden(exercise: ExerciseEntity, hidden: Boolean) =
        exerciseDao.upsert(exercise.copy(hidden = hidden, updatedAt = Instant.now(), synced = false))

    suspend fun rename(exercise: ExerciseEntity, name: String) {
        if (name.isBlank()) return
        exerciseDao.upsert(exercise.copy(name = name.trim(), updatedAt = Instant.now(), synced = false))
    }

    /** その日の記録をまるごと保存し直す（古い行は削除の印を付ける）。 */
    suspend fun saveDay(visitId: String, items: List<LoggedExercise>) {
        val now = Instant.now()
        db.withTransaction {
            workoutDao.softDeleteSetsOfVisit(visitId, now)
            workoutDao.softDeleteEntriesOfVisit(visitId, now)
            val entries = mutableListOf<WorkoutEntryEntity>()
            val sets = mutableListOf<WorkoutSetEntity>()
            items.forEachIndexed { index, item ->
                val entry = WorkoutEntryEntity(visitId = visitId, exerciseId = item.exerciseId, minutes = item.minutes, position = index)
                entries += entry
                item.sets.forEachIndexed { n, s ->
                    sets += WorkoutSetEntity(workoutEntryId = entry.id, setNumber = n + 1, weightKg = s.weightKg, reps = s.reps)
                }
            }
            workoutDao.upsertEntries(entries)
            workoutDao.upsertSets(sets)
            reorderByUsage()
        }
    }

    /** チップの並びを「よく使う順」にする。 */
    private suspend fun reorderByUsage() {
        val uses = exerciseDao.usage().associate { it.exerciseId to it.uses }
        val sorted = exerciseDao.getAll().sortedWith(
            compareByDescending<ExerciseEntity> { uses[it.id] ?: 0 }.thenBy { it.sortOrder }.thenBy { it.name },
        )
        val changed = sorted.mapIndexedNotNull { i, e ->
            if (e.sortOrder == i) null else e.copy(sortOrder = i, updatedAt = Instant.now(), synced = false)
        }
        if (changed.isNotEmpty()) exerciseDao.upsertAll(changed)
    }

    suspend fun createGroup(name: String, exerciseIds: List<String>) {
        val groups = groupDao.getGroups()
        val group = WorkoutGroupEntity(name = name.trim().ifEmpty { "新しいメニュー" }, sortOrder = (groups.maxOfOrNull { it.sortOrder } ?: -1) + 1)
        db.withTransaction {
            groupDao.upsertGroups(listOf(group))
            groupDao.upsertItems(exerciseIds.mapIndexed { i, id -> WorkoutGroupItemEntity(groupId = group.id, exerciseId = id, position = i) })
        }
    }

    suspend fun overwriteGroup(groupId: String, exerciseIds: List<String>) {
        val now = Instant.now()
        db.withTransaction {
            groupDao.softDeleteItems(groupId, now)
            groupDao.upsertItems(exerciseIds.mapIndexed { i, id -> WorkoutGroupItemEntity(groupId = groupId, exerciseId = id, position = i) })
            touchGroup(groupId) { it }
        }
    }

    suspend fun renameGroup(groupId: String, name: String) {
        if (name.isBlank()) return
        touchGroup(groupId) { it.copy(name = name.trim()) }
    }

    suspend fun deleteGroup(groupId: String) {
        db.withTransaction {
            groupDao.softDeleteItems(groupId, Instant.now())
            touchGroup(groupId) { it.copy(deleted = true) }
        }
    }

    /** 並べ替え（delta=-1 で左へ、+1 で右へ） */
    suspend fun moveGroup(groupId: String, delta: Int) {
        val groups = groupDao.getGroups().toMutableList()
        val from = groups.indexOfFirst { it.id == groupId }
        val to = from + delta
        if (from < 0 || to !in groups.indices) return
        groups.add(to, groups.removeAt(from))
        groupDao.upsertGroups(groups.mapIndexed { i, g -> g.copy(sortOrder = i, updatedAt = Instant.now(), synced = false) })
    }

    private suspend fun touchGroup(groupId: String, change: (WorkoutGroupEntity) -> WorkoutGroupEntity) {
        val g = groupDao.getGroups().firstOrNull { it.id == groupId } ?: return
        groupDao.upsertGroups(listOf(change(g).copy(updatedAt = Instant.now(), synced = false)))
    }
}
