package com.oashobi.musclehabit.desktop

import com.oashobi.musclehabit.shared.sync.BodyRecordDto
import com.oashobi.musclehabit.shared.sync.PairRequest
import com.oashobi.musclehabit.shared.sync.PairResponse
import com.oashobi.musclehabit.shared.sync.ScheduleDto
import com.oashobi.musclehabit.shared.sync.SettingsDto
import com.oashobi.musclehabit.shared.sync.SyncRequest
import com.oashobi.musclehabit.shared.sync.SyncResponse
import com.oashobi.musclehabit.shared.sync.VisitDto
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import java.io.File
import java.nio.file.Files
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.jetbrains.exposed.sql.Database

class DesktopSyncTest {
    private val tmp: File = Files.createTempDirectory("mh").toFile()

    /** H2 の MySQL 互換モードで、MySQL の代わりに試す */
    private fun h2Store(name: String) = Store(
        Database.connect("jdbc:h2:mem:$name;MODE=MySQL;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver"),
    )

    private fun engine(name: String) = AppEngine(
        configStore = ConfigStore(File(tmp, "$name.properties")).also { it.save(AppConfig(dbPassword = "x")) },
        storeFactory = { h2Store(name) },
    ).also { it.connectDatabase() }

    private fun millis(t: LocalDateTime) = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private val now = LocalDateTime.of(2026, 11, 20, 12, 0)

    private fun request(updated: Long = millis(now)) = SyncRequest(
        schedules = listOf(
            ScheduleDto("s1", 1, "19:00", true, updated, false),
            ScheduleDto("s5", 5, "19:00", true, updated, false),
        ),
        visits = listOf(
            VisitDto("v1", "2026-11-16", updated, "NORMAL", null, updated, false),
            VisitDto("v2", "2026-11-18", updated, "MINIMUM", null, updated, false),
            VisitDto("v3", "2026-11-19", updated, "PASS", null, updated, false),
        ),
        bodyRecords = listOf(BodyRecordDto("b1", "2026-11-18", 68.4f, 18.5f, updated, false)),
        settings = SettingsDto(weeklyGoal = 3, leadMinutes = 30, weekStart = 1),
    )

    @Test
    fun saveAndOverwriteBySameId() {
        val e = engine("save")
        assertEquals(6, e.save(request()))
        var snap = e.state.value.snapshot
        assertEquals(listOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), snap.slots.map { it.dayOfWeek })
        // PASS は回数に数えない、MINIMUM は数える
        assertEquals(setOf(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 18)), snap.visitDates)
        assertEquals(68.4f, snap.bodies.single().weightKg)

        // 同じ id で取り消し（deleted）と体重の修正が届く
        e.save(
            SyncRequest(
                visits = listOf(VisitDto("v2", "2026-11-18", 0, "MINIMUM", null, millis(now.plusHours(1)), true)),
                bodyRecords = listOf(BodyRecordDto("b1", "2026-11-18", 68.0f, null, millis(now.plusHours(1)), false)),
            ),
        )
        snap = e.state.value.snapshot
        assertEquals(setOf(LocalDate.of(2026, 11, 16)), snap.visitDates)
        assertEquals(68.0f, snap.bodies.single().weightKg)
        assertNull(snap.bodies.single().bodyFatPercent)
    }

    @Test
    fun notificationBeforeGymAndNotAfterCheckIn() {
        val e = engine("notify")
        e.save(request())
        // 金曜 18:29 はまだ、18:30 に出る、二度は出ない
        assertNull(e.checkNotification(LocalDateTime.of(2026, 11, 20, 18, 29)))
        val n = assertNotNull(e.checkNotification(LocalDateTime.of(2026, 11, 20, 18, 30)))
        assertEquals("今日はジムの日", n.first)
        assertTrue(n.second.startsWith("19:00にジムの予定です"))
        assertNull(e.checkNotification(LocalDateTime.of(2026, 11, 20, 18, 31)))

        // 次の月曜：スマホでチェックイン済みなら出さない
        e.save(SyncRequest(visits = listOf(VisitDto("v4", "2026-11-23", 0, "NORMAL", null, 0, false))))
        assertNull(e.checkNotification(LocalDateTime.of(2026, 11, 23, 18, 40)))
        // スリープ明けで予定時刻を過ぎていたら出さない
        assertNull(e.checkNotification(LocalDateTime.of(2026, 11, 27, 19, 30)))
    }

    @Test
    fun pairingAndSyncOverHttp() = testApplication {
        val e = engine("http")
        application { syncModule(e) }
        val client = createClient { install(ContentNegotiation) { json() } }

        // トークンなしは断る
        val noAuth = client.post("/sync") { contentType(ContentType.Application.Json); setBody(SyncRequest()) }
        assertEquals(HttpStatusCode.Unauthorized, noAuth.status)

        // コードちがいは断る
        val wrong = client.post("/pair") { contentType(ContentType.Application.Json); setBody(PairRequest("000000x", "test")) }
        assertEquals(HttpStatusCode.Forbidden, wrong.status)

        val code = e.state.value.config.pairingCode
        val pair = client.post("/pair") { contentType(ContentType.Application.Json); setBody(PairRequest(code, "Pixel")) }
        assertEquals(HttpStatusCode.OK, pair.status)
        val token = pair.body<PairResponse>().token
        // 使ったコードは変わる
        assertTrue(e.state.value.config.pairingCode != code)

        val sync = client.post("/sync") { bearerAuth(token); contentType(ContentType.Application.Json); setBody(request()) }
        assertEquals(HttpStatusCode.OK, sync.status)
        assertEquals(6, sync.body<SyncResponse>().saved)
        assertEquals(2, e.state.value.snapshot.visitDates.size)
    }
}
