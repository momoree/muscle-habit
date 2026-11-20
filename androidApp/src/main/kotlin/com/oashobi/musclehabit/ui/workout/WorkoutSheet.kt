@file:OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.oashobi.musclehabit.ui.workout

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oashobi.musclehabit.data.WorkoutGroup
import com.oashobi.musclehabit.shared.model.BodyPart
import com.oashobi.musclehabit.shared.model.ExerciseCategory
import com.oashobi.musclehabit.shared.usecase.WorkoutFormat
import com.oashobi.musclehabit.ui.common.shortJp
import com.oashobi.musclehabit.ui.theme.AppColors
import java.time.LocalDate
import kotlinx.coroutines.launch

/**
 * トレーニング記録の入力シート。チェックイン後、またはホームの「トレーニングを記録」から開く。
 * タップ中心で、キーボードはなるべく出さない（回数と時間はボタンだけで入力できる）。
 */
@Composable
fun WorkoutSheet(
    visitId: String,
    date: LocalDate,
    onDismiss: () -> Unit,
) {
    val vm: WorkoutViewModel = hiltViewModel(key = "workout-$visitId")
    LaunchedEffect(visitId) { vm.start(visitId) }
    val u by vm.ui.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val close: () -> Unit = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } }

    var editing by remember { mutableStateOf<DraftRow?>(null) }
    var nameDialog by remember { mutableStateOf<NameDialog?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = AppColors.Background) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 見出しと保存
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("今日のトレーニング", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("${date.shortJp()}チェックイン済み", fontSize = 13.sp, color = AppColors.SubText)
                }
                Button(
                    onClick = { vm.save(close) },
                    enabled = u.loaded && !u.saving,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(44.dp),
                ) { Text("保存", fontWeight = FontWeight.Bold) }
            }

            if (!u.loaded) return@Column

            // マイメニュー
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("マイメニュー（タップで呼び出し・長押しで編集）", fontSize = 12.sp, color = AppColors.SubText)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Pill("前回と同じ", selected = false, enabled = u.lastWorkout != null, onClick = vm::copyLast)
                    u.groups.forEachIndexed { index, g ->
                        GroupPill(
                            group = g,
                            active = g.id == u.activeGroupId,
                            isFirst = index == 0,
                            isLast = index == u.groups.lastIndex,
                            onClick = { vm.applyGroup(g) },
                            onOverwrite = { vm.overwriteGroup(g) },
                            onRename = { nameDialog = NameDialog.Rename(g) },
                            onDelete = { vm.deleteGroup(g) },
                            onMove = { vm.moveGroup(g, it) },
                        )
                    }
                    Pill("＋ 今の種目で作成", selected = false, dashed = true, onClick = { nameDialog = NameDialog.Create })
                }
            }

            // 種目のチップ
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                u.chips.forEach { ex ->
                    if (u.pickerOpen) {
                        Pill("${ex.name} ×", selected = false, onClick = { vm.removeChip(ex) })
                    } else {
                        Pill(ex.name, selected = ex.id == u.selectedId, onClick = { vm.select(ex) })
                    }
                }
                Pill(if (u.pickerOpen) "閉じる" else "＋ 種目", selected = false, dashed = true, onClick = vm::togglePicker)
            }

            if (u.pickerOpen) PickerCard(u, vm)

            // 入力
            u.selected?.let { ex -> InputCard(u, ex.name, ex.category, vm) }

            // 今日の記録
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(AppColors.Surface, RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("今日の記録（タップで修正）", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                if (u.rows.isEmpty()) Text("まだ記録はありません", fontSize = 14.sp, color = AppColors.SubText)
                val setNo = mutableMapOf<String, Int>()
                u.rows.forEach { row ->
                    val ex = u.exercise(row.exerciseId)
                    val title: String
                    val detail: String
                    when {
                        row.reps != null -> {
                            val n = (setNo[row.exerciseId] ?: 0) + 1
                            setNo[row.exerciseId] = n
                            title = "${ex?.name ?: ""}　${n}セット目"
                            detail = "${WorkoutFormat.weight(row.weightKg ?: 0f)} × ${row.reps}回"
                        }
                        row.minutes != null -> {
                            title = ex?.name ?: ""
                            detail = WorkoutFormat.minutes(row.minutes)
                        }
                        else -> {
                            title = ex?.name ?: ""
                            detail = "種目のみ（数値は後から入力できます）"
                        }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(AppColors.Background, RoundedCornerShape(12.dp))
                            .clickable { editing = row }
                            .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(detail, fontSize = 13.sp, color = AppColors.SubText)
                        }
                        IconButton(onClick = { vm.deleteRow(row.key) }) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "この記録を削除", tint = AppColors.SubText)
                        }
                    }
                }
            }
        }
    }

    editing?.let { row ->
        EditRowDialog(
            row = row,
            isCardio = u.exercise(row.exerciseId)?.category == ExerciseCategory.CARDIO,
            onSave = { vm.updateRow(it); editing = null },
            onDelete = { vm.deleteRow(row.key); editing = null },
            onDismiss = { editing = null },
        )
    }

    nameDialog?.let { d ->
        NameInputDialog(
            title = if (d is NameDialog.Rename) "メニュー名を変更" else "今の種目でメニューを作成",
            initial = (d as? NameDialog.Rename)?.group?.name ?: "",
            onDismiss = { nameDialog = null },
            onSave = { name ->
                when (d) {
                    NameDialog.Create -> vm.createGroup(name)
                    is NameDialog.Rename -> vm.renameGroup(d.group, name)
                }
                nameDialog = null
            },
        )
    }
}

private sealed interface NameDialog {
    data object Create : NameDialog
    data class Rename(val group: WorkoutGroup) : NameDialog
}

@Composable
private fun Pill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dashed: Boolean = false,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier
            .heightIn(min = 44.dp)
            .background(if (selected) AppColors.Primary else AppColors.Surface, shape)
            .border(1.dp, if (selected) AppColors.Primary else if (dashed) AppColors.Primary.copy(alpha = 0.5f) else AppColors.Border, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = when {
                !enabled -> AppColors.SubText.copy(alpha = 0.5f)
                selected -> Color.White
                dashed -> AppColors.PrimaryDark
                else -> AppColors.Text
            },
        )
    }
}

@Composable
private fun GroupPill(
    group: WorkoutGroup,
    active: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
    onOverwrite: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onMove: (Int) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(999.dp)
    Box {
        Box(
            Modifier
                .heightIn(min = 44.dp)
                .background(if (active) AppColors.Primary else AppColors.PrimaryLight, shape)
                .combinedClickable(onClick = onClick, onLongClick = { menu = true })
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${group.name}　${group.exerciseIds.size}種目",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (active) Color.White else AppColors.PrimaryDark,
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("今の種目で上書き") }, onClick = { menu = false; onOverwrite() })
            DropdownMenuItem(text = { Text("名前を変更") }, onClick = { menu = false; onRename() })
            if (!isFirst) DropdownMenuItem(text = { Text("左へ移動") }, onClick = { menu = false; onMove(-1) })
            if (!isLast) DropdownMenuItem(text = { Text("右へ移動") }, onClick = { menu = false; onMove(1) })
            DropdownMenuItem(text = { Text("削除", color = AppColors.Danger) }, onClick = { menu = false; confirmDelete = true })
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("「${group.name}」を削除？") },
            text = { Text("メニューだけを削除します。種目と過去の記録は残ります。") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("削除") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("やめる") } },
        )
    }
}

@Composable
private fun PickerCard(u: WorkoutUi, vm: WorkoutViewModel) {
    var custom by remember { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxWidth()
            .background(AppColors.Surface, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("種目を追加（タップで追加）", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text("上の種目をタップすると一覧から外せます。外した種目はここに戻ります", fontSize = 12.sp, color = AppColors.SubText)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BodyPart.entries.forEach { part ->
                Pill(part.label, selected = part == u.pickerPart, onClick = { vm.setPickerPart(part) })
            }
        }
        val candidates = vm.candidates(u)
        if (candidates.isEmpty()) {
            Text("この部位の種目はすべて追加済みです", fontSize = 13.sp, color = AppColors.SubText)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                candidates.forEach { c -> Pill("＋ ${c.name}", selected = false, onClick = { vm.addCandidate(c) }) }
            }
        }
        Text("一覧にない種目だけ、名前を入力して追加できます", fontSize = 12.sp, color = AppColors.SubText)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = custom,
                onValueChange = { custom = it },
                placeholder = { Text("種目名") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { vm.addCustom(custom); custom = "" },
                enabled = custom.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
            ) { Text("追加") }
        }
    }
}

@Composable
private fun InputCard(u: WorkoutUi, name: String, category: ExerciseCategory, vm: WorkoutViewModel) {
    val isStrength = category == ExerciseCategory.STRENGTH
    val prev = u.prev[u.selectedId]
    Column(
        Modifier
            .fillMaxWidth()
            .background(AppColors.Surface, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(name, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            val prevText = when {
                prev == null -> "初めての種目"
                isStrength && prev.reps != null -> "前回：${WorkoutFormat.weight(prev.weightKg ?: 0f)} × ${prev.reps}回"
                prev.minutes != null -> "前回：${prev.minutes}分"
                else -> ""
            }
            Text(prevText, fontSize = 13.sp, color = AppColors.SubText)
        }
        if (isStrength) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("重量", fontSize = 14.sp, color = AppColors.SubText)
                OutlinedTextField(
                    value = u.weightText,
                    onValueChange = vm::setWeightText,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    suffix = { Text("kg") },
                    modifier = Modifier.width(140.dp),
                )
                Text("自重は0kg", fontSize = 12.sp, color = AppColors.SubText)
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(if (isStrength) "回数" else "時間", fontSize = 14.sp, color = AppColors.SubText, modifier = Modifier.padding(bottom = 6.dp))
            Spacer(Modifier.width(12.dp))
            Text("${u.count}", fontSize = 36.sp, fontWeight = FontWeight.Black)
            Text(if (isStrength) " 回" else " 分", fontSize = 16.sp, color = AppColors.SubText, modifier = Modifier.padding(bottom = 6.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(-10, -5, -1, 1, 5, 10).forEach { d ->
                val label = if (d > 0) "+$d" else "−${-d}"
                Box(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .background(if (d > 0) AppColors.PrimaryLight else AppColors.Neutral, RoundedCornerShape(12.dp))
                        .clickable { vm.bump(d) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (d > 0) AppColors.PrimaryDark else AppColors.Text)
                }
            }
        }
        Button(
            onClick = vm::addSet,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(if (isStrength) "この内容でセット追加" else "この内容で追加", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = vm::addExerciseOnly, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("数値なしで種目だけ記録", color = AppColors.SubText, fontSize = 13.sp)
        }
    }
}

@Composable
private fun EditRowDialog(
    row: DraftRow,
    isCardio: Boolean,
    onSave: (DraftRow) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var weight by remember { mutableStateOf(row.weightKg?.let(WorkoutFormat::weightInput) ?: "") }
    var count by remember { mutableStateOf((if (isCardio) row.minutes else row.reps)?.toString() ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("記録を修正") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!isCardio) {
                    OutlinedTextField(
                        value = weight,
                        onValueChange = { v -> weight = v.replace(',', '.').filter { it.isDigit() || it == '.' } },
                        label = { Text("重量（kg、自重は0）") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
                OutlinedTextField(
                    value = count,
                    onValueChange = { v -> count = v.filter { it.isDigit() }.take(3) },
                    label = { Text(if (isCardio) "時間（分）" else "回数") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val n = count.toIntOrNull()
                onSave(
                    if (isCardio) {
                        row.copy(minutes = n)
                    } else {
                        row.copy(weightKg = if (n == null) null else weight.toFloatOrNull() ?: 0f, reps = n)
                    },
                )
            }) { Text("保存") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("削除", color = AppColors.Danger) }
                TextButton(onClick = onDismiss) { Text("やめる") }
            }
        },
    )
}

@Composable
fun NameInputDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, placeholder = { Text("例：上半身の日") }, singleLine = true)
        },
        confirmButton = { TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("やめる") } },
    )
}
