package com.oashobi.musclehabit.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oashobi.musclehabit.data.BodyRepository
import com.oashobi.musclehabit.data.DayWorkout
import com.oashobi.musclehabit.data.SettingsRepository
import com.oashobi.musclehabit.data.VisitRepository
import com.oashobi.musclehabit.data.WorkoutRepository
import com.oashobi.musclehabit.data.db.BodyRecordEntity
import com.oashobi.musclehabit.data.db.ExerciseEntity
import com.oashobi.musclehabit.shared.model.VisitType
import com.oashobi.musclehabit.shared.usecase.MonthSummary
import com.oashobi.musclehabit.shared.usecase.MonthlyMath
import com.oashobi.musclehabit.shared.usecase.WeekMath
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class Range(val label: String, val months: Long?) {
    MONTH1("1か月", 1),
    MONTH3("3か月", 3),
    ALL("全期間", null),
}

/** 通った回数と体重の重ね表示の期間 */
enum class MonthRange(val label: String, val months: Int) {
    HALF("6か月", 6),
    YEAR("1年", 12),
}

data class ProgressUi(
    val range: Range = Range.MONTH3,
    /** 範囲内の体重・体脂肪の記録（古い順） */
    val bodies: List<BodyRecordEntity> = emptyList(),
    val latest: BodyRecordEntity? = null,
    val month: YearMonth = YearMonth.now(),
    val visitedInMonth: Set<Int> = emptySet(),
    val today: LocalDate = LocalDate.now(),
    val streakWeeks: Int = 0,
    val selectedDate: LocalDate? = null,
    val selectedWorkout: DayWorkout? = null,
    /** 選んだ日に行っていれば、そのチェックインのID（トレーニングの編集に使う） */
    val selectedVisitId: String? = null,
    /** 選んだ日を最小目標モードで記録したか */
    val selectedMinimum: Boolean = false,
    val exercises: Map<String, ExerciseEntity> = emptyMap(),
    val monthRange: MonthRange = MonthRange.HALF,
    /** 月ごとの通った日数と体重（古い順） */
    val monthly: List<MonthSummary> = emptyList(),
)

private data class Selection(val range: Range, val month: YearMonth, val date: LocalDate?, val monthRange: MonthRange = MonthRange.HALF)

@HiltViewModel
class ProgressViewModel @Inject constructor(
    body: BodyRepository,
    visits: VisitRepository,
    settings: SettingsRepository,
    workouts: WorkoutRepository,
) : ViewModel() {

    private val selection = MutableStateFlow(Selection(Range.MONTH3, YearMonth.now(), null))

    private val data = combine(body.observe(), visits.observe(), settings.settings) { b, v, s -> Triple(b, v, s) }

    val ui: StateFlow<ProgressUi> = combine(
        selection,
        data,
        workouts.observeHistory(),
        workouts.observeExercises(),
    ) { sel, (bodies, visitList, s), history, exercises ->
        val today = LocalDate.now()
        val from = sel.range.months?.let { today.minusMonths(it) }
        val visitDates = visitList.filter { it.type.countsAsVisit }.map { it.date }.toSet()
        ProgressUi(
            range = sel.range,
            bodies = bodies.filter { from == null || !it.date.isBefore(from) },
            latest = bodies.lastOrNull(),
            month = sel.month,
            visitedInMonth = visitDates.filter { YearMonth.from(it) == sel.month }.map { it.dayOfMonth }.toSet(),
            today = today,
            streakWeeks = WeekMath.streakWeeks(visitDates, today, s.weekStart, s.weeklyGoal),
            selectedDate = sel.date,
            selectedWorkout = sel.date?.let { d -> history.firstOrNull { it.date == d } },
            selectedVisitId = sel.date?.let { d -> visitList.firstOrNull { it.date == d && it.type.countsAsVisit }?.id },
            selectedMinimum = sel.date?.let { d -> visitList.any { it.date == d && it.type == VisitType.MINIMUM } } ?: false,
            exercises = exercises.associateBy { it.id },
            monthRange = sel.monthRange,
            monthly = MonthlyMath.summaries(visitDates, bodies.map { it.date to it.weightKg }, today, sel.monthRange.months),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUi())

    fun setRange(r: Range) = selection.update { it.copy(range = r) }
    fun moveMonth(delta: Long) = selection.update { it.copy(month = it.month.plusMonths(delta)) }
    fun selectDate(d: LocalDate?) = selection.update { it.copy(date = d) }
    fun setMonthRange(r: MonthRange) = selection.update { it.copy(monthRange = r) }
}
