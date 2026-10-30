package net.nekomo.nex.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * API credentials encrypted with an AES-256-GCM key that never leaves the Android Keystore.
 * Only ciphertext is written to disk; keys are excluded from backups and exports.
 */
class SecureStore(context: Context) {
    private val prefs = context.getSharedPreferences("nex_secure", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun put(name: String, secret: String) {
        val c = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val enc = c.doFinal(secret.toByteArray())
        prefs.edit().putString(name, Base64.encodeToString(c.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(enc, Base64.NO_WRAP)).apply()
    }

    fun get(name: String): String? {
        val v = prefs.getString(name, null) ?: return null
        return runCatching {
            val (iv, data) = v.split(':').let { Base64.decode(it[0], Base64.NO_WRAP) to Base64.decode(it[1], Base64.NO_WRAP) }
            val c = Cipher.getInstance(TRANSFORM).apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
            String(c.doFinal(data))
        }.getOrNull()
    }

    fun remove(name: String) = prefs.edit().remove(name).apply()

    fun clear() = prefs.edit().clear().apply()

    companion object {
        private const val ALIAS = "nex_api_credentials"
        private const val TRANSFORM = "AES/GCM/NoPadding"
        fun apiKeyName(apiId: Long) = "api_key_$apiId"
    }
}
