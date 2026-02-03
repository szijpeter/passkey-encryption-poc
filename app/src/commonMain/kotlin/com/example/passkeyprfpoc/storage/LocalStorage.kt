package com.example.passkeyprfpoc.storage

import com.russhwolf.settings.Settings
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Local storage for passkey data and encrypted content. Uses multiplatform Settings for
 * persistence (backed by SharedPreferences on Android and NSUserDefaults on iOS).
 */
class LocalStorage(private val settings: Settings = Settings()) {

    companion object {
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
        get() = settings.getBoolean(KEY_HAS_PASSKEY, false)
        set(value) = settings.putBoolean(KEY_HAS_PASSKEY, value)

    var credentialId: String?
        get() = settings.getStringOrNull(KEY_CREDENTIAL_ID)
        set(value) {
            if (value == null) settings.remove(KEY_CREDENTIAL_ID)
            else settings.putString(KEY_CREDENTIAL_ID, value)
        }

    var userId: String?
        get() = settings.getStringOrNull(KEY_USER_ID)
        set(value) {
            if (value == null) settings.remove(KEY_USER_ID)
            else settings.putString(KEY_USER_ID, value)
        }

    // PRF Salt

    fun savePrfSalt(salt: ByteArray) {
        settings.putString(KEY_PRF_SALT, salt.encodeBase64())
    }

    fun getPrfSalt(): ByteArray? {
        val b64 = settings.getStringOrNull(KEY_PRF_SALT) ?: return null
        return b64.decodeBase64()
    }

    fun getOrCreatePrfSalt(): ByteArray {
        val existing = getPrfSalt()
        if (existing != null) return existing

        val salt = ByteArray(32)
        kotlin.random.Random.nextBytes(salt)
        savePrfSalt(salt)
        return salt
    }

    // Encrypted data (legacy - for direct crypto usage)

    fun saveEncryptedData(ciphertext: ByteArray, iv: ByteArray) {
        settings.putString(KEY_ENCRYPTED_CIPHERTEXT, ciphertext.encodeBase64())
        settings.putString(KEY_ENCRYPTED_IV, iv.encodeBase64())
    }

    fun getEncryptedData(): Pair<ByteArray, ByteArray>? {
        val ciphertextB64 = settings.getStringOrNull(KEY_ENCRYPTED_CIPHERTEXT) ?: return null
        val ivB64 = settings.getStringOrNull(KEY_ENCRYPTED_IV) ?: return null
        return ciphertextB64.decodeBase64() to ivB64.decodeBase64()
    }

    fun hasEncryptedData(): Boolean {
        return (settings.hasKey(KEY_ENCRYPTED_CIPHERTEXT) && settings.hasKey(KEY_ENCRYPTED_IV)) ||
                settings.hasKey(KEY_ENCRYPTED_BLOB)
    }

    // Encrypted blob (for SDK usage)

    fun saveEncryptedBlob(blobBytes: ByteArray) {
        settings.putString(KEY_ENCRYPTED_BLOB, blobBytes.encodeBase64())
    }

    fun getEncryptedBlob(): ByteArray? {
        val b64 = settings.getStringOrNull(KEY_ENCRYPTED_BLOB) ?: return null
        return b64.decodeBase64()
    }

    // Server URL

    var serverUrl: String
        get() = settings.getString(KEY_SERVER_URL, "")
        set(value) = settings.putString(KEY_SERVER_URL, value)

    // Clear all data

    fun clear() {
        settings.clear()
    }
}

@OptIn(ExperimentalEncodingApi::class)
private fun ByteArray.encodeBase64(): String = Base64.Default.encode(this)

@OptIn(ExperimentalEncodingApi::class)
private fun String.decodeBase64(): ByteArray = Base64.Default.decode(this)
