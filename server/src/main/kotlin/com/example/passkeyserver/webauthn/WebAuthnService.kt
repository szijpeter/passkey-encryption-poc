package com.example.passkeyserver.webauthn

import com.example.passkeyserver.model.*
import com.example.passkeyserver.storage.CredentialStorage
import com.webauthn4j.WebAuthnManager
import com.webauthn4j.converter.AttestedCredentialDataConverter
import com.webauthn4j.converter.util.ObjectConverter
import com.webauthn4j.data.*
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier
import com.webauthn4j.data.client.Origin
import com.webauthn4j.data.client.challenge.DefaultChallenge
import com.webauthn4j.credential.CredentialRecordImpl
import com.webauthn4j.server.ServerProperty
import com.webauthn4j.data.AuthenticatorTransport
import java.security.SecureRandom
import java.util.Base64

/**
 * WebAuthn service using webauthn4j library. Handles registration and authentication ceremonies.
 */
class WebAuthnService(
        private val rpId: String,
        private val rpName: String,
        private val origin: String,
        private val androidOrigin: String? = null
) {
        private val webAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager()
        private val objectConverter = ObjectConverter()
        private val secureRandom = SecureRandom()

        // Accept both HTTPS and Android APK origins
        private val allowedOrigins: Set<Origin> = buildSet {
                add(Origin.create(origin))
                androidOrigin?.let { add(Origin.create(it)) }
        }

        /** Generate registration options for creating a new passkey. */
        fun generateRegistrationOptions(
                userId: String,
                userName: String
        ): RegistrationOptionsResponse {
                // Generate a random challenge
                val challengeBytes = ByteArray(32)
                secureRandom.nextBytes(challengeBytes)

                // Store challenge for later verification
                CredentialStorage.storeChallenge(challengeBytes, userId)

                val challengeB64 =
                        Base64.getUrlEncoder().withoutPadding().encodeToString(challengeBytes)
                val userIdB64 =
                        Base64.getUrlEncoder().withoutPadding().encodeToString(userId.toByteArray())

                return RegistrationOptionsResponse(
                        challenge = challengeB64,
                        rp = RelyingParty(id = rpId, name = rpName),
                        user = UserEntity(id = userIdB64, name = userName, displayName = userName),
                        pubKeyCredParams =
                                listOf(
                                        PubKeyCredParam(
                                                alg = COSEAlgorithmIdentifier.ES256.value.toInt()
                                        ),
                                        PubKeyCredParam(
                                                alg = COSEAlgorithmIdentifier.RS256.value.toInt()
                                        )
                                ),
                        authenticatorSelection =
                                AuthenticatorSelection(
                                        residentKey = "required",
                                        userVerification = "required"
                                ),
                        extensions =
                                Extensions(
                                        prf = PrfExtension() // Request PRF support
                                )
                )
        }

        /** Verify registration response and store credential. */
        fun verifyRegistration(
                request: RegistrationVerifyRequest,
                userId: String
        ): RegistrationVerifyResponse {
                return try {
                        val clientDataJSON =
                                Base64.getUrlDecoder().decode(request.response.clientDataJSON)
                        val attestationObject =
                                Base64.getUrlDecoder().decode(request.response.attestationObject)

                        // Get the stored challenge
                        val challengeData =
                                CredentialStorage.getChallengeByUserId(userId)
                                        ?: return RegistrationVerifyResponse(
                                                success = false,
                                                message = "Challenge not found or expired"
                                        )

                        val challenge = DefaultChallenge(challengeData.challenge)
                        val serverProperty =
                                ServerProperty.builder()
                                        .origins(allowedOrigins)
                                        .rpId(rpId)
                                        .challenge(challenge)
                                        .build()

                        val registrationRequest =
                                RegistrationRequest(attestationObject, clientDataJSON)

                        val registrationParameters =
                                RegistrationParameters(
                                        serverProperty,
                                        listOf(
                                                PublicKeyCredentialParameters(
                                                        PublicKeyCredentialType.PUBLIC_KEY,
                                                        COSEAlgorithmIdentifier.ES256
                                                ),
                                                PublicKeyCredentialParameters(
                                                        PublicKeyCredentialType.PUBLIC_KEY,
                                                        COSEAlgorithmIdentifier.RS256
                                                )
                                        ),
                                        false, // userVerificationRequired - set to false for
                                        // flexibility
                                        true // userPresenceRequired
                                )

                        val registrationData =
                                webAuthnManager.verify(registrationRequest, registrationParameters)

                        // Extract credential data
                        val attestedCredentialData =
                                registrationData
                                        .attestationObject
                                        ?.authenticatorData
                                        ?.attestedCredentialData
                        if (attestedCredentialData == null) {
                                return RegistrationVerifyResponse(
                                        success = false,
                                        message = "No attested credential data found"
                                )
                        }

                        val credentialId = attestedCredentialData.credentialId
                        val publicKey =
                                AttestedCredentialDataConverter(objectConverter)
                                        .convert(attestedCredentialData)

                        // Store credential
                        CredentialStorage.storeCredential(
                                CredentialStorage.CredentialRecord(
                                        credentialId = credentialId,
                                        userId = userId,
                                        publicKey = publicKey,
                                        signCount =
                                                registrationData
                                                        .attestationObject
                                                        ?.authenticatorData
                                                        ?.signCount
                                                        ?: 0,
                                        transports = request.response.transports
                                )
                        )

                        val credIdB64 =
                                Base64.getUrlEncoder().withoutPadding().encodeToString(credentialId)

                        println("Registration successful! Credential ID: $credIdB64")
                        println("PRF enabled: ${request.clientExtensionResults?.prf?.enabled}")

                        RegistrationVerifyResponse(
                                success = true,
                                credentialId = credIdB64,
                                message = "Passkey registered successfully"
                        )
                } catch (e: Exception) {
                        println("Registration verification failed: ${e.message}")
                        e.printStackTrace()
                        RegistrationVerifyResponse(
                                success = false,
                                message = "Verification failed: ${e.message}"
                        )
                }
        }

        /** Generate authentication options. PRF is handled entirely by the client. */
        fun generateAuthenticationOptions(): AuthenticationOptionsResponse {
                // Generate a random challenge
                val challengeBytes = ByteArray(32)
                secureRandom.nextBytes(challengeBytes)

                val challengeB64 =
                        Base64.getUrlEncoder().withoutPadding().encodeToString(challengeBytes)

                // Store challenge (use empty userId for authentication - we'll get it from
                // assertion)
                CredentialStorage.storeChallenge(challengeBytes, "auth-$challengeB64")

                // Get all stored credentials
                val allowCredentials =
                        CredentialStorage.getAllCredentialIds().map { credId ->
                                val cred = CredentialStorage.getCredential(credId)
                                AllowCredential(id = credId, transports = cred?.transports)
                        }

                return AuthenticationOptionsResponse(
                        challenge = challengeB64,
                        rpId = rpId,
                        allowCredentials = allowCredentials
                )
        }

        /** Verify authentication assertion. */
        fun verifyAuthentication(
                request: AuthenticationVerifyRequest,
                challengeB64: String
        ): AuthenticationVerifyResponse {
                return try {
                        val credentialId = Base64.getUrlDecoder().decode(request.rawId)
                        val clientDataJSON =
                                Base64.getUrlDecoder().decode(request.response.clientDataJSON)
                        val authenticatorData =
                                Base64.getUrlDecoder().decode(request.response.authenticatorData)
                        val signature = Base64.getUrlDecoder().decode(request.response.signature)

                        // Get stored credential
                        val credIdB64 =
                                Base64.getUrlEncoder().withoutPadding().encodeToString(credentialId)
                        val storedCredential =
                                CredentialStorage.getCredential(credIdB64)
                                        ?: return AuthenticationVerifyResponse(
                                                success = false,
                                                message = "Credential not found"
                                        )

                        // Get the stored challenge
                        val challengeData =
                                CredentialStorage.getChallenge(challengeB64)
                                        ?: return AuthenticationVerifyResponse(
                                                success = false,
                                                message = "Challenge not found or expired"
                                        )

                        val challenge = DefaultChallenge(challengeData.challenge)
                        val serverProperty =
                                ServerProperty.builder()
                                        .origins(allowedOrigins)
                                        .rpId(rpId)
                                        .challenge(challenge)
                                        .build()

                        // Reconstruct authenticator from stored data
                        val attestedCredentialDataConverter =
                                AttestedCredentialDataConverter(objectConverter)
                        val attestedCredentialData =
                                attestedCredentialDataConverter.convert(storedCredential.publicKey)

                        val transports =
                                storedCredential.transports
                                        ?.map { AuthenticatorTransport.create(it) }
                                        ?.toSet()
                        val credentialRecord =
                                CredentialRecordImpl(
                                    /* attestationStatement = */ null,
                                    /* uvInitialized = */ null,
                                    /* backupEligible = */ null,
                                    /* backupState = */ null,
                                    /* counter = */ storedCredential.signCount,
                                    /* attestedCredentialData = */ attestedCredentialData,
                                    /* authenticatorExtensions = */ null,
                                    /* clientData = */ null,
                                    /* clientExtensions = */ null,
                                    /* transports = */ transports
                                )

                        val authenticationRequest =
                                AuthenticationRequest(
                                    /* credentialId = */ credentialId,
                                    /* userHandle = */ request.response.userHandle?.let {
                                                Base64.getUrlDecoder().decode(it)
                                        },
                                    /* authenticatorData = */ authenticatorData,
                                    /* clientDataJSON = */ clientDataJSON,
                                    /* clientExtensionsJSON = */ null,
                                    /* signature = */ signature
                                )

                        val authenticationParameters =
                                AuthenticationParameters(
                                    /* serverProperty = */ serverProperty,
                                    /* credentialRecord = */ credentialRecord,
                                    /* allowCredentials = */ listOf(credentialId),
                                    /* userVerificationRequired = */ false,
                                    /* userPresenceRequired = */ true
                                )

                        val authenticationData =
                                webAuthnManager.verify(
                                    /* authenticationRequest = */ authenticationRequest,
                                    /* authenticationParameters = */ authenticationParameters
                                )

                        // Update sign count
                        CredentialStorage.updateSignCount(
                            credentialIdB64 = credIdB64,
                            newSignCount = authenticationData.authenticatorData?.signCount ?: 0
                        )

                        println("Authentication successful!")
                        println("PRF results: ${request.clientExtensionResults?.prf?.results}")

                        AuthenticationVerifyResponse(
                                success = true,
                                userId = storedCredential.userId,
                                message = "Authentication successful"
                        )
                } catch (e: Exception) {
                        println("Authentication verification failed: ${e.message}")
                        e.printStackTrace()
                        AuthenticationVerifyResponse(
                                success = false,
                                message = "Verification failed: ${e.message}"
                        )
                }
        }
}
