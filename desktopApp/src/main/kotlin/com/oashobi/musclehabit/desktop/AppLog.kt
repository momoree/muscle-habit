package com.oashobi.musclehabit.desktop

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 動作のログ。%APPDATA%\MuscleHabit\app.log に書く（通知を出した・出せなかった、同期が届いた、など）。
 * 大きくなったら古い分を捨てる。
 */
object AppLog {
    private val file: File = File(ConfigStore.defaultFile().parentFile, "app.log")
    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private const val MAX_BYTES = 512 * 1024

    /** アプリとして起動したときだけ書く（テストで本物のログを汚さないため）。main で true にする */
    @Volatile var enabled = false

    @Synchronized
    fun write(message: String) {
        if (!enabled) return
        runCatching {
            file.parentFile.mkdirs()
            if (file.exists() && file.length() > MAX_BYTES) {
                val keep = file.readText(Charsets.UTF_8).takeLast(MAX_BYTES / 2)
                file.writeText(keep, Charsets.UTF_8)
            }
            file.appendText("${LocalDateTime.now().format(fmt)} $message\n", Charsets.UTF_8)
        }
    }
}
