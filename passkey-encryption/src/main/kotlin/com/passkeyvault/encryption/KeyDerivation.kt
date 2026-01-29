package com.passkeyvault.encryption

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/** Key derivation from PRF output using HKDF. */
internal object KeyDerivation {

    /**
     * Derive an AES-256 key from the PRF output.
     *
     * Uses HKDF-Extract then HKDF-Expand pattern.
     *
     * @param prfOutput The 32-byte output from PRF extension
     * @param saltId Context identifier for this key (used in info)
     * @return A SecretKey suitable for AES-256
     */
    fun deriveKey(prfOutput: ByteArray, saltId: String): SecretKey {
        // HKDF-Extract: PRK = HMAC-SHA256(salt, IKM)
        // Using empty salt as per RFC 5869
        val prk = hmacSha256(key = ByteArray(32), data = prfOutput)

        // HKDF-Expand: OKM = HMAC-SHA256(PRK, info || 0x01)
        val info = "passkey-vault-encryption-$saltId"
        val okm = hmacSha256(key = prk, data = info.toByteArray(Charsets.UTF_8) + byteArrayOf(0x01))

        return SecretKeySpec(okm, "AES")
    }

    /** Compute HMAC-SHA256. */
    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    /**
     * Get a hash of the key for logging (don't log actual key!). Returns first 16 hex chars of
     * SHA-256 hash.
     */
    fun getKeyHash(key: SecretKey): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(key.encoded)
        return hash.take(8).joinToString("") { "%02x".format(it) }
    }
}
