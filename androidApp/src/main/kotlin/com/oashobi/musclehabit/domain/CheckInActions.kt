package com.oashobi.musclehabit.domain

import com.oashobi.musclehabit.data.SettingsRepository
import com.oashobi.musclehabit.data.VisitRepository
import com.oashobi.musclehabit.data.db.VisitEntity
import com.oashobi.musclehabit.notify.AlarmScheduler
import com.oashobi.musclehabit.notify.Notifications
import com.oashobi.musclehabit.shared.model.VisitType
import com.oashobi.musclehabit.shared.usecase.WeekMath
import com.oashobi.musclehabit.shared.usecase.WeekProgress
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** チェックイン・パスと、そのあとの通知の後片付け。画面と通知の両方から使う。 */
@Singleton
class CheckInActions @Inject constructor(
    private val visits: VisitRepository,
    private val settings: SettingsRepository,
    private val scheduler: AlarmScheduler,
    private val notifications: Notifications,
) {
    /** @param type MINIMUM なら最小目標モード（ジムに着いたら成功）で記録する */
    suspend fun checkIn(date: LocalDate = LocalDate.now(), type: VisitType = VisitType.NORMAL): VisitEntity {
        val visit = visits.checkIn(date, type)
        notifications.cancelReminder()
        notifications.cancelPrep()
        scheduler.reschedule()
        return visit
    }

    suspend fun pass(date: LocalDate = LocalDate.now()) {
        visits.pass(date)
        notifications.cancelReminder()
        notifications.cancelPrep()
        scheduler.reschedule()
    }

    suspend fun cancel(date: LocalDate = LocalDate.now()) {
        visits.cancel(date)
        scheduler.reschedule()
    }

    suspend fun weekProgress(today: LocalDate = LocalDate.now()): WeekProgress {
        val s = settings.current()
        val dates = visits.getAll().filter { it.type.countsAsVisit }.map { it.date }.toSet()
        return WeekMath.progress(dates, today, s.weekStart, s.weeklyGoal)
    }
}
