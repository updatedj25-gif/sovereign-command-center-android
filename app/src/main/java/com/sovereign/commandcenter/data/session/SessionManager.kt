package com.sovereign.commandcenter.data.session

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.sovereign.commandcenter.data.api.ApiClient
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class OwnerSession(
    val token: String,
    val ownerId: String,
    val expiresAtMillis: Long,
    val displayName: String
)

object SessionManager {
    private const val PREFS_NAME = "sovereign_secure_session_prefs"
    private const val KEY_ALIAS = "SovereignSessionMasterKey"
    private const val KEY_CIPHER_TOKEN = "enc_session_token"
    private const val KEY_CIPHER_IV = "enc_session_iv"
    private const val KEY_OWNER_ID = "session_owner_id"
    private const val KEY_EXPIRES_AT = "session_expires_at"
    private const val KEY_DISPLAY_NAME = "session_display_name"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val GCM_TAG_LENGTH = 128

    @Volatile
    private var inMemorySession: OwnerSession? = null

    

    

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (entry != null) return entry.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance("AES", ANDROID_KEYSTORE)
        val spec = android.security.keystore.KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun encryptToken(plainToken: String): Pair<String, String> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val cipherBytes = cipher.doFinal(plainToken.toByteArray(StandardCharsets.UTF_8))
        val cipherText = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
        return Pair(cipherText, iv)
    }

    private fun decryptToken(cipherText: String, ivText: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = Base64.decode(ivText, Base64.NO_WRAP)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)
        val plainBytes = cipher.doFinal(Base64.decode(cipherText, Base64.NO_WRAP))
        return String(plainBytes, StandardCharsets.UTF_8)
    }

    @Synchronized
    fun saveSession(context: Context, session: OwnerSession) {
        inMemorySession = session
        try {
            val (encToken, iv) = encryptToken(session.token)
            val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_CIPHER_TOKEN, encToken)
                .putString(KEY_CIPHER_IV, iv)
                .putString(KEY_OWNER_ID, session.ownerId)
                .putLong(KEY_EXPIRES_AT, session.expiresAtMillis)
                .putString(KEY_DISPLAY_NAME, session.displayName)
                .apply()
        } catch (_: Exception) {
            // Keep in-memory session if keystore operation encounters platform quirks
        }
    }

    @Synchronized
    fun loadSession(context: Context): OwnerSession? {
        val current = inMemorySession
        if (current != null && isSessionValid(current)) {
            return current
        }

        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val encToken = prefs.getString(KEY_CIPHER_TOKEN, null) ?: return null
            val iv = prefs.getString(KEY_CIPHER_IV, null) ?: return null
            val ownerId = prefs.getString(KEY_OWNER_ID, null) ?: return null
            val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
            val displayName = prefs.getString(KEY_DISPLAY_NAME, "CEO") ?: "CEO"

            if (System.currentTimeMillis() >= expiresAt) {
                clearSession(context)
                return null
            }

            val token = decryptToken(encToken, iv)
            val restored = OwnerSession(
                token = token,
                ownerId = ownerId,
                expiresAtMillis = expiresAt,
                displayName = displayName
            )
            inMemorySession = restored
            return restored
        } catch (_: Exception) {
            clearSession(context)
            return null
        }
    }

    fun isSessionValid(session: OwnerSession? = inMemorySession): Boolean {
        if (session == null) return false
        return System.currentTimeMillis() < session.expiresAtMillis
    }

    @Synchronized
    fun getAuthCookieHeader(context: Context? = null): String? {
        val session = inMemorySession ?: (context?.let { loadSession(it) })
        if (session != null && isSessionValid(session)) {
            return "sovereign_owner_session=${session.token}"
        }
        return null
    }

    @Synchronized
    fun clearSession(context: Context? = null) {
        inMemorySession = null
        if (context != null) {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit()
                    .remove(KEY_CIPHER_TOKEN)
                    .remove(KEY_CIPHER_IV)
                    .remove(KEY_OWNER_ID)
                    .remove(KEY_EXPIRES_AT)
                    .remove(KEY_DISPLAY_NAME)
                    .apply()
            } catch (_: Exception) {}
        }
    }
}
