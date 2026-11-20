package com.oashobi.musclehabit.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
) {
    val checkedIn: Boolean get() = todayVisit?.type?.countsAsVisit == true
    val passed: Boolean get() = todayVisit != null && !checkedIn
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    visits: VisitRepository,
    schedules: ScheduleRepository,
    settings: SettingsRepository,
    workouts: WorkoutRepository,
    body: BodyRepository,
    private val actions: CheckInActions,
) : ViewModel() {

    /** 日付が変わったときに画面を更新するため、画面に戻るたびに今日を入れ直す */
    private val today = MutableStateFlow(LocalDate.now())

    private val base = combine(visits.observe(), schedules.observe(), settings.settings, today) { v, s, st, t ->
        Base(v, s.map { it.toSlot() }, st.weeklyGoal, st.weekStart, st.leadMinutes, t)
    }

    val state: StateFlow<HomeState> = combine(
        base,
        workouts.observeHistory(),
        workouts.observeExercises(),
        body.observe(),
    ) { b, history, exercises, bodies ->
        val visitDates = b.visits.filter { it.type.countsAsVisit }.map { it.date }.toSet()
        val todayVisit = b.visits.firstOrNull { it.date == b.today }
        HomeState(
            loaded = true,
            today = b.today,
            progress = WeekMath.progress(visitDates, b.today, b.weekStart, b.goal),
            todayVisit = todayVisit,
            todaySlot = ReminderMath.todaySlot(b.slots, b.today),
            nextSlotAt = ReminderMath.nextAfterToday(b.slots, b.today),
            leadMinutes = b.lead,
            todayWorkout = history.firstOrNull { it.visitId == todayVisit?.id },
            lastWorkout = history.firstOrNull { it.date.isBefore(b.today) },
            exercises = exercises.associateBy { it.id },
            latestBody = bodies.lastOrNull(),
            hasSchedule = b.slots.any { it.enabled },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun refreshToday() {
        today.value = LocalDate.now()
    }

    /** チェックインして、トレーニング記録のシートを開くために visitId を返す */
    fun checkIn(onDone: (String) -> Unit) {
        viewModelScope.launch { onDone(actions.checkIn().id) }
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
        val goal: Int,
        val weekStart: java.time.DayOfWeek,
        val lead: Int,
        val today: LocalDate,
    )
}
