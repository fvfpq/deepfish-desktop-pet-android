package com.deepfish.pet

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object ApiKeyStore {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "deepfish_api_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEY_ENCRYPTED = "api_key_encrypted"

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .run {
                init(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build()
                )
                generateKey()
            }
    }

    fun encrypt(@Suppress("UNUSED_PARAMETER") context: Context, plain: String): String {
        if (plain.isBlank()) return ""
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        val payload = iv + encrypted
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    fun decrypt(context: Context): String {
        val stored = context.getSharedPreferences(PrefsFile, Context.MODE_PRIVATE)
            .getString(KEY_ENCRYPTED, "") ?: return ""
        if (stored.isEmpty()) return ""
        return try {
            val payload = Base64.decode(stored, Base64.NO_WRAP)
            val iv = payload.copyOfRange(0, 12)
            val encrypted = payload.copyOfRange(12, payload.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    fun save(context: Context, plain: String) {
        context.getSharedPreferences(PrefsFile, Context.MODE_PRIVATE).edit()
            .putString(KEY_ENCRYPTED, if (plain.isBlank()) "" else encrypt(context, plain.trim()))
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PrefsFile, Context.MODE_PRIVATE).edit()
            .remove(KEY_ENCRYPTED)
            .apply()
    }

    fun hasKey(context: Context): Boolean = decrypt(context).isNotBlank()

    private const val PrefsFile = "deepfish_api_key_store"
}
