package com.oashobi.musclehabit.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.oashobi.musclehabit.data.db.AppDatabase
import com.oashobi.musclehabit.data.db.BodyPhotoEntity
import com.oashobi.musclehabit.data.db.newId
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 体型写真（フェーズ3）。カメラで撮るか、ギャラリーから選んだ画像を、
 * 向きを直して長い辺 1600px の JPEG にし、アプリ専用領域に保存する。
 */
@Singleton
class PhotoRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    db: AppDatabase,
) {
    companion object {
        private const val MAX_SIDE = 1600
        private val EXIF_DATE = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")
    }

    private val dao = db.bodyPhotoDao()
    private val dir get() = File(context.filesDir, "photos").apply { mkdirs() }

    fun observe(): Flow<List<BodyPhotoEntity>> = dao.observeAll()

    fun file(photo: BodyPhotoEntity): File = File(context.filesDir, photo.filePath)

    /** カメラアプリに渡す書き込み先（撮ったあと save で取り込む） */
    fun newCameraUri(): Uri {
        val f = File(context.cacheDir, "camera").apply { mkdirs() }.let { File(it, "shot.jpg") }
        return FileProvider.getUriForFile(context, "${context.packageName}.files", f)
    }

    /**
     * 画像を取り込む。撮影日は写真の Exif（撮った日時）を使い、なければ今日にする。
     * @return 保存した写真。読めない画像なら null
     */
    suspend fun save(uri: Uri, fallbackDate: LocalDate = LocalDate.now()): BodyPhotoEntity? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val exif = runCatching { resolver.openInputStream(uri)?.use { ExifInterface(it) } }.getOrNull()
        val date = exif?.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?.let { runCatching { LocalDateTime.parse(it, EXIF_DATE).toLocalDate() }.getOrNull() }
            ?: fallbackDate

        // まず大きさだけ読んで、縮小率を決める
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // （inJustDecodeBounds のときは Bitmap を返さず null になるので、結果は bounds で見る）
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return@withContext null

        val rotated = rotate(decoded, exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) ?: ExifInterface.ORIENTATION_NORMAL)
        val scale = MAX_SIDE.toFloat() / maxOf(rotated.width, rotated.height)
        val finalBitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(rotated, (rotated.width * scale).toInt(), (rotated.height * scale).toInt(), true)
        } else {
            rotated
        }

        val id = newId()
        val relative = "photos/$id.jpg"
        File(dir, "$id.jpg").outputStream().use { finalBitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        val photo = BodyPhotoEntity(id = id, date = date, filePath = relative)
        dao.upsert(photo)
        photo
    }

    /** 撮影日を直す（ギャラリーの写真で日付がずれていたときなど） */
    suspend fun setDate(photo: BodyPhotoEntity, date: LocalDate) =
        dao.upsert(photo.copy(date = date, updatedAt = Instant.now(), synced = false))

    /** 削除。行には印を付け、画像ファイルは消す */
    suspend fun delete(photo: BodyPhotoEntity) = withContext(Dispatchers.IO) {
        file(photo).delete()
        dao.upsert(photo.copy(deleted = true, updatedAt = Instant.now(), synced = false))
    }

    private fun rotate(b: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return b
        }
        return Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
    }
}

/** 画像ファイルを縮小して読む（一覧のサムネイル用） */
fun decodeSampled(file: File, reqSide: Int): Bitmap? {
    if (!file.exists()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= reqSide) sample *= 2
    return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
}
