package com.oashobi.musclehabit.shared.model

import java.time.DayOfWeek
import java.time.LocalTime

/** チェックインの種類。PASS は「今日はパス」を押した日で、週の回数には数えない。 */
enum class VisitType {
    NORMAL,

    /** 最小目標モード（フェーズ2）。「ジムに着いたら成功」。回数に数える。 */
    MINIMUM,

    /** 今日はパス。振り返り用に残すだけで、回数には数えない。 */
    PASS;

    val countsAsVisit: Boolean get() = this != PASS
}

enum class ExerciseCategory { STRENGTH, CARDIO }

enum class BodyPart(val label: String) {
    CHEST("胸"),
    BACK("背中"),
    SHOULDER("肩"),
    ARMS("腕"),
    ABS("腹"),
    LEGS("脚・お尻"),
    CARDIO("有酸素"),
}

/** 曜日ごとの予定（1曜日につき1件）。 */
data class ScheduleSlot(
    val dayOfWeek: DayOfWeek,
    val time: LocalTime,
    val enabled: Boolean = true,
)
