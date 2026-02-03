package com.passkeyvault.encryption

import com.passkeyvault.model.EncryptedBlob
import com.passkeyvault.model.EncryptionSession
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.random.CryptographyRandom

/** Handles AES-GCM encryption and decryption. */
@OptIn(DelicateCryptographyApi::class)
internal object EncryptionManager {
    private const val GCM_IV_LENGTH_BYTES = 12

    private val provider = CryptographyProvider.Default
    private val aesGcm = provider.get(AES.GCM)

    /** Encrypt plaintext bytes using AES-GCM. */
    suspend fun encrypt(
        session: EncryptionSession,
        plaintext: ByteArray,
    ): EncryptedBlob {
        session.requireValid()

        val iv = CryptographyRandom.nextBytes(GCM_IV_LENGTH_BYTES)
        val key = aesGcm.keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, session.keyBytes)
        val cipher = key.cipher()
        val ciphertext = cipher.encryptWithIv(iv = iv, plaintext = plaintext)

        return EncryptedBlob(ciphertext, iv, session.saltId)
    }

    /** Decrypt ciphertext using AES-GCM. */
    suspend fun decrypt(
        session: EncryptionSession,
        blob: EncryptedBlob,
    ): ByteArray {
        session.requireValid()

        val key = aesGcm.keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, session.keyBytes)
        val cipher = key.cipher()
        return cipher.decryptWithIv(iv = blob.iv, ciphertext = blob.ciphertext)
    }

    /** Encrypt a string to EncryptedBlob. */
    suspend fun encryptString(
        session: EncryptionSession,
        plaintext: String,
    ): EncryptedBlob = encrypt(session, plaintext.encodeToByteArray())

    /** Decrypt EncryptedBlob to a string. */
    suspend fun decryptString(
        session: EncryptionSession,
        blob: EncryptedBlob,
    ): String = decrypt(session, blob).decodeToString()
}
