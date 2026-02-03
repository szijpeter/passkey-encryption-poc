package com.passkeyvault.model

/**
 * Represents an active encryption session. Created after successful passkey authentication with
 * PRF.
 *
 * The session holds the derived encryption key in memory. Call [clear] when done to wipe the key
 * from memory.
 */
class EncryptionSession
internal constructor(
        /** Hash of the key for logging/verification (not the actual key!) */
        val keyHash: String,
        /** The salt ID used to derive this key */
        val saltId: String,
        internal val keyBytes: ByteArray
) {
    private var isCleared = false

    /** Check if this session is still valid (not cleared). */
    val isValid: Boolean
        get() = !isCleared

    /**
     * Wipe the key from memory. After calling this, the session cannot be used for
     * encryption/decryption.
     */
    fun clear() {
        if (!isCleared) {
            keyBytes.fill(0)
            isCleared = true
        }
    }

    internal fun requireValid() {
        if (isCleared) {
            throw IllegalStateException("EncryptionSession has been cleared and cannot be used")
        }
    }
}
