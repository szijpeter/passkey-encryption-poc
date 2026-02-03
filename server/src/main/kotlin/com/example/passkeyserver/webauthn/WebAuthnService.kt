package com.example.passkeyserver.webauthn

import com.example.passkeyserver.model.AllowCredential
import com.example.passkeyserver.model.AuthenticationOptionsResponse
import com.example.passkeyserver.model.AuthenticationVerifyRequest
import com.example.passkeyserver.model.AuthenticationVerifyResponse
import com.example.passkeyserver.model.AuthenticatorSelection
import com.example.passkeyserver.model.Extensions
import com.example.passkeyserver.model.PrfExtension
import com.example.passkeyserver.model.PubKeyCredParam
import com.example.passkeyserver.model.RegistrationOptionsResponse
import com.example.passkeyserver.model.RegistrationVerifyRequest
import com.example.passkeyserver.model.RegistrationVerifyResponse
import com.example.passkeyserver.model.RelyingParty
import com.example.passkeyserver.model.UserEntity
import com.example.passkeyserver.storage.CredentialStorage
import com.webauthn4j.WebAuthnManager
import com.webauthn4j.converter.AttestedCredentialDataConverter
import com.webauthn4j.converter.util.ObjectConverter
import com.webauthn4j.credential.CredentialRecordImpl
import com.webauthn4j.data.AuthenticationParameters
import com.webauthn4j.data.AuthenticationRequest
import com.webauthn4j.data.AuthenticatorTransport
import com.webauthn4j.data.PublicKeyCredentialParameters
import com.webauthn4j.data.PublicKeyCredentialType
import com.webauthn4j.data.RegistrationParameters
import com.webauthn4j.data.RegistrationRequest
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier
import com.webauthn4j.data.client.Origin
import com.webauthn4j.data.client.challenge.DefaultChallenge
import com.webauthn4j.server.ServerProperty
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.util.Base64

/**
 * WebAuthn service using webauthn4j library. Handles registration and authentication ceremonies.
 */
class WebAuthnService(
    private val rpId: String,
    private val rpName: String,
    private val origin: String,
    private val androidOrigin: String? = null,
) {
    private val logger = LoggerFactory.getLogger(WebAuthnService::class.java)
    private val webAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager()
    private val objectConverter = ObjectConverter()
    private val secureRandom = SecureRandom()

    // Accept both HTTPS and Android APK origins
    private val allowedOrigins: Set<Origin> =
        buildSet {
            add(Origin.create(origin))
            androidOrigin?.let { add(Origin.create(it)) }
        }

    /** Generate registration options for creating a new passkey. */
    fun generateRegistrationOptions(
        userId: String,
        userName: String,
    ): RegistrationOptionsResponse {
        val challengeBytes = newChallenge()
        CredentialStorage.storeChallenge(challengeBytes, userId)

        val challengeB64 = base64UrlEncode(challengeBytes)
        val userIdB64 = base64UrlEncode(userId.toByteArray())

        return RegistrationOptionsResponse(
            challenge = challengeB64,
            rp = RelyingParty(id = rpId, name = rpName),
            user = UserEntity(id = userIdB64, name = userName, displayName = userName),
            pubKeyCredParams =
                listOf(
                    PubKeyCredParam(alg = COSEAlgorithmIdentifier.ES256.value.toInt()),
                    PubKeyCredParam(alg = COSEAlgorithmIdentifier.RS256.value.toInt()),
                ),
            authenticatorSelection =
                AuthenticatorSelection(
                    residentKey = "required",
                    userVerification = "required",
                ),
            extensions = Extensions(prf = PrfExtension()),
        )
    }

    /** Verify registration response and store credential. */
    fun verifyRegistration(
        request: RegistrationVerifyRequest,
        userId: String,
    ): RegistrationVerifyResponse =
        runCatching {
            val clientDataJson = base64UrlDecode(request.response.clientDataJSON)
            val attestationObject = base64UrlDecode(request.response.attestationObject)
            val challengeData =
                CredentialStorage.getChallengeByUserId(userId)
                    ?: throw IllegalStateException(CHALLENGE_NOT_FOUND_MESSAGE)

            val serverProperty = buildServerProperty(challengeData.challenge)
            val registrationData =
                webAuthnManager.verify(
                    RegistrationRequest(attestationObject, clientDataJson),
                    registrationParameters(serverProperty),
                )

            val attestedCredentialData =
                registrationData
                    .attestationObject
                    ?.authenticatorData
                    ?.attestedCredentialData
                    ?: throw IllegalStateException(NO_ATTESTED_CREDENTIAL_MESSAGE)

            val credentialId = attestedCredentialData.credentialId
            val publicKey =
                AttestedCredentialDataConverter(objectConverter)
                    .convert(attestedCredentialData)
            val signCount =
                registrationData
                    .attestationObject
                    ?.authenticatorData
                    ?.signCount
                    ?: 0

            CredentialStorage.storeCredential(
                CredentialStorage.CredentialRecord(
                    credentialId = credentialId,
                    userId = userId,
                    publicKey = publicKey,
                    signCount = signCount,
                    transports = request.response.transports,
                ),
            )

            val credIdB64 = base64UrlEncode(credentialId)
            logger.info("Registration successful! Credential ID: {}", credIdB64)
            logger.info("PRF enabled: {}", request.clientExtensionResults?.prf?.enabled)

            RegistrationVerifyResponse(
                success = true,
                credentialId = credIdB64,
                message = REGISTRATION_SUCCESS_MESSAGE,
            )
        }.getOrElse { e ->
            logger.warn("Registration verification failed: {}", e.message)
            RegistrationVerifyResponse(
                success = false,
                message = "Verification failed: ${e.message}",
            )
        }

    /** Generate authentication options. PRF is handled entirely by the client. */
    fun generateAuthenticationOptions(): AuthenticationOptionsResponse {
        val challengeBytes = newChallenge()
        val challengeB64 = base64UrlEncode(challengeBytes)

        // Store challenge (use empty userId for authentication - we'll get it from assertion)
        CredentialStorage.storeChallenge(challengeBytes, "auth-$challengeB64")

        val allowCredentials =
            CredentialStorage.getAllCredentialIds().map { credId ->
                val cred = CredentialStorage.getCredential(credId)
                AllowCredential(id = credId, transports = cred?.transports)
            }

        return AuthenticationOptionsResponse(
            challenge = challengeB64,
            rpId = rpId,
            allowCredentials = allowCredentials,
        )
    }

    /** Verify authentication assertion. */
    fun verifyAuthentication(
        request: AuthenticationVerifyRequest,
        challengeB64: String,
    ): AuthenticationVerifyResponse =
        runCatching {
            val credentialId = base64UrlDecode(request.rawId)
            val clientDataJson = base64UrlDecode(request.response.clientDataJSON)
            val authenticatorData = base64UrlDecode(request.response.authenticatorData)
            val signature = base64UrlDecode(request.response.signature)

            val credIdB64 = base64UrlEncode(credentialId)
            val storedCredential =
                CredentialStorage.getCredential(credIdB64)
                    ?: throw IllegalStateException(CREDENTIAL_NOT_FOUND_MESSAGE)
            val challengeData =
                CredentialStorage.getChallenge(challengeB64)
                    ?: throw IllegalStateException(CHALLENGE_NOT_FOUND_MESSAGE)

            val serverProperty = buildServerProperty(challengeData.challenge)
            val credentialRecord = buildCredentialRecord(storedCredential)
            val authenticationRequest =
                AuthenticationRequest(
                    // credentialId =
                    credentialId,
                    // userHandle =
                    request.response.userHandle?.let { base64UrlDecode(it) },
                    // authenticatorData =
                    authenticatorData,
                    // clientDataJSON =
                    clientDataJson,
                    // clientExtensionsJSON =
                    null,
                    // signature =
                    signature,
                )
            val authenticationParameters =
                authenticationParameters(serverProperty, credentialRecord, credentialId)
            val authenticationData =
                webAuthnManager.verify(authenticationRequest, authenticationParameters)

            CredentialStorage.updateSignCount(
                credentialIdB64 = credIdB64,
                newSignCount = authenticationData.authenticatorData?.signCount ?: 0,
            )

            logger.info("Authentication successful!")
            logger.info("PRF results: {}", request.clientExtensionResults?.prf?.results)

            AuthenticationVerifyResponse(
                success = true,
                userId = storedCredential.userId,
                message = AUTHENTICATION_SUCCESS_MESSAGE,
            )
        }.getOrElse { e ->
            logger.warn("Authentication verification failed: {}", e.message)
            AuthenticationVerifyResponse(
                success = false,
                message = "Verification failed: ${e.message}",
            )
        }

    private fun newChallenge(): ByteArray {
        val challengeBytes = ByteArray(CHALLENGE_SIZE)
        secureRandom.nextBytes(challengeBytes)
        return challengeBytes
    }

    private fun buildServerProperty(challengeBytes: ByteArray): ServerProperty {
        val challenge = DefaultChallenge(challengeBytes)
        return ServerProperty
            .builder()
            .origins(allowedOrigins)
            .rpId(rpId)
            .challenge(challenge)
            .build()
    }

    private fun registrationParameters(serverProperty: ServerProperty): RegistrationParameters =
        RegistrationParameters(
            serverProperty,
            listOf(
                PublicKeyCredentialParameters(
                    PublicKeyCredentialType.PUBLIC_KEY,
                    COSEAlgorithmIdentifier.ES256,
                ),
                PublicKeyCredentialParameters(
                    PublicKeyCredentialType.PUBLIC_KEY,
                    COSEAlgorithmIdentifier.RS256,
                ),
            ),
            false,
            true,
        )

    private fun authenticationParameters(
        serverProperty: ServerProperty,
        credentialRecord: CredentialRecordImpl,
        credentialId: ByteArray,
    ): AuthenticationParameters =
        AuthenticationParameters(
            // serverProperty =
            serverProperty,
            // credentialRecord =
            credentialRecord,
            // allowCredentials =
            listOf(credentialId),
            // userVerificationRequired =
            false,
            // userPresenceRequired =
            true,
        )

    private fun buildCredentialRecord(storedCredential: CredentialStorage.CredentialRecord): CredentialRecordImpl {
        val attestedCredentialData =
            AttestedCredentialDataConverter(objectConverter)
                .convert(storedCredential.publicKey)
        val transports =
            storedCredential.transports
                ?.map { AuthenticatorTransport.create(it) }
                ?.toSet()

        return CredentialRecordImpl(
            // attestationStatement =
            null,
            // uvInitialized =
            null,
            // backupEligible =
            null,
            // backupState =
            null,
            // counter =
            storedCredential.signCount,
            // attestedCredentialData =
            attestedCredentialData,
            // authenticatorExtensions =
            null,
            // clientData =
            null,
            // clientExtensions =
            null,
            // transports =
            transports,
        )
    }

    companion object {
        private const val CHALLENGE_SIZE = 32
        private const val CHALLENGE_NOT_FOUND_MESSAGE = "Challenge not found or expired"
        private const val CREDENTIAL_NOT_FOUND_MESSAGE = "Credential not found"
        private const val NO_ATTESTED_CREDENTIAL_MESSAGE = "No attested credential data found"
        private const val REGISTRATION_SUCCESS_MESSAGE = "Passkey registered successfully"
        private const val AUTHENTICATION_SUCCESS_MESSAGE = "Authentication successful"
    }
}

private fun base64UrlEncode(value: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(value)

private fun base64UrlDecode(value: String): ByteArray = Base64.getUrlDecoder().decode(value)
