package com.oashobi.musclehabit.shared.usecase

/** トレーニング記録の表示用の文字列。 */
object WorkoutFormat {

    /** 60.0 → "60kg"、62.5 → "62.5kg"、0 → "自重"。 */
    fun weight(kg: Float): String {
        if (kg <= 0f) return "自重"
        val rounded = Math.round(kg * 100f) / 100f
        val text = if (rounded == rounded.toLong().toFloat()) rounded.toLong().toString() else rounded.toString()
        return "${text}kg"
    }

    /** 入力欄に入れる数字（"60"、"62.5"、0 は "0"）。 */
    fun weightInput(kg: Float): String {
        val rounded = Math.round(kg * 100f) / 100f
        return if (rounded == rounded.toLong().toFloat()) rounded.toLong().toString() else rounded.toString()
    }

    /**
     * セットの一覧を1行にまとめる。
     * 重量がすべて同じなら「60kg × 10・10・8回」、違えば「60kg×10・62.5kg×8回」。
     */
    fun sets(sets: List<Pair<Float, Int>>): String {
        if (sets.isEmpty()) return ""
        val weights = sets.map { it.first }.distinct()
        return if (weights.size == 1) {
            "${weight(weights[0])} × ${sets.joinToString("・") { it.second.toString() }}回"
        } else {
            sets.joinToString("・") { "${weight(it.first)}×${it.second}" } + "回"
        }
    }

    fun minutes(min: Int): String = "${min}分"

    /** 体重の変化（−1.2kg / +0.4kg / ±0kg）。 */
    fun weightDiff(diff: Float): String {
        val r = Math.round(diff * 10f) / 10f
        return when {
            r > 0f -> "+${r}kg"
            r < 0f -> "−${-r}kg"
            else -> "±0kg"
        }
    }
}
