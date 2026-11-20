package com.oashobi.musclehabit.desktop

import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock

/** %APPDATA%\MuscleHabit\app.lock をロックして、PC アプリが1つだけ動くようにする */
object SingleInstance {
    private var lock: FileLock? = null

    fun acquire(): Boolean {
        val file = File(ConfigStore.defaultFile().parentFile, "app.lock").apply { parentFile.mkdirs() }
        val channel = RandomAccessFile(file, "rw").channel
        lock = runCatching { channel.tryLock() }.getOrNull()
        if (lock == null) channel.close()
        return lock != null
    }
}
