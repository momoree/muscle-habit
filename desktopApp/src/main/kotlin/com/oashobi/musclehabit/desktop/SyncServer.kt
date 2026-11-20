package com.oashobi.musclehabit.desktop

import com.oashobi.musclehabit.shared.sync.HealthResponse
import com.oashobi.musclehabit.shared.sync.PairRequest
import com.oashobi.musclehabit.shared.sync.PairResponse
import com.oashobi.musclehabit.shared.sync.SyncRequest
import com.oashobi.musclehabit.shared.sync.SyncResponse
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json

/** 同期サーバーがすること。AppEngine が実装する（テストでは差し替える）。 */
interface SyncBackend {
    fun pcName(): String
    fun databaseOk(): Boolean

    /** コードが合っていればトークンを発行して返す。ちがえば null */
    fun pair(code: String, deviceName: String): String?
    fun authorized(token: String): Boolean

    /** 保存した件数。DB につながらなければ DatabaseUnavailable を投げる */
    fun save(req: SyncRequest): Int
}

class DatabaseUnavailable(cause: Throwable? = null) : Exception(cause)

/**
 * スマホから記録を受け取る API（家のネットワークの中だけ）。
 * - GET  /health  接続確認
 * - POST /pair    ペアリングコードを確かめてトークンを返す
 * - POST /sync    予定・チェックイン・体重・設定をまとめて受け取り、MySQL に保存する
 */
fun Application.syncModule(backend: SyncBackend) {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    routing {
        get("/health") {
            call.respond(HealthResponse(pcName = backend.pcName(), database = backend.databaseOk()))
        }
        post("/pair") {
            val req = call.receive<PairRequest>()
            val token = backend.pair(req.code, req.deviceName)
            if (token == null) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "wrong code"))
            } else {
                call.respond(PairResponse(token = token, pcName = backend.pcName()))
            }
        }
        post("/sync") {
            val token = call.request.headers["Authorization"]?.removePrefix("Bearer ")?.trim()
            if (token == null || !backend.authorized(token)) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "not paired"))
                return@post
            }
            val req = call.receive<SyncRequest>()
            try {
                call.respond(SyncResponse(saved = backend.save(req)))
            } catch (e: DatabaseUnavailable) {
                call.respond(HttpStatusCode.ServiceUnavailable, mapOf("error" to "database"))
            }
        }
    }
}

fun startSyncServer(port: Int, backend: SyncBackend): EmbeddedServer<*, *> =
    embeddedServer(CIO, port = port, host = "0.0.0.0") { syncModule(backend) }.start(wait = false)
