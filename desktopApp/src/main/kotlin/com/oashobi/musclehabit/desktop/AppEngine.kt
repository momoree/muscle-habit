package com.oashobi.musclehabit.desktop

import com.oashobi.musclehabit.shared.sync.SyncRequest
import com.oashobi.musclehabit.shared.usecase.NudgeMath
import com.oashobi.musclehabit.shared.usecase.ReminderMath
import com.oashobi.musclehabit.shared.usecase.ReminderText
import com.oashobi.musclehabit.shared.usecase.WeekMath
import io.ktor.server.engine.EmbeddedServer
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 画面に出す状態 */
data class EngineState(
    val config: AppConfig = AppConfig(),
    /** DB につながっているか。つながらないときは理由 */
    val dbConnected: Boolean = false,
    val dbError: String? = null,
    /** 同期サーバーが動いているか。動かないときは理由 */
    val serverRunning: Boolean = false,
    val serverError: String? = null,
    val snapshot: Snapshot = Snapshot(),
    /** 最後にスマホから届いた日時 */
    val lastSyncAt: LocalDateTime? = null,
    val lastSyncDevice: String? = null,
)

/**
 * PC アプリの中身：設定・MySQL・同期サーバー・通知をまとめて持つ。
 * 画面は state を見て表示し、ここの関数を呼んで操作する。
 */
class AppEngine(
    private val configStore: ConfigStore = ConfigStore(),
    private val storeFactory: (AppConfig) -> Store = Store::mysql,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) : SyncBackend {

    private val _state = MutableStateFlow(EngineState(config = configStore.load()))
    val state: StateFlow<EngineState> = _state

    @Volatile private var store: Store? = null
    private var server: EmbeddedServer<*, *>? = null
    private var wrongCodeCount = 0
    private val lock = Any()

    /**
     * 出す通知の順番待ち。画面側（Main）が受け取って、トレイから出す。
     * Compose の TrayState.sendNotification は受け手がいないと捨てるので、裏のスレッドから直接呼ばない。
     */
    private val _notifications = Channel<Pair<String, String>>(Channel.BUFFERED)
    val notifications: Flow<Pair<String, String>> = _notifications.receiveAsFlow()

    fun start() {
        AppLog.write("起動")
        scope.launch { connectDatabase() }
        startServer()
        scope.launch { notifyLoop() }
    }

    fun stop() {
        server?.stop(500, 1000)
    }

    // ---- DB ----

    /** 設定の DB につなぎ、無いテーブルを作って中身を読み込む。 */
    fun connectDatabase() {
        val c = _state.value.config
        if (c.dbPassword.isEmpty()) {
            store = null
            _state.update { it.copy(dbConnected = false, dbError = "MySQL のパスワードが未設定です。「設定」で入力してください") }
            return
        }
        try {
            val s = storeFactory(c)
            s.init()
            store = s
            _state.update { it.copy(dbConnected = true, dbError = null, snapshot = s.snapshot()) }
            AppLog.write("MySQL につながりました")
        } catch (e: Exception) {
            store = null
            _state.update { it.copy(dbConnected = false, dbError = describe(e)) }
        }
    }

    fun saveDatabaseSettings(url: String, user: String, password: String) {
        val c = _state.value.config.copy(dbUrl = url.trim(), dbUser = user.trim(), dbPassword = password)
        configStore.save(c)
        _state.update { it.copy(config = c) }
        scope.launch { connectDatabase() }
    }

    fun refresh() {
        val s = store ?: return connectDatabase()
        try {
            _state.update { it.copy(snapshot = s.snapshot(), dbConnected = true, dbError = null) }
        } catch (e: Exception) {
            _state.update { it.copy(dbConnected = false, dbError = describe(e)) }
        }
    }

    private fun describe(e: Throwable): String {
        val msg = generateSequence(e) { it.cause }.mapNotNull { it.message }.joinToString(" / ")
        return when {
            "Access denied" in msg -> "MySQL のユーザー名かパスワードがちがいます"
            "Unknown database" in msg -> "データベース musclehabit がありません。sql/setup.sql を実行してください"
            "Communications link failure" in msg || "Connection refused" in msg -> "MySQL につながりません。MySQL のサービスが動いているか確かめてください"
            else -> "MySQL につながりません（${msg.take(160)}）"
        }
    }

    // ---- 同期サーバー ----

    private fun startServer() {
        try {
            server = startSyncServer(_state.value.config.port, this)
            _state.update { it.copy(serverRunning = true, serverError = null) }
        } catch (e: Exception) {
            _state.update {
                it.copy(serverRunning = false, serverError = "ポート ${_state.value.config.port} を開けません。MuscleHabit がもう1つ起動していないか確かめてください")
            }
        }
    }

    fun regeneratePairingCode() = synchronized(lock) {
        val c = _state.value.config.copy(pairingCode = newPairingCode())
        configStore.save(c)
        wrongCodeCount = 0
        _state.update { it.copy(config = c) }
    }

    /** つないだスマホを全部外す（トークンを捨てる） */
    fun forgetPhones() = synchronized(lock) {
        val c = _state.value.config.copy(tokens = emptySet())
        configStore.save(c)
        _state.update { it.copy(config = c) }
    }

    override fun pcName(): String = Network.pcName()

    override fun databaseOk(): Boolean = store != null

    override fun pair(code: String, deviceName: String): String? = synchronized(lock) {
        val c = _state.value.config
        if (code != c.pairingCode) {
            // 当てずっぽうで試されないよう、5回まちがえたらコードを変える
            if (++wrongCodeCount >= 5) regeneratePairingCode()
            return null
        }
        val token = newToken()
        // 一度使ったコードは使えないように作り直す
        val updated = c.copy(tokens = c.tokens + token, pairingCode = newPairingCode())
        configStore.save(updated)
        wrongCodeCount = 0
        _state.update { it.copy(config = updated, lastSyncDevice = deviceName) }
        token
    }

    override fun authorized(token: String): Boolean = token in _state.value.config.tokens

    override fun save(req: SyncRequest): Int {
        val s = store ?: run {
            connectDatabase()
            store
        } ?: throw DatabaseUnavailable()
        val saved = try {
            s.save(req)
        } catch (e: Exception) {
            _state.update { it.copy(dbConnected = false, dbError = describe(e)) }
            store = null
            throw DatabaseUnavailable(e)
        }
        _state.update { it.copy(snapshot = s.snapshot(), lastSyncAt = LocalDateTime.now()) }
        AppLog.write("スマホから $saved 件を保存")
        return saved
    }

    // ---- 通知 ----

    @Volatile private var notifiedGymAt: LocalDateTime? = null

    /**
     * 30秒ごとに、通知の時刻を過ぎた予定があるか確かめる。
     * スリープから戻ったときも、その時点で計算し直すので、予定時刻を過ぎていれば出さない。
     * その日にスマホでチェックイン（またはパス）した記録が届いていれば出さない。
     */
    private suspend fun notifyLoop() {
        while (scope.isActive) {
            try {
                checkNotification(LocalDateTime.now())?.let { n ->
                    AppLog.write("通知：${n.first}／${n.second}")
                    _notifications.trySend(n)
                }
            } catch (e: Exception) {
                AppLog.write("通知の確認に失敗：$e")
            }
            delay(30_000)
        }
    }

    /** 今出すべき通知があれば（タイトル, 本文）を返す。テストから呼べるように分けている。 */
    fun checkNotification(now: LocalDateTime): Pair<String, String>? {
        if (store == null) return null
        refresh()
        val snap = _state.value.snapshot
        val r = ReminderMath.next(snap.slots, now, snap.settings.leadMinutes, snap.recordedDates) ?: return null
        if (r.triggerAt.isAfter(now) || r.gymAt == notifiedGymAt) return null
        notifiedGymAt = r.gymAt
        val today = r.gymAt.toLocalDate()
        val p = WeekMath.progress(snap.visitDates, today, snap.settings.weekStart, snap.settings.weeklyGoal)
        return ReminderText.title() to ReminderText.body(r.gymAt, p.remaining, NudgeMath.daysLeft(p, today, false))
    }
}
