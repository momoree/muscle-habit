package com.oashobi.musclehabit.desktop

import java.util.concurrent.TimeUnit

/**
 * Windows のログイン時に自動で起動する設定。
 * HKCU\Software\Microsoft\Windows\CurrentVersion\Run に登録する（管理者権限は不要）。
 * インストールした MuscleHabit.exe から起動したときだけ使える（gradlew run では exe のパスがない）。
 */
object AutoStart {
    private const val KEY = """HKCU\Software\Microsoft\Windows\CurrentVersion\Run"""
    private const val NAME = "MuscleHabit"
    const val MINIMIZED_ARG = "--minimized"

    /** jpackage で作った exe から起動すると入るパス */
    private val exePath: String? get() = System.getProperty("jpackage.app-path")

    val available: Boolean get() = exePath != null && System.getProperty("os.name").startsWith("Windows")

    fun isEnabled(): Boolean = available && reg("query", KEY, "/v", NAME) == 0

    fun setEnabled(on: Boolean): Boolean {
        val exe = exePath ?: return false
        val code = if (on) {
            // 値は "C:\...\MuscleHabit.exe" --minimized。Java は空白を含む引数を "..." で囲むので、
            // 中の引用符は \" と書いて reg.exe にそのまま渡す
            reg("add", KEY, "/v", NAME, "/t", "REG_SZ", "/d", "\\\"$exe\\\" $MINIMIZED_ARG", "/f")
        } else {
            reg("delete", KEY, "/v", NAME, "/f")
        }
        return code == 0
    }

    private fun reg(vararg args: String): Int = runCatching {
        val p = ProcessBuilder(listOf("reg.exe") + args).redirectErrorStream(true).start()
        p.inputStream.readAllBytes()
        if (p.waitFor(5, TimeUnit.SECONDS)) p.exitValue() else -1
    }.getOrDefault(-1)
}
