package net.nekomo.nex.util

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Passphrase encryption for the optional API-key section of a backup (PBKDF2-SHA256 + AES-256-GCM). */
object BackupCrypto {
    private const val ITERATIONS = 150_000

    private fun key(pass: CharArray, salt: ByteArray): SecretKeySpec {
        val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(pass, salt, ITERATIONS, 256)).encoded
        return SecretKeySpec(raw, "AES")
    }

    fun encrypt(plain: String, pass: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(pass.toCharArray(), salt), GCMParameterSpec(128, iv)) }
        fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
        return b64(salt) + ":" + b64(iv) + ":" + b64(c.doFinal(plain.toByteArray()))
    }

    /** Returns null when the passphrase is wrong or the blob is damaged. */
    fun decrypt(blob: String, pass: String): String? = runCatching {
        val p = blob.split(':')
        val salt = Base64.decode(p[0], Base64.NO_WRAP)
        val iv = Base64.decode(p[1], Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(pass.toCharArray(), salt), GCMParameterSpec(128, iv)) }
        String(c.doFinal(Base64.decode(p[2], Base64.NO_WRAP)))
    }.getOrNull()
}
