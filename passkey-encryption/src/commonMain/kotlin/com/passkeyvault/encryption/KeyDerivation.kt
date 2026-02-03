package com.passkeyvault.encryption

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.HMAC
import dev.whyoleg.cryptography.algorithms.SHA256

/** Key derivation from PRF output using HKDF. */
internal object KeyDerivation {

    private val provider = CryptographyProvider.Default
    private val hmac = provider.get(HMAC)
    private val sha256 = provider.get(SHA256)

    /**
     * Derive an AES-256 key from the PRF output.
     *
     * Uses HKDF-Extract then HKDF-Expand pattern.
     *
     * @param prfOutput The 32-byte output from PRF extension
     * @param saltId Context identifier for this key (used in info)
     * @return Raw AES-256 key bytes
     */
    suspend fun deriveKey(prfOutput: ByteArray, saltId: String): ByteArray {
        // HKDF-Extract: PRK = HMAC-SHA256(salt, IKM)
        // Using empty salt as per RFC 5869
        val prk = hmacSha256(key = ByteArray(32), data = prfOutput)

        // HKDF-Expand: OKM = HMAC-SHA256(PRK, info || 0x01)
        val info = "passkey-vault-encryption-$saltId"
        return hmacSha256(
                key = prk,
                data = info.encodeToByteArray() + byteArrayOf(0x01)
        )
    }

    /** Compute HMAC-SHA256. */
    private suspend fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val hmacKey =
                hmac.keyDecoder(SHA256).decodeFromByteArray(HMAC.Key.Format.RAW, key)
        return hmacKey.signatureGenerator().generateSignature(data)
    }

    /**
     * Get a hash of the key for logging (don't log actual key!). Returns first 16 hex chars of
     * SHA-256 hash.
     */
    suspend fun getKeyHash(keyBytes: ByteArray): String {
        val hash = sha256.hasher().hash(keyBytes)
        val hexChars = "0123456789abcdef"
        val limit = minOf(8, hash.size)
        val builder = StringBuilder(limit * 2)
        for (index in 0 until limit) {
            val value = hash[index].toInt() and 0xFF
            builder.append(hexChars[value ushr 4])
            builder.append(hexChars[value and 0x0F])
        }
        return builder.toString()
    }
}
