package net.nekomo.nex.util

import java.io.File
import java.security.MessageDigest

object Hashes {
    fun sha256(file: File): String = file.inputStream().use { input ->
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            md.update(buf, 0, n)
        }
        md.digest().hex()
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).hex()

    fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }
}
