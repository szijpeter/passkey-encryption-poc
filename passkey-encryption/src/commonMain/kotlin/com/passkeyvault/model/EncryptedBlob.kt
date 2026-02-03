package com.passkeyvault.model

/**
 * Self-contained encrypted data blob. Includes ciphertext, IV, and salt ID for key reconstruction.
 */
data class EncryptedBlob(val ciphertext: ByteArray, val iv: ByteArray, val saltId: String) {
    /**
     * Serialize to bytes for storage. Format: [saltId length (4 bytes)][saltId][iv length (4
     * bytes)][iv][ciphertext]
     */
    fun toBytes(): ByteArray {
        val saltIdBytes = saltId.encodeToByteArray()
        val result = ByteArray(4 + saltIdBytes.size + 4 + iv.size + ciphertext.size)
        var offset = 0

        // Salt ID length + data
        result[offset++] = (saltIdBytes.size shr 24).toByte()
        result[offset++] = (saltIdBytes.size shr 16).toByte()
        result[offset++] = (saltIdBytes.size shr 8).toByte()
        result[offset++] = saltIdBytes.size.toByte()
        saltIdBytes.copyInto(result, destinationOffset = offset)
        offset += saltIdBytes.size

        // IV length + data
        result[offset++] = (iv.size shr 24).toByte()
        result[offset++] = (iv.size shr 16).toByte()
        result[offset++] = (iv.size shr 8).toByte()
        result[offset++] = iv.size.toByte()
        iv.copyInto(result, destinationOffset = offset)
        offset += iv.size

        // Ciphertext
        ciphertext.copyInto(result, destinationOffset = offset)

        return result
    }

    companion object {
        /** Deserialize from bytes. */
        fun fromBytes(bytes: ByteArray): EncryptedBlob {
            var offset = 0

            // Read salt ID
            val saltIdLen =
                    ((bytes[offset++].toInt() and 0xFF) shl 24) or
                            ((bytes[offset++].toInt() and 0xFF) shl 16) or
                            ((bytes[offset++].toInt() and 0xFF) shl 8) or
                            (bytes[offset++].toInt() and 0xFF)
            val saltId = bytes.decodeToString(offset, offset + saltIdLen)
            offset += saltIdLen

            // Read IV
            val ivLen =
                    ((bytes[offset++].toInt() and 0xFF) shl 24) or
                            ((bytes[offset++].toInt() and 0xFF) shl 16) or
                            ((bytes[offset++].toInt() and 0xFF) shl 8) or
                            (bytes[offset++].toInt() and 0xFF)
            val iv = ByteArray(ivLen)
            bytes.copyInto(iv, destinationOffset = 0, startIndex = offset, endIndex = offset + ivLen)
            offset += ivLen

            // Read ciphertext
            val ciphertext = ByteArray(bytes.size - offset)
            bytes.copyInto(
                    ciphertext,
                    destinationOffset = 0,
                    startIndex = offset,
                    endIndex = bytes.size
            )

            return EncryptedBlob(ciphertext, iv, saltId)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedBlob) return false
        return ciphertext.contentEquals(other.ciphertext) &&
                iv.contentEquals(other.iv) &&
                saltId == other.saltId
    }

    override fun hashCode(): Int {
        var result = ciphertext.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + saltId.hashCode()
        return result
    }
}
