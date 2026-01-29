package com.passkeyvault.auth

import android.app.Activity
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import com.passkeyvault.model.CredentialDescriptor
import java.util.Base64
import kotlinx.serialization.json.*

/** Handles passkey authentication with PRF extension. */
internal class PrfAuthenticator {

    companion object {
        private const val TAG = "PasskeyVault"
    }

    /**
     * Authenticate with passkey and get PRF output.
     *
     * @param activity The activity context
     * @param challenge Base64URL-encoded challenge from WebAuthn server
     * @param rpId Relying Party ID
     * @param allowCredentials List of allowed credentials
     * @param prfSalt The salt for PRF evaluation
     * @return PRF output bytes (32 bytes) or null if PRF not supported
     */
    suspend fun authenticate(
            activity: Activity,
            challenge: String,
            rpId: String,
            allowCredentials: List<CredentialDescriptor>,
            prfSalt: ByteArray
    ): PrfAuthResult {
        val credentialManager = CredentialManager.create(activity)

        // Convert salt to base64url
        val saltB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(prfSalt)

        // Build the WebAuthn get options JSON with PRF
        val requestJson = buildAuthenticationJson(challenge, rpId, allowCredentials, saltB64)
        Log.d(TAG, "Authentication request built for PRF")

        val publicKeyOption = GetPublicKeyCredentialOption(requestJson = requestJson)
        val request = GetCredentialRequest(credentialOptions = listOf(publicKeyOption))

        val result = credentialManager.getCredential(activity, request)
        val credential =
                result.credential as? PublicKeyCredential
                        ?: throw IllegalStateException("Expected PublicKeyCredential")

        val responseJson = credential.authenticationResponseJson
        Log.d(TAG, "Authentication completed")

        // Extract PRF output from client extension results
        val prfOutput = extractPrfOutput(responseJson)

        if (prfOutput != null) {
            Log.d(TAG, "PRF output received: ${prfOutput.size} bytes")
        } else {
            Log.w(TAG, "No PRF output - extension may not be supported")
        }

        return PrfAuthResult(responseJson, prfOutput)
    }

    private fun buildAuthenticationJson(
            challenge: String,
            rpId: String,
            allowCredentials: List<CredentialDescriptor>,
            prfSaltB64: String
    ): String {
        return buildJsonObject {
                    put("challenge", challenge)
                    put("rpId", rpId)
                    put("timeout", 60000)
                    put("userVerification", "required")
                    putJsonArray("allowCredentials") {
                        allowCredentials.forEach { cred ->
                            addJsonObject {
                                put("type", "public-key")
                                put("id", cred.id)
                                cred.transports?.let { transports ->
                                    putJsonArray("transports") { transports.forEach { add(it) } }
                                }
                            }
                        }
                    }
                    // Request PRF evaluation with the provided salt
                    putJsonObject("extensions") {
                        putJsonObject("prf") { putJsonObject("eval") { put("first", prfSaltB64) } }
                    }
                }
                .toString()
    }

    private fun extractPrfOutput(responseJson: String): ByteArray? {
        return try {
            val json = Json.parseToJsonElement(responseJson).jsonObject
            val clientExtensionResults = json["clientExtensionResults"]?.jsonObject
            val prf = clientExtensionResults?.get("prf")?.jsonObject
            val results = prf?.get("results")?.jsonObject
            val firstB64 = results?.get("first")?.jsonPrimitive?.content

            firstB64?.let { Base64.getUrlDecoder().decode(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract PRF output", e)
            null
        }
    }
}

/** Result of PRF authentication. */
internal data class PrfAuthResult(val responseJson: String, val prfOutput: ByteArray?) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as PrfAuthResult
        return responseJson == other.responseJson && prfOutput.contentEquals(other.prfOutput)
    }

    override fun hashCode(): Int {
        var result = responseJson.hashCode()
        result = 31 * result + (prfOutput?.contentHashCode() ?: 0)
        return result
    }
}
