package com.example.passkeyprfpoc.api

import com.example.passkeyprfpoc.platform.createHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

/** API client for communicating with the Passkey server. */
class ApiClient(
    private val baseUrl: String,
) {
    private val client: HttpClient = createHttpClient()

    /** Get registration options from server. */
    suspend fun getRegistrationOptions(
        userId: String,
        userName: String,
    ): RegistrationOptionsResponse =
        client
            .post("$baseUrl/register/options") {
                contentType(ContentType.Application.Json)
                setBody(RegistrationOptionsRequest(userId, userName))
            }.body()

    /** Send registration response to server for verification. */
    suspend fun verifyRegistration(
        userId: String,
        credentialJson: String,
    ): VerifyResponse =
        client
            .post("$baseUrl/register/verify") {
                parameter("userId", userId)
                contentType(ContentType.Application.Json)
                setBody(credentialJson)
            }.body()

    /** Get authentication options (PRF is handled client-side). */
    suspend fun getAuthenticationOptions(): AuthenticationOptionsResponse =
        client
            .post("$baseUrl/authenticate/options") {
                contentType(ContentType.Application.Json)
                setBody(AuthenticationOptionsRequest())
            }.body()

    /** Send authentication response to server for verification. */
    suspend fun verifyAuthentication(
        challengeB64: String,
        assertionJson: String,
    ): VerifyResponse =
        client
            .post("$baseUrl/authenticate/verify") {
                parameter("challenge", challengeB64)
                contentType(ContentType.Application.Json)
                setBody(assertionJson)
            }.body()

    fun close() {
        client.close()
    }
}

// Request/Response models

@Serializable data class RegistrationOptionsRequest(
    val userId: String,
    val userName: String,
)

@Serializable
data class RegistrationOptionsResponse(
    val challenge: String,
    val rp: RelyingParty,
    val user: UserEntity,
    val pubKeyCredParams: List<PubKeyCredParam>,
    val timeout: Long = 60000,
    val attestation: String = "none",
    val authenticatorSelection: AuthenticatorSelection,
    val extensions: Extensions? = null,
)

@Serializable data class RelyingParty(
    val id: String,
    val name: String,
)

@Serializable data class UserEntity(
    val id: String,
    val name: String,
    val displayName: String,
)

@Serializable data class PubKeyCredParam(
    val type: String = "public-key",
    val alg: Int,
)

@Serializable
data class AuthenticatorSelection(
    val authenticatorAttachment: String? = null,
    val residentKey: String = "required",
    val userVerification: String = "required",
)

@Serializable data class Extensions(
    val prf: PrfExtension? = null,
)

@Serializable data class PrfExtension(
    val eval: PrfEval? = null,
)

@Serializable data class PrfEval(
    val first: String? = null,
    val second: String? = null,
)

@Serializable data class AuthenticationOptionsRequest(
    val userId: String? = null,
)

@Serializable
data class AuthenticationOptionsResponse(
    val challenge: String,
    val rpId: String,
    val timeout: Long = 60000,
    val userVerification: String = "required",
    val allowCredentials: List<AllowCredential>,
)

@Serializable
data class AllowCredential(
    val type: String = "public-key",
    val id: String,
    val transports: List<String>? = null,
)

@Serializable
data class VerifyResponse(
    val success: Boolean,
    val credentialId: String? = null,
    val userId: String? = null,
    val message: String? = null,
)
