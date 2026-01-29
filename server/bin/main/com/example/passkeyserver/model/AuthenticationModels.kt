package com.example.passkeyserver.model

import kotlinx.serialization.Serializable

@Serializable data class AuthenticationOptionsRequest(val userId: String? = null)

@Serializable
data class AuthenticationOptionsResponse(
        val challenge: String,
        val rpId: String,
        val timeout: Long = 60000,
        val userVerification: String = "required",
        val allowCredentials: List<AllowCredential>
)

@Serializable
data class AllowCredential(
        val type: String = "public-key",
        val id: String,
        val transports: List<String>? = null
)

@Serializable
data class AuthenticationVerifyRequest(
        val id: String,
        val rawId: String,
        val type: String,
        val response: AuthenticatorAssertionResponse,
        val clientExtensionResults: ClientExtensionResults? = null
)

@Serializable
data class AuthenticatorAssertionResponse(
        val clientDataJSON: String,
        val authenticatorData: String,
        val signature: String,
        val userHandle: String? = null
)

@Serializable
data class AuthenticationVerifyResponse(
        val success: Boolean,
        val userId: String? = null,
        val message: String? = null
)
