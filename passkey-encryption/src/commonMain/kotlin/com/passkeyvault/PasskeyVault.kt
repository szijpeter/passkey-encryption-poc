package com.passkeyvault

import com.passkeyvault.auth.PrfAuthResult
import com.passkeyvault.auth.PrfAuthenticator
import com.passkeyvault.auth.createPlatformPrfAuthenticator
import com.passkeyvault.encryption.EncryptionManager
import com.passkeyvault.encryption.KeyDerivation
import com.passkeyvault.model.CredentialDescriptor
import com.passkeyvault.model.EncryptedBlob
import com.passkeyvault.model.EncryptionSession
import com.passkeyvault.platform.PlatformContext
import com.passkeyvault.storage.SaltStore

/**
 * PasskeyVault - Encrypt anything with your passkey.
 *
 * This SDK allows apps with existing WebAuthn/passkey implementations to add client-side encryption
 * using the PRF extension.
 *
 * ## Usage
 *
 * ```kotlin
 * val vault = PasskeyVault()
 *
 * // Authenticate and create an encryption session
 * val session = vault.authenticateForEncryption(
 *     activity = activity,
 *     challenge = serverChallenge,  // From your WebAuthn server
 *     rpId = "your-domain.com",
 *     allowCredentials = listOf(CredentialDescriptor(id = credentialId)),
 *     saltId = "user-vault"  // Optional context identifier
 * ).getOrThrow()
 *
 * // Encrypt data
 * val encrypted = vault.encrypt(session, "my secret data")
 *
 * // Store encrypted.toBytes() anywhere
 *
 * // Later, decrypt
 * val decrypted = vault.decrypt(session, encrypted)
 *
 * // Clean up when done
 * session.clear()
 * ```
 */
class PasskeyVault(
    private val saltStore: SaltStore = SaltStore(),
    private val prfAuthenticator: PrfAuthenticator = createPlatformPrfAuthenticator(),
) {
    companion object {
        /** Default salt ID if none specified */
        const val DEFAULT_SALT_ID = "default"
    }

    /**
     * Authenticate with passkey and create an encryption session.
     *
     * This method:
     * 1. Gets or creates a salt for the specified saltId
     * 2. Triggers passkey authentication with PRF extension
     * 3. Derives an AES-256 key from the PRF output
     * 4. Returns an EncryptionSession ready for encrypt/decrypt
     *
     * @param platformContext The platform UI context (needed for biometric prompt)
     * @param challenge Base64URL-encoded challenge from your WebAuthn server
     * @param rpId Your Relying Party ID (domain)
     * @param allowCredentials List of credential IDs the user can authenticate with
     * @param saltId Identifier for this encryption context (default: "default")
     * @return Result containing EncryptionSession or error
     */
    suspend fun authenticateForEncryption(
        platformContext: PlatformContext,
        challenge: String,
        rpId: String,
        allowCredentials: List<CredentialDescriptor>,
        saltId: String = DEFAULT_SALT_ID,
    ): Result<EncryptionSession> =
        runCatching {
            // Get or create salt for this context
            val salt = saltStore.getOrCreateSalt(saltId)

            // Authenticate with passkey and get PRF output
            val authResult: PrfAuthResult =
                prfAuthenticator.authenticate(
                    platformContext = platformContext,
                    challenge = challenge,
                    rpId = rpId,
                    allowCredentials = allowCredentials,
                    prfSalt = salt,
                )

            val prfOutput =
                authResult.prfOutput
                    ?: throw UnsupportedOperationException(
                        "PRF extension not supported by this authenticator",
                    )

            // Derive encryption key
            val keyBytes = KeyDerivation.deriveKey(prfOutput, saltId)
            val keyHash = KeyDerivation.getKeyHash(keyBytes)

            EncryptionSession(keyHash, saltId, keyBytes)
        }

    /**
     * Encrypt bytes using an active session.
     *
     * @param session The encryption session from authenticateForEncryption
     * @param plaintext The data to encrypt
     * @return EncryptedBlob containing ciphertext, IV, and saltId
     */
    suspend fun encrypt(
        session: EncryptionSession,
        plaintext: ByteArray,
    ): EncryptedBlob = EncryptionManager.encrypt(session, plaintext)

    /**
     * Encrypt a string using an active session.
     *
     * @param session The encryption session from authenticateForEncryption
     * @param plaintext The string to encrypt
     * @return EncryptedBlob containing ciphertext, IV, and saltId
     */
    suspend fun encrypt(
        session: EncryptionSession,
        plaintext: String,
    ): EncryptedBlob = EncryptionManager.encryptString(session, plaintext)

    /**
     * Decrypt an EncryptedBlob using an active session.
     *
     * @param session The encryption session (must use same saltId as encryption)
     * @param blob The encrypted data
     * @return Decrypted bytes
     */
    suspend fun decrypt(
        session: EncryptionSession,
        blob: EncryptedBlob,
    ): ByteArray = EncryptionManager.decrypt(session, blob)

    /**
     * Decrypt an EncryptedBlob to a string using an active session.
     *
     * @param session The encryption session (must use same saltId as encryption)
     * @param blob The encrypted data
     * @return Decrypted string
     */
    suspend fun decryptToString(
        session: EncryptionSession,
        blob: EncryptedBlob,
    ): String = EncryptionManager.decryptString(session, blob)

    /**
     * Check if a salt exists for the given ID. Useful to determine if encryption has been set up
     * for a context.
     */
    fun hasSalt(saltId: String = DEFAULT_SALT_ID): Boolean = saltStore.hasSalt(saltId)

    /**
     * Delete the salt for a given ID. Warning: This will make previously encrypted data
     * unrecoverable!
     */
    fun deleteSalt(saltId: String) {
        saltStore.deleteSalt(saltId)
    }
}
