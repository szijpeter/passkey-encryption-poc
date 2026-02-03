package com.example.passkeyserver.model

import kotlinx.serialization.Serializable

@Serializable
data class RegistrationOptionsRequest(
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

@Serializable
data class RelyingParty(
    val id: String,
    val name: String,
)

@Serializable
data class UserEntity(
    val id: String,
    val name: String,
    val displayName: String,
)

@Serializable
data class PubKeyCredParam(
    val type: String = "public-key",
    val alg: Int,
)

@Serializable
data class AuthenticatorSelection(
    val authenticatorAttachment: String? = null,
    val residentKey: String = "required",
    val userVerification: String = "required",
)

@Serializable
data class Extensions(
    val prf: PrfExtension? = null,
)

@Serializable
data class PrfExtension(
    val eval: PrfEval? = null,
)

@Serializable
data class PrfEval(
    val first: String? = null,
    val second: String? = null,
)

@Serializable
data class RegistrationVerifyRequest(
    val id: String,
    val rawId: String,
    val type: String,
    val response: AuthenticatorAttestationResponse,
    val clientExtensionResults: ClientExtensionResults? = null,
)

@Serializable
data class AuthenticatorAttestationResponse(
    val clientDataJSON: String,
    val attestationObject: String,
    val transports: List<String>? = null,
)

@Serializable
data class ClientExtensionResults(
    val prf: PrfResults? = null,
)

@Serializable
data class PrfResults(
    val enabled: Boolean? = null,
    val results: PrfResultValues? = null,
)

@Serializable
data class PrfResultValues(
    val first: String? = null,
    val second: String? = null,
)

@Serializable
data class RegistrationVerifyResponse(
    val success: Boolean,
    val credentialId: String? = null,
    val message: String? = null,
)
