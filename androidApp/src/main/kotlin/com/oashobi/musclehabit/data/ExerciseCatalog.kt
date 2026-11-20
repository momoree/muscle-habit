package com.oashobi.musclehabit.data

import android.content.Context
import com.oashobi.musclehabit.shared.model.BodyPart
import com.oashobi.musclehabit.shared.model.ExerciseCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

/** アプリに同梱する種目（assets/exercises.json）。アプリ更新で増やせるよう、コードではなく JSON に置く。 */
data class CatalogExercise(
    val name: String,
    val bodyPart: BodyPart,
    val category: ExerciseCategory,
)

@Singleton
class ExerciseCatalog @Inject constructor(
    @ApplicationContext context: Context,
) {
    /** 初回に登録してチップに並べる種目 */
    val initial: List<CatalogExercise>

    /** 「＋種目」で部位ごとに出す候補 */
    val candidates: List<CatalogExercise>

    init {
        val json = JSONObject(context.assets.open("exercises.json").bufferedReader().use { it.readText() })
        val init = json.getJSONArray("initial")
        initial = (0 until init.length()).map { i ->
            val o = init.getJSONObject(i)
            CatalogExercise(
                name = o.getString("name"),
                bodyPart = BodyPart.valueOf(o.getString("bodyPart")),
                category = ExerciseCategory.valueOf(o.getString("category")),
            )
        }
        val cands = json.getJSONObject("candidates")
        candidates = BodyPart.entries.flatMap { part ->
            val byCategory = cands.optJSONObject(part.name) ?: return@flatMap emptyList()
            ExerciseCategory.entries.flatMap { cat ->
                val arr = byCategory.optJSONArray(cat.name) ?: return@flatMap emptyList()
                (0 until arr.length()).map { CatalogExercise(arr.getString(it), part, cat) }
            }
        }
    }
}
