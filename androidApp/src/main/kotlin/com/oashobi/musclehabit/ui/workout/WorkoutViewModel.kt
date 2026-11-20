package com.oashobi.musclehabit.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oashobi.musclehabit.data.CatalogExercise
import com.oashobi.musclehabit.data.DayWorkout
import com.oashobi.musclehabit.data.ExerciseCatalog
import com.oashobi.musclehabit.data.LoggedExercise
import com.oashobi.musclehabit.data.LoggedSet
import com.oashobi.musclehabit.data.WorkoutGroup
import com.oashobi.musclehabit.data.WorkoutRepository
import com.oashobi.musclehabit.data.db.ExerciseEntity
import com.oashobi.musclehabit.shared.model.BodyPart
import com.oashobi.musclehabit.shared.model.ExerciseCategory
import com.oashobi.musclehabit.shared.usecase.WorkoutFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 「今日の記録」の1行。筋トレは1セットで1行、有酸素は1回で1行。数値なしは「種目のみ」。 */
data class DraftRow(
    val key: Long,
    val exerciseId: String,
    val weightKg: Float? = null,
    val reps: Int? = null,
    val minutes: Int? = null,
)

/** 前回の値（その種目の最後のセット、または最後の時間） */
data class PrevValue(val weightKg: Float? = null, val reps: Int? = null, val minutes: Int? = null)

/** 「＋種目」の候補。owned は過去に追加して外した種目（タップで前回の重量ごと戻る） */
data class Candidate(val name: String, val category: ExerciseCategory, val bodyPart: BodyPart, val owned: ExerciseEntity?)

data class WorkoutUi(
    val loaded: Boolean = false,
    val visitId: String? = null,
    val exercises: List<ExerciseEntity> = emptyList(),
    val groups: List<WorkoutGroup> = emptyList(),
    val activeGroupId: String? = null,
    /** マイメニューを呼び出しているときに並べる種目（その日だけの追加・削除を含む）。null なら全種目 */
    val sessionIds: List<String>? = null,
    val selectedId: String? = null,
    val weightText: String = "",
    val count: Int = 10,
    val rows: List<DraftRow> = emptyList(),
    val pickerOpen: Boolean = false,
    val pickerPart: BodyPart = BodyPart.CHEST,
    val prev: Map<String, PrevValue> = emptyMap(),
    val lastWorkout: DayWorkout? = null,
    val saving: Boolean = false,
) {
    private val byId get() = exercises.associateBy { it.id }

    val chips: List<ExerciseEntity>
        get() = sessionIds?.mapNotNull { byId[it] } ?: exercises.filter { !it.hidden }

    val selected: ExerciseEntity? get() = selectedId?.let { byId[it] }

    fun exercise(id: String): ExerciseEntity? = byId[id]
}

@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val repo: WorkoutRepository,
    private val catalog: ExerciseCatalog,
) : ViewModel() {

    private val _ui = MutableStateFlow(WorkoutUi())
    val ui: StateFlow<WorkoutUi> = _ui
    private var nextKey = 1L
    private var started = false

    fun start(visitId: String) {
        if (started) return
        started = true
        _ui.update { it.copy(visitId = visitId) }
        viewModelScope.launch {
            combine(repo.observeExercises(), repo.observeGroups(), repo.observeHistory()) { ex, groups, history ->
                Triple(ex, groups, history)
            }.collect { (ex, groups, history) ->
                val past = history.filter { it.visitId != visitId }
                _ui.update { u ->
                    var next = u.copy(exercises = ex, groups = groups, prev = prevValues(past), lastWorkout = past.firstOrNull())
                    if (!u.loaded) {
                        // 開いたときに、その日の記録があれば読み込む（帰宅後に数値を直す使い方）
                        val today = history.firstOrNull { it.visitId == visitId }
                        next = next.copy(loaded = true, rows = today?.let { toRows(it.items) }.orEmpty())
                    }
                    next
                }
                if (_ui.value.selectedId == null || _ui.value.selected == null) {
                    _ui.value.chips.firstOrNull()?.let { select(it) }
                }
            }
        }
    }

    // ---- 種目の選択と入力 ----

    fun select(ex: ExerciseEntity) {
        _ui.update { u ->
            // 今日すでに記録した種目なら、その最後の値を引き継ぐ。なければ前回の値
            val lastToday = u.rows.lastOrNull { it.exerciseId == ex.id && (it.reps != null || it.minutes != null) }
            val prev = u.prev[ex.id]
            if (ex.category == ExerciseCategory.STRENGTH) {
                val w = lastToday?.weightKg ?: prev?.weightKg
                u.copy(
                    selectedId = ex.id,
                    weightText = w?.let(WorkoutFormat::weightInput) ?: "",
                    count = lastToday?.reps ?: prev?.reps ?: 10,
                )
            } else {
                u.copy(selectedId = ex.id, count = lastToday?.minutes ?: prev?.minutes ?: 20)
            }
        }
    }

    fun setWeightText(text: String) {
        val cleaned = text.replace(',', '.').filter { it.isDigit() || it == '.' }
        if (cleaned.count { it == '.' } > 1 || cleaned.length > 6) return
        _ui.update { it.copy(weightText = cleaned) }
    }

    fun bump(delta: Int) = _ui.update { it.copy(count = (it.count + delta).coerceIn(0, 999)) }

    /** 「この内容でセット追加」。重量と回数はそのまま残るので、同じ内容なら続けて押すだけ */
    fun addSet() {
        val u = _ui.value
        val ex = u.selected ?: return
        val row = if (ex.category == ExerciseCategory.STRENGTH) {
            DraftRow(nextKey++, ex.id, weightKg = u.weightText.toFloatOrNull() ?: 0f, reps = u.count)
        } else {
            DraftRow(nextKey++, ex.id, minutes = u.count)
        }
        _ui.update { it.copy(rows = it.rows + row) }
    }

    /** 数値を入れずに種目だけ記録する */
    fun addExerciseOnly() {
        val ex = _ui.value.selected ?: return
        if (_ui.value.rows.any { it.exerciseId == ex.id }) return
        _ui.update { it.copy(rows = it.rows + DraftRow(nextKey++, ex.id)) }
    }

    fun updateRow(row: DraftRow) = _ui.update { u -> u.copy(rows = u.rows.map { if (it.key == row.key) row else it }) }

    fun deleteRow(key: Long) = _ui.update { u -> u.copy(rows = u.rows.filterNot { it.key == key }) }

    /** 「前回と同じ」：前回の種目と数値をまるごとコピーする */
    fun copyLast() {
        val last = _ui.value.lastWorkout ?: return
        _ui.update { it.copy(rows = toRows(last.items), activeGroupId = null, sessionIds = null) }
        last.items.firstOrNull()?.let { item -> _ui.value.exercise(item.exerciseId)?.let(::select) }
    }

    // ---- マイメニュー ----

    fun applyGroup(group: WorkoutGroup) {
        if (_ui.value.activeGroupId == group.id) {
            // もう一度タップしたら全種目に戻す
            _ui.update { it.copy(activeGroupId = null, sessionIds = null) }
            return
        }
        viewModelScope.launch {
            val owned = _ui.value.exercises.associateBy { it.id }
            val ids = group.exerciseIds.filter { it in owned }
            // 呼び出したメニューに、チップから外していた種目があれば戻す
            ids.mapNotNull { owned[it] }.filter { it.hidden }.forEach { repo.setHidden(it, false) }
            _ui.update { it.copy(activeGroupId = group.id, sessionIds = ids, pickerOpen = false) }
            ids.firstOrNull()?.let { id -> owned[id]?.let(::select) }
        }
    }

    fun createGroup(name: String) = viewModelScope.launch {
        repo.createGroup(name, _ui.value.chips.map { it.id })
    }

    fun overwriteGroup(group: WorkoutGroup) = viewModelScope.launch {
        repo.overwriteGroup(group.id, _ui.value.chips.map { it.id })
    }

    fun renameGroup(group: WorkoutGroup, name: String) = viewModelScope.launch { repo.renameGroup(group.id, name) }

    fun deleteGroup(group: WorkoutGroup) = viewModelScope.launch {
        if (_ui.value.activeGroupId == group.id) _ui.update { it.copy(activeGroupId = null, sessionIds = null) }
        repo.deleteGroup(group.id)
    }

    fun moveGroup(group: WorkoutGroup, delta: Int) = viewModelScope.launch { repo.moveGroup(group.id, delta) }

    // ---- 種目の追加・削除 ----

    fun togglePicker() = _ui.update { it.copy(pickerOpen = !it.pickerOpen) }

    fun setPickerPart(part: BodyPart) = _ui.update { it.copy(pickerPart = part) }

    /** 選んでいる部位の候補（外した種目＋まだ追加していない同梱の種目） */
    fun candidates(u: WorkoutUi): List<Candidate> {
        val shownIds = u.chips.map { it.id }.toSet()
        val ownedByName = u.exercises.associateBy { it.name }
        val removed = u.exercises
            .filter { it.bodyPart == u.pickerPart && it.id !in shownIds }
            .map { Candidate(it.name, it.category, it.bodyPart, it) }
        val fresh = catalog.candidates
            .filter { it.bodyPart == u.pickerPart && it.name !in ownedByName }
            .map { c: CatalogExercise -> Candidate(c.name, c.category, c.bodyPart, null) }
        return removed + fresh
    }

    fun addCandidate(c: Candidate) = viewModelScope.launch {
        val ex = repo.addExercise(c.name, c.category, c.bodyPart)
        _ui.update { u ->
            u.copy(
                sessionIds = u.sessionIds?.let { if (ex.id in it) it else it + ex.id },
                exercises = if (u.exercises.any { it.id == ex.id }) u.exercises.map { if (it.id == ex.id) ex else it } else u.exercises + ex,
                pickerOpen = false,
            )
        }
        select(ex)
    }

    /** 一覧にない種目を名前で追加する。有酸素の部位なら時間で記録する種目にする */
    fun addCustom(name: String) {
        if (name.isBlank()) return
        val part = _ui.value.pickerPart
        val category = if (part == BodyPart.CARDIO) ExerciseCategory.CARDIO else ExerciseCategory.STRENGTH
        addCandidate(Candidate(name.trim(), category, part, null))
    }

    /** 「＋種目」を開いているときのチップの×。チップから外すだけで、過去の記録は残る */
    fun removeChip(ex: ExerciseEntity) = viewModelScope.launch {
        val u = _ui.value
        if (u.sessionIds != null) {
            _ui.update { it.copy(sessionIds = it.sessionIds?.minus(ex.id)) }
        } else {
            repo.setHidden(ex, true)
            _ui.update { s -> s.copy(exercises = s.exercises.map { if (it.id == ex.id) it.copy(hidden = true) else it }) }
        }
        if (_ui.value.selectedId == ex.id) {
            val first = _ui.value.chips.firstOrNull()
            if (first != null) select(first) else _ui.update { it.copy(selectedId = null) }
        }
    }

    // ---- 保存 ----

    fun save(onDone: () -> Unit) {
        val u = _ui.value
        val visitId = u.visitId ?: return
        _ui.update { it.copy(saving = true) }
        viewModelScope.launch {
            repo.saveDay(visitId, toLogged(u.rows, u))
            onDone()
        }
    }

    private fun toLogged(rows: List<DraftRow>, u: WorkoutUi): List<LoggedExercise> {
        val order = rows.map { it.exerciseId }.distinct()
        return order.map { id ->
            val mine = rows.filter { it.exerciseId == id }
            val isCardio = u.exercise(id)?.category == ExerciseCategory.CARDIO
            if (isCardio) {
                val mins = mine.mapNotNull { it.minutes }
                LoggedExercise(id, minutes = if (mins.isEmpty()) null else mins.sum())
            } else {
                LoggedExercise(id, sets = mine.filter { it.reps != null }.map { LoggedSet(it.weightKg ?: 0f, it.reps ?: 0) })
            }
        }
    }

    private fun toRows(items: List<LoggedExercise>): List<DraftRow> = items.flatMap { item ->
        when {
            item.sets.isNotEmpty() -> item.sets.map { DraftRow(nextKey++, item.exerciseId, weightKg = it.weightKg, reps = it.reps) }
            item.minutes != null -> listOf(DraftRow(nextKey++, item.exerciseId, minutes = item.minutes))
            else -> listOf(DraftRow(nextKey++, item.exerciseId))
        }
    }

    /** 種目ごとの前回の値。「前回の重量」は最新のセットから取り出す（別に保存しない） */
    private fun prevValues(past: List<DayWorkout>): Map<String, PrevValue> {
        val result = mutableMapOf<String, PrevValue>()
        for (day in past) { // 新しい日から
            for (item in day.items) {
                if (item.exerciseId in result) continue
                val lastSet = item.sets.lastOrNull()
                when {
                    lastSet != null -> result[item.exerciseId] = PrevValue(weightKg = lastSet.weightKg, reps = lastSet.reps)
                    item.minutes != null -> result[item.exerciseId] = PrevValue(minutes = item.minutes)
                }
            }
        }
        return result
    }
}
