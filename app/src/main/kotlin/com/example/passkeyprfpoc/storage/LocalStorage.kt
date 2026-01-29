package com.example.passkeyprfpoc.storage

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.example.passkeyprfpoc.crypto.EncryptedData

/**
 * Local storage for passkey data and encrypted content. Uses SharedPreferences for simplicity (in
 * production, use EncryptedSharedPreferences).
 */
class LocalStorage(context: Context) {

    private val prefs: SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "passkey_prf_poc"

        private const val KEY_HAS_PASSKEY = "has_passkey"
        private const val KEY_CREDENTIAL_ID = "credential_id"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_PRF_SALT = "prf_salt"
        private const val KEY_ENCRYPTED_CIPHERTEXT = "encrypted_ciphertext"
        private const val KEY_ENCRYPTED_IV = "encrypted_iv"
        private const val KEY_ENCRYPTED_BLOB = "encrypted_blob"
        private const val KEY_SERVER_URL = "server_url"
    }

    // Passkey status

    var hasPasskey: Boolean
        get() = prefs.getBoolean(KEY_HAS_PASSKEY, false)
        set(value) = prefs.edit().putBoolean(KEY_HAS_PASSKEY, value).apply()

    var credentialId: String?
        get() = prefs.getString(KEY_CREDENTIAL_ID, null)
        set(value) = prefs.edit().putString(KEY_CREDENTIAL_ID, value).apply()

    var userId: String?
        get() = prefs.getString(KEY_USER_ID, null)
        set(value) = prefs.edit().putString(KEY_USER_ID, value).apply()

    // PRF Salt

    fun savePrfSalt(salt: ByteArray) {
        val b64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        prefs.edit().putString(KEY_PRF_SALT, b64).apply()
    }

    fun getPrfSalt(): ByteArray? {
        val b64 = prefs.getString(KEY_PRF_SALT, null) ?: return null
        return Base64.decode(b64, Base64.NO_WRAP)
    }

    fun getOrCreatePrfSalt(): ByteArray {
        val existing = getPrfSalt()
        if (existing != null) return existing

        // Generate a new random salt
        val salt = ByteArray(32)
        java.security.SecureRandom().nextBytes(salt)
        savePrfSalt(salt)
        return salt
    }

    // Encrypted data (legacy - for direct crypto usage)

    fun saveEncryptedData(data: EncryptedData) {
        prefs.edit()
                .putString(
                        KEY_ENCRYPTED_CIPHERTEXT,
                        Base64.encodeToString(data.ciphertext, Base64.NO_WRAP)
                )
                .putString(KEY_ENCRYPTED_IV, Base64.encodeToString(data.iv, Base64.NO_WRAP))
                .apply()
    }

    fun getEncryptedData(): EncryptedData? {
        val ciphertextB64 = prefs.getString(KEY_ENCRYPTED_CIPHERTEXT, null) ?: return null
        val ivB64 = prefs.getString(KEY_ENCRYPTED_IV, null) ?: return null

        return EncryptedData(
                ciphertext = Base64.decode(ciphertextB64, Base64.NO_WRAP),
                iv = Base64.decode(ivB64, Base64.NO_WRAP)
        )
    }

    fun hasEncryptedData(): Boolean {
        return prefs.contains(KEY_ENCRYPTED_CIPHERTEXT) && prefs.contains(KEY_ENCRYPTED_IV) ||
               prefs.contains(KEY_ENCRYPTED_BLOB)
    }
    
    // Encrypted blob (for SDK usage)
    
    fun saveEncryptedBlob(blobBytes: ByteArray) {
        val b64 = Base64.encodeToString(blobBytes, Base64.NO_WRAP)
        prefs.edit().putString(KEY_ENCRYPTED_BLOB, b64).apply()
    }
    
    fun getEncryptedBlob(): ByteArray? {
        val b64 = prefs.getString(KEY_ENCRYPTED_BLOB, null) ?: return null
        return Base64.decode(b64, Base64.NO_WRAP)
    }

    // Server URL

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()

    // Clear all data

    fun clear() {
        prefs.edit().clear().apply()
    }
}
