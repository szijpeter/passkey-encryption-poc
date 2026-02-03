package com.passkeyvault.auth

import com.passkeyvault.model.CredentialDescriptor
import com.passkeyvault.platform.PlatformContext

/** Handles passkey authentication with PRF extension. */
interface PrfAuthenticator {
    suspend fun authenticate(
            platformContext: PlatformContext,
            challenge: String,
            rpId: String,
            allowCredentials: List<CredentialDescriptor>,
            prfSalt: ByteArray
    ): PrfAuthResult
}

/** Result of PRF authentication. */
data class PrfAuthResult(val responseJson: String, val prfOutput: ByteArray?) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PrfAuthResult) return false
        val prfOutputMatches =
                when {
                    prfOutput == null && other.prfOutput == null -> true
                    prfOutput != null && other.prfOutput != null ->
                            prfOutput.contentEquals(other.prfOutput)
                    else -> false
                }
        return responseJson == other.responseJson && prfOutputMatches
    }

    override fun hashCode(): Int {
        var result = responseJson.hashCode()
        result = 31 * result + (prfOutput?.contentHashCode() ?: 0)
        return result
    }
}

expect fun createPlatformPrfAuthenticator(): PrfAuthenticator
