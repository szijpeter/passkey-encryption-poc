package com.passkeyvault.storage

import com.russhwolf.settings.Settings
import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Manages PRF salts for different encryption contexts. */
class SaltStore(private val settings: Settings = Settings()) {

    companion object {
        private const val SALT_SIZE = 32
    }

    /** Get or create a salt for the given ID. If no salt exists, generates a new random 32-byte salt. */
    fun getOrCreateSalt(saltId: String): ByteArray {
        val key = keyForSalt(saltId)
        val existing = settings.getStringOrNull(key)
        if (existing != null) return existing.decodeBase64()

        val salt = CryptographyRandom.nextBytes(SALT_SIZE)
        settings.putString(key, salt.encodeBase64())
        return salt
    }

    /** Check if a salt exists for the given ID. */
    fun hasSalt(saltId: String): Boolean = settings.hasKey(keyForSalt(saltId))

    /** Delete a salt. */
    fun deleteSalt(saltId: String) {
        settings.remove(keyForSalt(saltId))
    }

    /** Clear all stored salts. */
    fun clearAll() {
        settings.clear()
    }

    private fun keyForSalt(saltId: String) = "salt_$saltId"
}

@OptIn(ExperimentalEncodingApi::class)
private fun ByteArray.encodeBase64(): String = Base64.Default.encode(this)

@OptIn(ExperimentalEncodingApi::class)
private fun String.decodeBase64(): ByteArray = Base64.Default.decode(this)
