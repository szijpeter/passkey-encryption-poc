package com.example.passkeyprfpoc.passkey

import android.util.Log
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import com.example.passkeyprfpoc.api.AuthenticationOptionsResponse
import com.example.passkeyprfpoc.api.RegistrationOptionsResponse
import com.passkeyvault.platform.PlatformContext
import java.util.Base64
import kotlinx.serialization.json.*

/**
 * Manages passkey operations using Android Credential Manager. Handles creation and authentication
 * with PRF extension support.
 */
actual class PasskeyManager {

    companion object {
        private const val TAG = "PasskeyManager"
    }

    /**
     * Create a new passkey with PRF extension support.
     *
     * @return The credential response JSON string
     */
    actual suspend fun createPasskey(
            platformContext: PlatformContext,
            options: RegistrationOptionsResponse
    ): Result<String> {
        return try {
            val credentialManager = CredentialManager.create(platformContext)

            // Build the WebAuthn creation options JSON
            val requestJson = buildRegistrationJson(options)
            Log.d(TAG, "Registration request JSON: $requestJson")

            val request =
                    CreatePublicKeyCredentialRequest(
                            requestJson = requestJson,
                            preferImmediatelyAvailableCredentials = false
                    )

            val result = credentialManager.createCredential(platformContext, request)
            val credential =
                    result.data.getString(
                            "androidx.credentials.BUNDLE_KEY_REGISTRATION_RESPONSE_JSON"
                    )
                            ?: throw IllegalStateException("No credential response found")

            Log.d(TAG, "Credential created successfully")
            Log.d(TAG, "Response: $credential")

            Result.success(credential)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create passkey", e)
            Result.failure(e)
        }
    }

    /**
     * Authenticate with a passkey and get PRF output.
     *
     * @param prfSalt Base64-encoded salt for PRF evaluation
     * @return Pair of (credential response JSON, PRF output bytes or null)
     */
    actual suspend fun authenticateWithPrf(
            platformContext: PlatformContext,
            options: AuthenticationOptionsResponse,
            prfSalt: ByteArray
    ): Result<PasskeyAuthResult> {
        return try {
            val credentialManager = CredentialManager.create(platformContext)

            // Convert salt to base64url
            val saltB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(prfSalt)

            // Build the WebAuthn get options JSON with PRF
            val requestJson = buildAuthenticationJson(options, saltB64)
            Log.d(TAG, "Authentication request JSON: $requestJson")

            val publicKeyOption = GetPublicKeyCredentialOption(requestJson = requestJson)

            val request = GetCredentialRequest(credentialOptions = listOf(publicKeyOption))

            val result = credentialManager.getCredential(platformContext, request)
            val credential =
                    result.credential as? PublicKeyCredential
                            ?: throw IllegalStateException("Expected PublicKeyCredential")

            val responseJson = credential.authenticationResponseJson
            Log.d(TAG, "Authentication response: $responseJson")

            // Extract PRF output from client extension results
            val prfOutput = extractPrfOutput(responseJson)

            if (prfOutput != null) {
                Log.d(TAG, "PRF output received: ${prfOutput.size} bytes")
                Log.d(TAG, "PRF output hash: ${prfOutput.contentHashCode()}")
            } else {
                Log.w(TAG, "No PRF output in response - extension may not be supported")
            }

            Result.success(PasskeyAuthResult(responseJson, prfOutput))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to authenticate", e)
            Result.failure(e)
        }
    }

    /** Build registration JSON from server options. */
    private fun buildRegistrationJson(options: RegistrationOptionsResponse): String {
        return buildJsonObject {
                    put("challenge", options.challenge)
                    putJsonObject("rp") {
                        put("id", options.rp.id)
                        put("name", options.rp.name)
                    }
                    putJsonObject("user") {
                        put("id", options.user.id)
                        put("name", options.user.name)
                        put("displayName", options.user.displayName)
                    }
                    putJsonArray("pubKeyCredParams") {
                        options.pubKeyCredParams.forEach { param ->
                            addJsonObject {
                                put("type", param.type)
                                put("alg", param.alg)
                            }
                        }
                    }
                    put("timeout", options.timeout)
                    put("attestation", options.attestation)
                    putJsonObject("authenticatorSelection") {
                        options.authenticatorSelection.authenticatorAttachment?.let {
                            put("authenticatorAttachment", it)
                        }
                        put("residentKey", options.authenticatorSelection.residentKey)
                        put("userVerification", options.authenticatorSelection.userVerification)
                    }
                    // Request PRF extension support
                    putJsonObject("extensions") { putJsonObject("prf") {} }
                }
                .toString()
    }

    /** Build authentication JSON from server options with PRF evaluation. */
    private fun buildAuthenticationJson(
            options: AuthenticationOptionsResponse,
            prfSaltB64: String
    ): String {
        return buildJsonObject {
                    put("challenge", options.challenge)
                    put("rpId", options.rpId)
                    put("timeout", options.timeout)
                    put("userVerification", options.userVerification)
                    putJsonArray("allowCredentials") {
                        options.allowCredentials.forEach { cred ->
                            addJsonObject {
                                put("type", cred.type)
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

    /** Extract PRF output from authentication response. */
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
