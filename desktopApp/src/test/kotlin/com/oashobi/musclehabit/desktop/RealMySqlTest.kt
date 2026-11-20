package com.oashobi.musclehabit.desktop

import com.oashobi.musclehabit.shared.sync.BodyRecordDto
import com.oashobi.musclehabit.shared.sync.SyncRequest
import com.oashobi.musclehabit.shared.sync.VisitDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * 本物の MySQL（%APPDATA%\MuscleHabit\config.properties の接続先）で試す。
 * 環境変数 MH_REAL_MYSQL=1 のときだけ動く。試しに入れた行（id が test- で始まる）は最後に消す。
 */
class RealMySqlTest {
    @Test
    fun saveToRealMySql() {
        if (System.getenv("MH_REAL_MYSQL") != "1") return
        val c = ConfigStore().load()
        val store = Store.mysql(c)
        store.init()
        val db = Database.connect(
            c.dbUrl + "?connectionTimeZone=LOCAL",
            driver = "com.mysql.cj.jdbc.Driver",
            user = c.dbUser,
            password = c.dbPassword,
        )
        try {
            val saved = store.save(
                SyncRequest(
                    visits = listOf(VisitDto("test-v1", "2020-01-06", 0, "MINIMUM", "日本語メモ", 0, false)),
                    bodyRecords = listOf(BodyRecordDto("test-b1", "2020-01-06", 70.5f, null, 0, false)),
                ),
            )
            assertEquals(2, saved)
            // 同じ id でもう一度（上書き）
            store.save(SyncRequest(bodyRecords = listOf(BodyRecordDto("test-b1", "2020-01-06", 70.1f, 19.0f, 1, false))))
            val snap = store.snapshot()
            assertTrue(snap.visits.any { it.date.toString() == "2020-01-06" })
            assertEquals(70.1f, snap.bodies.first { it.date.toString() == "2020-01-06" }.weightKg)
        } finally {
            transaction(db) {
                Visits.deleteWhere { Visits.id like "test-%" }
                BodyRecords.deleteWhere { BodyRecords.id like "test-%" }
            }
        }
    }
}
