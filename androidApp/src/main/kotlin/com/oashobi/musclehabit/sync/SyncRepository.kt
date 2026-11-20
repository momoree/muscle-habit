package com.oashobi.musclehabit.sync

import android.os.Build
import androidx.room.withTransaction
import com.oashobi.musclehabit.data.SettingsRepository
import com.oashobi.musclehabit.data.db.AppDatabase
import com.oashobi.musclehabit.data.db.BodyRecordEntity
import com.oashobi.musclehabit.data.db.ScheduleEntity
import com.oashobi.musclehabit.data.db.VisitEntity
import com.oashobi.musclehabit.shared.sync.BodyRecordDto
import com.oashobi.musclehabit.shared.sync.HealthResponse
import com.oashobi.musclehabit.shared.sync.PairRequest
import com.oashobi.musclehabit.shared.sync.PairResponse
import com.oashobi.musclehabit.shared.sync.SYNC_DEFAULT_PORT
import com.oashobi.musclehabit.shared.sync.ScheduleDto
import com.oashobi.musclehabit.shared.sync.SettingsDto
import com.oashobi.musclehabit.shared.sync.SyncRequest
import com.oashobi.musclehabit.shared.sync.SyncResponse
import com.oashobi.musclehabit.shared.sync.VisitDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

/** 同期の結果。画面に出す文言を持つ。 */
sealed interface SyncResult {
    data class Ok(val sent: Int) : SyncResult
    data class Failed(val message: String, val retry: Boolean) : SyncResult
    data object NotPaired : SyncResult
}

/**
 * スマホ → PC の片方向同期（フェーズ2）。
 * Room の synced = 0 の行（予定・チェックイン・体重）と設定をまとめて PC に送り、受け取られたら送信済みにする。
 * PC は家の Wi-Fi の中だけで動くので、HTTP（暗号化なし）でつなぎ、ペアリングで受け取ったトークンを付ける。
 */
@Singleton
class SyncRepository @Inject constructor(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
) {
    private val dao = db.syncDao()

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout) {
            connectTimeoutMillis = 5_000
            requestTimeoutMillis = 20_000
        }
        expectSuccess = false
    }

    /** "192.168.1.10" や "192.168.1.10:8765" を "192.168.1.10:8765" にそろえる */
    fun normalizeHost(input: String): String {
        val s = input.trim().removePrefix("http://").trimEnd('/')
        return if (':' in s) s else "$s:$SYNC_DEFAULT_PORT"
    }

    /** PC に表示されたペアリングコードでつなぐ。成功したら全部の記録を送り直す。 */
    suspend fun pair(hostInput: String, code: String): Result<String> = runCatching {
        val host = normalizeHost(hostInput)
        val res = try {
            client.post("http://$host/pair") {
                contentType(ContentType.Application.Json)
                setBody(PairRequest(code.trim(), "${Build.MANUFACTURER} ${Build.MODEL}"))
            }
        } catch (e: IOException) {
            throw IllegalStateException("PC につながりません。IP アドレスと、PC アプリが起動しているかを確かめてください")
        }
        when (res.status) {
            HttpStatusCode.OK -> Unit
            HttpStatusCode.Forbidden -> throw IllegalStateException("ペアリングコードがちがいます")
            else -> throw IllegalStateException("PC から想定外の応答がありました（${res.status.value}）")
        }
        val pair = res.body<PairResponse>()
        db.withTransaction {
            dao.resetSchedules()
            dao.resetVisits()
            dao.resetBodyRecords()
        }
        settings.setSyncPairing(host, pair.token, pair.pcName)
        pair.pcName
    }

    suspend fun unpair() = settings.clearSyncPairing()

    /** 未送信の分を送る。送るものがなくても、設定を送って接続を確かめる。 */
    suspend fun syncNow(): SyncResult {
        val s = settings.current()
        val host = s.syncHost
        val token = s.syncToken
        if (host == null || token == null) return SyncResult.NotPaired

        val schedules = dao.pendingSchedules()
        val visits = dao.pendingVisits()
        val bodies = dao.pendingBodyRecords()
        val request = SyncRequest(
            schedules = schedules.map { it.toDto() },
            visits = visits.map { it.toDto() },
            bodyRecords = bodies.map { it.toDto() },
            settings = SettingsDto(s.weeklyGoal, s.leadMinutes, s.weekStart.value),
        )
        val result = try {
            val res: HttpResponse = client.post("http://$host/sync") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            when (res.status) {
                HttpStatusCode.OK -> {
                    res.body<SyncResponse>()
                    db.withTransaction {
                        schedules.forEach { dao.markScheduleSynced(it.id, it.updatedAt) }
                        visits.forEach { dao.markVisitSynced(it.id, it.updatedAt) }
                        bodies.forEach { dao.markBodyRecordSynced(it.id, it.updatedAt) }
                    }
                    SyncResult.Ok(schedules.size + visits.size + bodies.size)
                }
                HttpStatusCode.Unauthorized -> SyncResult.Failed("PC との接続が切れています。つなぎ直してください", retry = false)
                HttpStatusCode.ServiceUnavailable -> SyncResult.Failed("PC のデータベースにつながっていません", retry = true)
                else -> SyncResult.Failed("PC から想定外の応答がありました（${res.status.value}）", retry = true)
            }
        } catch (e: IOException) {
            SyncResult.Failed("PC につながりません（PC が起動していないか、家の Wi-Fi ではありません）", retry = true)
        }
        when (result) {
            is SyncResult.Ok -> settings.setSyncResult(System.currentTimeMillis(), null)
            is SyncResult.Failed -> settings.setSyncResult(null, result.message)
            SyncResult.NotPaired -> Unit
        }
        return result
    }

    /** 接続確認（設定画面から） */
    suspend fun health(): HealthResponse? {
        val host = settings.current().syncHost ?: return null
        return runCatching { client.get("http://$host/health").body<HealthResponse>() }.getOrNull()
    }
}

private fun ScheduleEntity.toDto() = ScheduleDto(
    id = id,
    dayOfWeek = dayOfWeek,
    time = time.toString(),
    enabled = enabled,
    updatedAt = updatedAt.toEpochMilli(),
    deleted = deleted,
)

private fun VisitEntity.toDto() = VisitDto(
    id = id,
    date = date.toString(),
    checkedInAt = checkedInAt.toEpochMilli(),
    type = type.name,
    memo = memo,
    updatedAt = updatedAt.toEpochMilli(),
    deleted = deleted,
)

private fun BodyRecordEntity.toDto() = BodyRecordDto(
    id = id,
    date = date.toString(),
    weightKg = weightKg,
    bodyFatPercent = bodyFatPercent,
    updatedAt = updatedAt.toEpochMilli(),
    deleted = deleted,
)
