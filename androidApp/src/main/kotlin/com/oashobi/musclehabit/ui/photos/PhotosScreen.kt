package com.oashobi.musclehabit.ui.photos

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.oashobi.musclehabit.data.BodyRepository
import com.oashobi.musclehabit.data.PhotoRepository
import com.oashobi.musclehabit.data.db.BodyPhotoEntity
import com.oashobi.musclehabit.data.decodeSampled
import com.oashobi.musclehabit.shared.usecase.WorkoutFormat
import com.oashobi.musclehabit.ui.common.AppCard
import com.oashobi.musclehabit.ui.common.shortJp
import com.oashobi.musclehabit.ui.theme.AppColors
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PhotoItem(val photo: BodyPhotoEntity, val file: File, val weightKg: Float?)

data class PhotosUi(
    val loaded: Boolean = false,
    /** 新しい順 */
    val photos: List<PhotoItem> = emptyList(),
) {
    val hasThisMonth: Boolean get() = photos.any { YearMonth.from(it.photo.date) == YearMonth.now() }
}

@HiltViewModel
class PhotosViewModel @Inject constructor(
    private val repo: PhotoRepository,
    body: BodyRepository,
) : ViewModel() {

    val ui: StateFlow<PhotosUi> = combine(repo.observe(), body.observe()) { photos, bodies ->
        PhotosUi(
            loaded = true,
            photos = photos.map { p ->
                // 撮影日の前後1週間でいちばん近い体重の記録を添える
                val near = bodies.minByOrNull { abs(ChronoUnit.DAYS.between(it.date, p.date)) }
                    ?.takeIf { abs(ChronoUnit.DAYS.between(it.date, p.date)) <= 7 }
                PhotoItem(p, repo.file(p), near?.weightKg)
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PhotosUi())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun cameraUri(): Uri = repo.newCameraUri()

    fun add(uri: Uri) = viewModelScope.launch {
        _message.value = if (repo.save(uri) != null) "写真を保存しました" else "画像を読み込めませんでした"
    }

    fun delete(p: BodyPhotoEntity) = viewModelScope.launch { repo.delete(p) }

    fun clearMessage() {
        _message.value = null
    }
}

/** フェーズ3：体型写真。2枚を左右に並べて見比べる。推移画面から開く */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotosScreen(vm: PhotosViewModel = hiltViewModel()) {
    val u by vm.ui.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(message) {
        message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); vm.clearMessage() }
    }

    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri?.let(vm::add)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.add(uri)
    }

    // 比べる2枚（左＝前、右＝あと）。選んでいなければ、いちばん古い写真と新しい写真
    var leftId by rememberSaveable { mutableStateOf<String?>(null) }
    var rightId by rememberSaveable { mutableStateOf<String?>(null) }
    var activeSlot by rememberSaveable { mutableStateOf(1) } // 0=左, 1=右（次にタップした写真を入れる側）
    var deleting by remember { mutableStateOf<PhotoItem?>(null) }

    val left = u.photos.firstOrNull { it.photo.id == leftId } ?: u.photos.lastOrNull()
    val right = u.photos.firstOrNull { it.photo.id == rightId } ?: u.photos.firstOrNull()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("体型写真", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "月に1回くらい、同じ場所・同じ服で撮ると変化が分かりやすくなります。写真はこのスマホの中だけに保存し、PC には送りません。",
            fontSize = 13.sp,
            color = AppColors.SubText,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { vm.cameraUri().also { cameraUri = it; camera.launch(it) } },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Outlined.PhotoCamera, null)
                Text("  撮る", fontWeight = FontWeight.Bold)
            }
            FilledTonalButton(
                onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Outlined.PhotoLibrary, null)
                Text("  選ぶ", fontWeight = FontWeight.Bold)
            }
        }
        if (u.loaded && !u.hasThisMonth) {
            Text("今月の写真はまだです", fontSize = 13.sp, color = AppColors.Streak, fontWeight = FontWeight.Bold)
        }

        if (u.photos.size >= 1) {
            AppCard(contentPadding = PaddingValues(12.dp), verticalSpacing = 8) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Slot("前", left, active = activeSlot == 0, onClick = { activeSlot = 0 }, modifier = Modifier.weight(1f))
                    Slot("あと", right, active = activeSlot == 1, onClick = { activeSlot = 1 }, modifier = Modifier.weight(1f))
                }
                if (left != null && right != null && left.photo.id != right.photo.id) {
                    val days = ChronoUnit.DAYS.between(left.photo.date, right.photo.date)
                    val diff = if (left.weightKg != null && right.weightKg != null) "　体重 ${WorkoutFormat.weightDiff(right.weightKg - left.weightKg)}" else ""
                    Text("${days}日の変化$diff", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppColors.PrimaryDark)
                }
                Text("比べる写真を変えるときは、「前」か「あと」をタップしてから下の写真を選んでください", fontSize = 12.sp, color = AppColors.SubText)
            }
        } else if (u.loaded) {
            AppCard {
                Text("まだ写真がありません。「撮る」から1枚目を撮りましょう", fontSize = 14.sp, color = AppColors.SubText)
            }
        }

        // 一覧（3列、新しい順）。長押しで削除
        u.photos.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { item ->
                    val selected = item.photo.id == left?.photo?.id || item.photo.id == right?.photo?.id
                    Column(
                        Modifier
                            .weight(1f)
                            .combinedClickable(
                                onClick = { if (activeSlot == 0) leftId = item.photo.id else rightId = item.photo.id },
                                onLongClick = { deleting = item },
                            ),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        PhotoImage(
                            item.file,
                            300,
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(3f / 4f)
                                .clip(RoundedCornerShape(10.dp))
                                .then(if (selected) Modifier.border(3.dp, AppColors.Primary, RoundedCornerShape(10.dp)) else Modifier),
                        )
                        Text(item.photo.date.shortJp(), fontSize = 11.sp, color = AppColors.SubText)
                    }
                }
                repeat(3 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }

    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("この写真を削除する？") },
            text = { Text("${item.photo.date.shortJp()}の写真を削除します。元に戻せません。") },
            confirmButton = { TextButton(onClick = { vm.delete(item.photo); deleting = null }) { Text("削除", color = AppColors.Danger) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("やめる") } },
        )
    }
}

@Composable
private fun Slot(label: String, item: PhotoItem?, active: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .border(if (active) 3.dp else 1.dp, if (active) AppColors.Primary else AppColors.Neutral, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (item != null) {
            PhotoImage(item.file, 900, Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(8.dp)))
        } else {
            Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f).background(AppColors.Neutral, RoundedCornerShape(8.dp)))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (active) AppColors.Primary else AppColors.SubText, modifier = Modifier.weight(1f))
            Text(item?.photo?.date?.shortJp() ?: "", fontSize = 12.sp, color = AppColors.SubText)
        }
        item?.weightKg?.let { Text("体重 ${WorkoutFormat.weightInput(it)}kg", fontSize = 12.sp, color = AppColors.SubText) }
    }
}

/** 画像ファイルを縮小して表示する */
@Composable
fun PhotoImage(file: File, reqSide: Int, modifier: Modifier) {
    val bitmap by produceState<Bitmap?>(null, file.path, reqSide) {
        value = withContext(Dispatchers.IO) { decodeSampled(file, reqSide) }
    }
    val b = bitmap
    if (b != null) {
        Image(b.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
    } else {
        Box(modifier.background(AppColors.Neutral))
    }
}

/** 推移画面に出す「体型写真」の入口 */
@Composable
fun PhotoEntryCard(onOpen: () -> Unit, vm: PhotosViewModel = hiltViewModel()) {
    val u by vm.ui.collectAsStateWithLifecycle()
    AppCard(contentPadding = PaddingValues(16.dp), verticalSpacing = 10) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("体型写真", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        u.photos.isEmpty() -> "月1回の写真で見た目の変化を比べられます"
                        !u.hasThisMonth -> "${u.photos.size}枚・今月の写真はまだです"
                        else -> "${u.photos.size}枚・最新 ${u.photos.first().photo.date.shortJp()}"
                    },
                    fontSize = 12.sp,
                    color = if (u.photos.isNotEmpty() && !u.hasThisMonth) AppColors.Streak else AppColors.SubText,
                )
            }
            FilledTonalButton(onClick = onOpen, shape = RoundedCornerShape(12.dp)) {
                Text(if (u.photos.size >= 2) "比べる" else "撮る", fontWeight = FontWeight.Bold)
            }
        }
        if (u.photos.size >= 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(u.photos.last(), u.photos.first()).forEach { item ->
                    PhotoImage(item.file, 400, Modifier.weight(1f).aspectRatio(3f / 4f).clip(RoundedCornerShape(10.dp)))
                }
            }
        }
    }
}
