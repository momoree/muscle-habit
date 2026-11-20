package com.oashobi.musclehabit.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oashobi.musclehabit.data.AppSettings
import com.oashobi.musclehabit.data.BodyRepository
import com.oashobi.musclehabit.data.DayWorkout
import com.oashobi.musclehabit.data.ScheduleRepository
import com.oashobi.musclehabit.data.SettingsRepository
import com.oashobi.musclehabit.data.VisitRepository
import com.oashobi.musclehabit.data.WorkoutRepository
import com.oashobi.musclehabit.data.db.BodyRecordEntity
import com.oashobi.musclehabit.data.db.ExerciseEntity
import com.oashobi.musclehabit.data.db.VisitEntity
import com.oashobi.musclehabit.data.toSlot
import com.oashobi.musclehabit.domain.CheckInActions
import com.oashobi.musclehabit.shared.model.ScheduleSlot
import com.oashobi.musclehabit.shared.model.VisitType
import com.oashobi.musclehabit.shared.usecase.Nudge
import com.oashobi.musclehabit.shared.usecase.NudgeMath
import com.oashobi.musclehabit.shared.usecase.PrepMath
import com.oashobi.musclehabit.shared.usecase.ReminderMath
import com.oashobi.musclehabit.shared.usecase.WeekMath
import com.oashobi.musclehabit.shared.usecase.WeekProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeState(
    val loaded: Boolean = false,
    val today: LocalDate = LocalDate.now(),
    val progress: WeekProgress? = null,
    val todayVisit: VisitEntity? = null,
    val todaySlot: ScheduleSlot? = null,
    val nextSlotAt: LocalDateTime? = null,
    val leadMinutes: Int = 30,
    val todayWorkout: DayWorkout? = null,
    val lastWorkout: DayWorkout? = null,
    val exercises: Map<String, ExerciseEntity> = emptyMap(),
    val latestBody: BodyRecordEntity? = null,
    val hasSchedule: Boolean = false,
    /** 見出しと一言（未達時の後押し） */
    val nudge: Nudge? = null,
    /** 準備リストを出すジムの日（出さないなら null） */
    val prepDate: LocalDate? = null,
    val prepItems: List<String> = emptyList(),
    val prepChecked: Set<String> = emptySet(),
) {
    val checkedIn: Boolean get() = todayVisit?.type?.countsAsVisit == true
    val passed: Boolean get() = todayVisit != null && !checkedIn
    val minimum: Boolean get() = todayVisit?.type == VisitType.MINIMUM
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    visits: VisitRepository,
    schedules: ScheduleRepository,
    private val settings: SettingsRepository,
    workouts: WorkoutRepository,
    body: BodyRepository,
    private val actions: CheckInActions,
) : ViewModel() {

    /** 日付が変わったときに画面を更新するため、画面に戻るたびに今日を入れ直す */
    private val now = MutableStateFlow(LocalDateTime.now())

    private val base = combine(visits.observe(), schedules.observe(), settings.settings, now) { v, s, st, t ->
        Base(v, s.map { it.toSlot() }, st, t)
    }

    val state: StateFlow<HomeState> = combine(
        base,
        workouts.observeHistory(),
        workouts.observeExercises(),
        body.observe(),
    ) { b, history, exercises, bodies ->
        val visitDates = b.visits.filter { it.type.countsAsVisit }.map { it.date }.toSet()
        val todayVisit = b.visits.firstOrNull { it.date == b.today }
        val progress = WeekMath.progress(visitDates, b.today, b.settings.weekStart, b.settings.weeklyGoal)
        val prepDate = if (b.settings.prepEnabled) PrepMath.cardDate(b.slots, b.now, b.settings.prepTime, todayVisit != null) else null
        HomeState(
            loaded = true,
            today = b.today,
            progress = progress,
            nudge = NudgeMath.home(progress, b.today, todayVisit != null, b.slots),
            todayVisit = todayVisit,
            todaySlot = ReminderMath.todaySlot(b.slots, b.today),
            nextSlotAt = ReminderMath.nextAfterToday(b.slots, b.today),
            leadMinutes = b.settings.leadMinutes,
            todayWorkout = history.firstOrNull { it.visitId == todayVisit?.id },
            lastWorkout = history.firstOrNull { it.date.isBefore(b.today) },
            exercises = exercises.associateBy { it.id },
            latestBody = bodies.lastOrNull(),
            hasSchedule = b.slots.any { it.enabled },
            prepDate = prepDate,
            prepItems = b.settings.prepItems,
            prepChecked = if (prepDate != null && b.settings.prepCheckedFor == prepDate) b.settings.prepChecked else emptySet(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun refreshToday() {
        now.value = LocalDateTime.now()
    }

    /** チェックインして、トレーニング記録のシートを開くために visitId を返す */
    fun checkIn(onDone: (String) -> Unit) {
        viewModelScope.launch { onDone(actions.checkIn().id) }
    }

    /** 最小目標モード。「着いたら成功」なのでトレーニング記録のシートは開かない */
    fun checkInMinimum() {
        viewModelScope.launch { actions.checkIn(type = VisitType.MINIMUM) }
    }

    fun togglePrep(item: String) {
        val date = state.value.prepDate ?: return
        viewModelScope.launch { settings.togglePrepItem(date, item) }
    }

    fun pass() {
        viewModelScope.launch { actions.pass() }
    }

    fun cancelToday() {
        viewModelScope.launch { actions.cancel() }
    }

    private data class Base(
        val visits: List<VisitEntity>,
        val slots: List<ScheduleSlot>,
        val settings: AppSettings,
        val now: LocalDateTime,
    ) {
        val today: LocalDate get() = now.toLocalDate()
    }
}
