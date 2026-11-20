package com.oashobi.musclehabit.shared.usecase

import java.time.LocalDate
import java.time.YearMonth

/** ある月の、通った日数と体重（フェーズ3：通った回数と体重の重ね表示）。 */
data class MonthSummary(
    val month: YearMonth,
    /** その月に通った日数（パスは数えない） */
    val visits: Int,
    /** その月の最後に記録した体重（記録がなければ null） */
    val weightKg: Float?,
    /** 前に体重を記録した月からの変化（比べる月がなければ null） */
    val weightChange: Float?,
)

object MonthlyMath {

    /**
     * 今月を含めて直近 months か月分を古い順に返す。
     * 体重は「その月の最後の記録」を使い、記録のない月は空けておく（線はとばして結ぶ）。
     */
    fun summaries(
        visitDates: Set<LocalDate>,
        bodies: List<Pair<LocalDate, Float>>,
        today: LocalDate,
        months: Int,
    ): List<MonthSummary> {
        val current = YearMonth.from(today)
        val lastWeightByMonth = bodies.groupBy { YearMonth.from(it.first) }
            .mapValues { (_, list) -> list.maxBy { it.first }.second }
        val range = (months - 1 downTo 0).map { current.minusMonths(it.toLong()) }
        // 表示範囲より前の最後の体重も、最初の月の変化を出すのに使う
        var previous: Float? = lastWeightByMonth.filterKeys { it < range.first() }.maxByOrNull { it.key }?.value
        return range.map { m ->
            val w = lastWeightByMonth[m]
            val change = if (w != null && previous != null) w - previous!! else null
            if (w != null) previous = w
            MonthSummary(
                month = m,
                visits = visitDates.count { YearMonth.from(it) == m },
                weightKg = w,
                weightChange = change,
            )
        }
    }
}
