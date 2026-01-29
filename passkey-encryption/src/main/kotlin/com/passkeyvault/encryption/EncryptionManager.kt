package com.passkeyvault.encryption

import android.util.Log
import com.passkeyvault.model.EncryptedBlob
import com.passkeyvault.model.EncryptionSession
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

/** Handles AES-GCM encryption and decryption. */
internal object EncryptionManager {

    private const val TAG = "PasskeyVault"
    private const val AES_GCM_ALGORITHM = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128

    /** Encrypt plaintext bytes using AES-GCM. */
    fun encrypt(session: EncryptionSession, plaintext: ByteArray): EncryptedBlob {
        session.requireValid()

        val cipher = Cipher.getInstance(AES_GCM_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, session.key)

        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)

        Log.d(TAG, "Encrypted ${plaintext.size} bytes to ${ciphertext.size} bytes")

        return EncryptedBlob(ciphertext, iv, session.saltId)
    }

    /** Decrypt ciphertext using AES-GCM. */
    fun decrypt(session: EncryptionSession, blob: EncryptedBlob): ByteArray {
        session.requireValid()

        val cipher = Cipher.getInstance(AES_GCM_ALGORITHM)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, blob.iv)
        cipher.init(Cipher.DECRYPT_MODE, session.key, spec)

        val plaintext = cipher.doFinal(blob.ciphertext)

        Log.d(TAG, "Decrypted ${blob.ciphertext.size} bytes to ${plaintext.size} bytes")

        return plaintext
    }

    /** Encrypt a string to EncryptedBlob. */
    fun encryptString(session: EncryptionSession, plaintext: String): EncryptedBlob {
        return encrypt(session, plaintext.toByteArray(Charsets.UTF_8))
    }

    /** Decrypt EncryptedBlob to a string. */
    fun decryptString(session: EncryptionSession, blob: EncryptedBlob): String {
        return String(decrypt(session, blob), Charsets.UTF_8)
    }
}
