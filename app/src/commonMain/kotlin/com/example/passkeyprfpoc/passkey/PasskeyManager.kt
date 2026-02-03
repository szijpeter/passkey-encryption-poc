package com.example.passkeyprfpoc.passkey

import com.example.passkeyprfpoc.api.AuthenticationOptionsResponse
import com.example.passkeyprfpoc.api.RegistrationOptionsResponse
import com.passkeyvault.platform.PlatformContext

expect class PasskeyManager() {
    suspend fun createPasskey(
            platformContext: PlatformContext,
            options: RegistrationOptionsResponse
    ): Result<String>

    suspend fun authenticateWithPrf(
            platformContext: PlatformContext,
            options: AuthenticationOptionsResponse,
            prfSalt: ByteArray
    ): Result<PasskeyAuthResult>
}

/** Result of passkey authentication with optional PRF output. */
data class PasskeyAuthResult(val responseJson: String, val prfOutput: ByteArray?) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PasskeyAuthResult) return false
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
