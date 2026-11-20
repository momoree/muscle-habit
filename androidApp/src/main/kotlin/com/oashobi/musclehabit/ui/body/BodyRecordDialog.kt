package com.oashobi.musclehabit.ui.body

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oashobi.musclehabit.data.BodyRepository
import com.oashobi.musclehabit.shared.usecase.WorkoutFormat
import com.oashobi.musclehabit.ui.common.longJp
import com.oashobi.musclehabit.ui.theme.AppColors
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class BodyViewModel @Inject constructor(private val repo: BodyRepository) : ViewModel() {
    /** 入力欄の初期値（その日の記録、なければ最後の記録） */
    suspend fun initial(date: LocalDate): Pair<String, String> {
        val r = repo.getByDate(date) ?: repo.latest()
        return (r?.weightKg?.let(WorkoutFormat::weightInput) ?: "") to (r?.bodyFatPercent?.let(WorkoutFormat::weightInput) ?: "")
    }

    fun save(date: LocalDate, weight: Float, fat: Float?, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.save(date, weight, fat)
            onDone()
        }
    }
}

/** 体重と体脂肪率（任意）の入力。1日1件で、同じ日は上書き。 */
@Composable
fun BodyRecordDialog(
    onDismiss: () -> Unit,
    askAfterCheckIn: Boolean = false,
    date: LocalDate = LocalDate.now(),
    vm: BodyViewModel = hiltViewModel(),
) {
    var weight by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    LaunchedEffect(date) {
        val (w, f) = vm.initial(date)
        weight = w
        fat = f
    }
    val w = weight.toFloatOrNull()
    val valid = w != null && w in 20f..300f && (fat.isEmpty() || fat.toFloatOrNull()?.let { it in 1f..80f } == true)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (askAfterCheckIn) "体重も記録する？" else "体重を記録") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(date.longJp(), fontSize = 13.sp, color = AppColors.SubText)
                OutlinedTextField(
                    value = weight,
                    onValueChange = { v -> weight = v.replace(',', '.').filter { it.isDigit() || it == '.' }.take(5) },
                    label = { Text("体重") },
                    suffix = { Text("kg") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = fat,
                    onValueChange = { v -> fat = v.replace(',', '.').filter { it.isDigit() || it == '.' }.take(4) },
                    label = { Text("体脂肪率（任意）") },
                    suffix = { Text("%") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.save(date, w!!, fat.toFloatOrNull(), onDismiss) }, enabled = valid) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (askAfterCheckIn) "今日はしない" else "やめる") }
        },
    )
}
