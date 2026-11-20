package com.oashobi.musclehabit.desktop

import com.oashobi.musclehabit.shared.sync.SYNC_DEFAULT_PORT
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.security.SecureRandom
import java.util.Properties

/**
 * PC アプリの設定。%APPDATA%\MuscleHabit\config.properties に保存する。
 * DB のパスワードもここに入る（自分の PC のユーザーフォルダの中だけ）。
 */
data class AppConfig(
    val dbUrl: String = "jdbc:mysql://localhost:3306/musclehabit",
    val dbUser: String = "musclehabit",
    val dbPassword: String = "",
    val port: Int = SYNC_DEFAULT_PORT,
    /** ペアリング済みのスマホに渡したトークン */
    val tokens: Set<String> = emptySet(),
    /** スマホに入力してもらう6桁のコード */
    val pairingCode: String = newPairingCode(),
)

private val random = SecureRandom()

fun newPairingCode(): String = (0 until 6).joinToString("") { random.nextInt(10).toString() }

fun newToken(): String = ByteArray(24).also(random::nextBytes).joinToString("") { "%02x".format(it) }

class ConfigStore(private val file: File = defaultFile()) {

    companion object {
        fun defaultFile(): File {
            val base = System.getenv("APPDATA")?.let(::File) ?: File(System.getProperty("user.home"), ".config")
            return File(base, "MuscleHabit/config.properties")
        }
    }

    fun load(): AppConfig {
        if (!file.exists()) return AppConfig().also(::save)
        val p = Properties().apply { file.inputStream().use { load(it.reader(Charsets.UTF_8)) } }
        val d = AppConfig()
        return AppConfig(
            dbUrl = p.getProperty("db.url") ?: d.dbUrl,
            dbUser = p.getProperty("db.user") ?: d.dbUser,
            dbPassword = p.getProperty("db.password") ?: d.dbPassword,
            port = p.getProperty("server.port")?.toIntOrNull() ?: d.port,
            tokens = p.getProperty("sync.tokens")?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: emptySet(),
            pairingCode = p.getProperty("sync.pairingCode") ?: d.pairingCode,
        )
    }

    fun save(c: AppConfig) {
        file.parentFile.mkdirs()
        val p = Properties()
        p["db.url"] = c.dbUrl
        p["db.user"] = c.dbUser
        p["db.password"] = c.dbPassword
        p["server.port"] = c.port.toString()
        p["sync.tokens"] = c.tokens.joinToString(",")
        p["sync.pairingCode"] = c.pairingCode
        file.outputStream().use { p.store(it.writer(Charsets.UTF_8), "MuscleHabit PC") }
    }
}

object Network {
    /** スマホから見えるこの PC の IP アドレス（家の中のアドレスだけ） */
    fun localAddresses(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .flatMap { nic -> nic.inetAddresses.toList().map { nic to it } }
            .filter { (_, a) -> a is Inet4Address && a.isSiteLocalAddress }
            // 仮想アダプター（Hyper-V・VirtualBox・WSL など）は後ろに回す
            .sortedBy { (nic, _) -> if (VIRTUAL.any { nic.displayName.contains(it, ignoreCase = true) }) 1 else 0 }
            .map { (_, a) -> a.hostAddress }
            .distinct()
    }.getOrDefault(emptyList())

    fun pcName(): String = runCatching { InetAddress.getLocalHost().hostName }.getOrDefault("PC")

    private val VIRTUAL = listOf("virtual", "hyper-v", "vmware", "vbox", "wsl", "vethernet", "loopback")
}
