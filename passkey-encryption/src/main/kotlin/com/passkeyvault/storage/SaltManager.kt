package com.passkeyvault.storage

import android.content.Context
import android.content.SharedPreferences
import java.security.SecureRandom
import java.util.Base64

/**
 * Manages PRF salts for different encryption contexts. Each saltId gets its own unique 32-byte
 * salt.
 */
internal class SaltManager(context: Context) {

    private val prefs: SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "passkey_vault_salts"
        private const val SALT_SIZE = 32
    }

    /**
     * Get or create a salt for the given ID. If no salt exists, generates a new random 32-byte
     * salt.
     */
    fun getOrCreateSalt(saltId: String): ByteArray {
        val key = "salt_$saltId"
        val existing = prefs.getString(key, null)

        if (existing != null) {
            return Base64.getDecoder().decode(existing)
        }

        // Generate new salt
        val salt = ByteArray(SALT_SIZE)
        SecureRandom().nextBytes(salt)

        // Store it
        val encoded = Base64.getEncoder().encodeToString(salt)
        prefs.edit().putString(key, encoded).apply()

        return salt
    }

    /** Check if a salt exists for the given ID. */
    fun hasSalt(saltId: String): Boolean {
        return prefs.contains("salt_$saltId")
    }

    /** Delete a salt. */
    fun deleteSalt(saltId: String) {
        prefs.edit().remove("salt_$saltId").apply()
    }

    /** Clear all stored salts. */
    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
