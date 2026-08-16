package com.middleproject.tripcopilot.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.middleproject.tripcopilot.domain.DeviceCredential
import com.middleproject.tripcopilot.domain.DeviceTokenStore
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Keystore AES-GCM credential storage. Only the ciphertext and IV live in
 * private preferences; the AES-256 key never leaves the Keystore. No credential is
 * ever logged, placed in a source/resource/build-config, or written to a public store.
 */
class AndroidDeviceTokenStore(context: Context) : DeviceTokenStore {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): DeviceCredential? {
        val ciphertext = prefs.getString(KEY_CIPHERTEXT, null) ?: return null
        val iv = prefs.getString(KEY_IV, null) ?: return null
        val expiresAtEpochMillis = prefs.getLong(KEY_EXPIRES_AT, 0L)
        if (expiresAtEpochMillis <= 0L) return null
        return try {
            val token = decrypt(ciphertext, iv)
            DeviceCredential(token, expiresAtEpochMillis)
        } catch (e: Exception) {
            // A corrupt/foreign Keystore entry must not crash pairing flows.
            null
        }
    }

    override fun save(credential: DeviceCredential) {
        val (ciphertext, iv) = encrypt(credential.token)
        prefs.edit()
            .putString(KEY_CIPHERTEXT, ciphertext)
            .putString(KEY_IV, iv)
            .putLong(KEY_EXPIRES_AT, credential.expiresAtEpochMillis)
            .apply()
    }

    override fun clear() {
        prefs.edit().remove(KEY_CIPHERTEXT).remove(KEY_IV).remove(KEY_EXPIRES_AT).apply()
        deleteKeyIfPresent()
    }

    private fun deleteKeyIfPresent() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
            }
        } catch (_: Exception) {
            // Keystore cleanup is best-effort; the credential is already removed.
        }
    }

    private fun encrypt(plaintext: String): Pair<String, String> {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val ciphertext = Base64.encodeToString(cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        return ciphertext to iv
    }

    private fun decrypt(ciphertextB64: String, ivB64: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(GCM_TAG_BITS, Base64.decode(ivB64, Base64.NO_WRAP))
        )
        val plaintext = cipher.doFinal(Base64.decode(ciphertextB64, Base64.NO_WRAP))
        return String(plaintext, Charsets.UTF_8)
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFS_NAME = "trip_copilot_device"
        const val KEY_CIPHERTEXT = "device_token_ciphertext"
        const val KEY_IV = "device_token_iv"
        const val KEY_EXPIRES_AT = "device_token_expires_at_epoch_millis"
        const val KEY_ALIAS = "trip_copilot_device_token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }
}
