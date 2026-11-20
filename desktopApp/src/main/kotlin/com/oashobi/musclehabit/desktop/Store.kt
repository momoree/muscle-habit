package com.oashobi.musclehabit.desktop

import com.oashobi.musclehabit.shared.model.ScheduleSlot
import com.oashobi.musclehabit.shared.model.VisitType
import com.oashobi.musclehabit.shared.sync.SettingsDto
import com.oashobi.musclehabit.shared.sync.SyncRequest
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.javatime.time
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert

/*
 * MySQL のテーブル（sql/schema.sql と同じ形）。Exposed で SQL を組み立てる。
 * 同じ id が届いたら上書きする（MySQL では INSERT ... ON DUPLICATE KEY UPDATE になる）。
 */

object Schedules : Table("schedules") {
    val id = varchar("id", 36)
    val dayOfWeek = integer("day_of_week")
    val time = time("time")
    val enabled = bool("enabled")
    val updatedAt = datetime("updated_at")
    val deleted = bool("deleted").default(false)
    override val primaryKey = PrimaryKey(id)
}

object Visits : Table("visits") {
    val id = varchar("id", 36)
    val date = date("date").index("idx_visits_date")
    val checkedInAt = datetime("checked_in_at")
    val type = varchar("type", 10)
    val memo = varchar("memo", 500).nullable()
    val updatedAt = datetime("updated_at")
    val deleted = bool("deleted").default(false)
    override val primaryKey = PrimaryKey(id)
}

object BodyRecords : Table("body_records") {
    val id = varchar("id", 36)
    val date = date("date").index("idx_body_records_date")
    val weightKg = float("weight_kg")
    val bodyFatPercent = float("body_fat_percent").nullable()
    val updatedAt = datetime("updated_at")
    val deleted = bool("deleted").default(false)
    override val primaryKey = PrimaryKey(id)
}

object AppSettingsTable : Table("app_settings") {
    val name = varchar("name", 50)
    val value = varchar("value", 200)
    override val primaryKey = PrimaryKey(name)
}

/** スマホから届いた設定（まだ届いていなければ初期値） */
data class PcSettings(val weeklyGoal: Int = 3, val leadMinutes: Int = 30, val weekStart: DayOfWeek = DayOfWeek.MONDAY)

data class PcVisit(val date: LocalDate, val type: VisitType)

data class PcBody(val date: LocalDate, val weightKg: Float, val bodyFatPercent: Float?)

/** 閲覧画面と通知に使う、DB の中身ひとそろい */
data class Snapshot(
    val slots: List<ScheduleSlot> = emptyList(),
    val visits: List<PcVisit> = emptyList(),
    val bodies: List<PcBody> = emptyList(),
    val settings: PcSettings = PcSettings(),
) {
    val visitDates: Set<LocalDate> get() = visits.filter { it.type.countsAsVisit }.map { it.date }.toSet()
    val recordedDates: Set<LocalDate> get() = visits.map { it.date }.toSet()
}

/** MySQL への読み書き。接続できないときは例外を投げる。 */
class Store(private val db: Database) {

    companion object {
        fun mysql(c: AppConfig): Store = Store(
            Database.connect(
                url = c.dbUrl + (if ('?' in c.dbUrl) "&" else "?") + "connectTimeout=3000&connectionTimeZone=LOCAL&characterEncoding=UTF-8",
                driver = "com.mysql.cj.jdbc.Driver",
                user = c.dbUser,
                password = c.dbPassword,
            ),
        )
    }

    private val zone = ZoneId.systemDefault()
    private fun Long.toLocal(): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(this), zone)

    /** 無いテーブルを作る（CREATE TABLE IF NOT EXISTS）。接続確認も兼ねる。 */
    fun init() = transaction(db) {
        SchemaUtils.create(Schedules, Visits, BodyRecords, AppSettingsTable)
    }

    /** スマホから届いた分を保存し、保存した件数を返す。 */
    fun save(req: SyncRequest): Int = transaction(db) {
        req.schedules.forEach { s ->
            Schedules.upsert {
                it[id] = s.id
                it[dayOfWeek] = s.dayOfWeek
                it[time] = LocalTime.parse(s.time)
                it[enabled] = s.enabled
                it[updatedAt] = s.updatedAt.toLocal()
                it[deleted] = s.deleted
            }
        }
        req.visits.forEach { v ->
            Visits.upsert {
                it[id] = v.id
                it[date] = LocalDate.parse(v.date)
                it[checkedInAt] = v.checkedInAt.toLocal()
                it[type] = v.type
                it[memo] = v.memo
                it[updatedAt] = v.updatedAt.toLocal()
                it[deleted] = v.deleted
            }
        }
        req.bodyRecords.forEach { b ->
            BodyRecords.upsert {
                it[id] = b.id
                it[date] = LocalDate.parse(b.date)
                it[weightKg] = b.weightKg
                it[bodyFatPercent] = b.bodyFatPercent
                it[updatedAt] = b.updatedAt.toLocal()
                it[deleted] = b.deleted
            }
        }
        req.settings?.let { saveSettings(it) }
        req.schedules.size + req.visits.size + req.bodyRecords.size
    }

    private fun saveSettings(s: SettingsDto) {
        mapOf("weekly_goal" to s.weeklyGoal, "lead_minutes" to s.leadMinutes, "week_start" to s.weekStart).forEach { (k, v) ->
            AppSettingsTable.upsert {
                it[name] = k
                it[value] = v.toString()
            }
        }
    }

    fun snapshot(): Snapshot = transaction(db) {
        // 同じ曜日の予定が複数あれば（スマホを入れ直したときなど）、最後に変更したものを使う
        val slots = Schedules.selectAll().where { Schedules.deleted eq false }
            .map { Triple(it[Schedules.dayOfWeek], it[Schedules.updatedAt], ScheduleSlot(DayOfWeek.of(it[Schedules.dayOfWeek]), it[Schedules.time], it[Schedules.enabled])) }
            .groupBy { it.first }
            .map { (_, rows) -> rows.maxBy { it.second }.third }
            .sortedBy { it.dayOfWeek }
        val visits = Visits.selectAll().where { Visits.deleted eq false }
            .map { PcVisit(it[Visits.date], runCatching { VisitType.valueOf(it[Visits.type]) }.getOrDefault(VisitType.NORMAL)) }
        val bodies = BodyRecords.selectAll().where { BodyRecords.deleted eq false }
            .map { PcBody(it[BodyRecords.date], it[BodyRecords.weightKg], it[BodyRecords.bodyFatPercent]) }
            // 同じ日が複数あれば最後のもの
            .associateBy { it.date }.values.sortedBy { it.date }
        val kv = AppSettingsTable.selectAll().associate { it[AppSettingsTable.name] to it[AppSettingsTable.value].toIntOrNull() }
        val d = PcSettings()
        Snapshot(
            slots = slots,
            visits = visits,
            bodies = bodies,
            settings = PcSettings(
                weeklyGoal = kv["weekly_goal"] ?: d.weeklyGoal,
                leadMinutes = kv["lead_minutes"] ?: d.leadMinutes,
                weekStart = kv["week_start"]?.let { runCatching { DayOfWeek.of(it) }.getOrNull() } ?: d.weekStart,
            ),
        )
    }
}
