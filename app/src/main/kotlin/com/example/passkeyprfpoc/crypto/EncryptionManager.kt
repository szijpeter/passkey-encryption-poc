package com.example.passkeyprfpoc.crypto

import android.util.Log
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Handles AES-GCM encryption and decryption using keys derived from PRF output. */
object EncryptionManager {

    private const val TAG = "EncryptionManager"
    private const val AES_GCM_ALGORITHM = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12

    /**
     * Encrypt plaintext using AES-GCM.
     *
     * @param key The AES key derived from PRF output
     * @param plaintext The string to encrypt
     * @return EncryptedData containing ciphertext and IV
     */
    fun encrypt(key: SecretKey, plaintext: String): EncryptedData {
        val cipher = Cipher.getInstance(AES_GCM_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key)

        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        Log.d(TAG, "Encrypted ${plaintext.length} chars to ${ciphertext.size} bytes")

        return EncryptedData(ciphertext, iv)
    }

    /**
     * Decrypt ciphertext using AES-GCM.
     *
     * @param key The AES key derived from PRF output
     * @param data The encrypted data with IV
     * @return The decrypted plaintext string
     */
    fun decrypt(key: SecretKey, data: EncryptedData): String {
        val cipher = Cipher.getInstance(AES_GCM_ALGORITHM)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, data.iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        val plaintext = cipher.doFinal(data.ciphertext)

        Log.d(TAG, "Decrypted ${data.ciphertext.size} bytes to ${plaintext.size} chars")

        return String(plaintext, Charsets.UTF_8)
    }
}

/** Container for encrypted data (ciphertext + IV). */
data class EncryptedData(val ciphertext: ByteArray, val iv: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EncryptedData
        return ciphertext.contentEquals(other.ciphertext) && iv.contentEquals(other.iv)
    }

    override fun hashCode(): Int {
        var result = ciphertext.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        return result
    }
}

/** Key derivation from PRF output using HKDF-like construction. */
object KeyDerivation {

    private const val TAG = "KeyDerivation"

    /**
     * Derive an AES-256 key from the PRF output.
     *
     * Uses HKDF-Extract then HKDF-Expand pattern.
     *
     * @param prfOutput The 32-byte output from PRF extension
     * @param salt Optional salt (can be app-specific)
     * @param info Context info for key derivation
     * @return A SecretKey suitable for AES-256
     */
    fun deriveKey(
            prfOutput: ByteArray,
            salt: ByteArray = byteArrayOf(),
            info: String = "passkey-prf-encryption-key"
    ): SecretKey {
        // HKDF-Extract: PRK = HMAC-SHA256(salt, IKM)
        val prk = hmacSha256(key = if (salt.isEmpty()) ByteArray(32) else salt, data = prfOutput)

        // HKDF-Expand: OKM = HMAC-SHA256(PRK, info || 0x01)
        val okm = hmacSha256(key = prk, data = info.toByteArray(Charsets.UTF_8) + byteArrayOf(0x01))

        Log.d(TAG, "Derived key hash: ${okm.contentHashCode()}")

        return SecretKeySpec(okm, "AES")
    }

    /** Compute HMAC-SHA256. */
    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    /** Get a hash of the key for logging (don't log actual key!). */
    fun getKeyHash(key: SecretKey): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(key.encoded)
        return hash.take(8).joinToString("") { "%02x".format(it) }
    }
}
