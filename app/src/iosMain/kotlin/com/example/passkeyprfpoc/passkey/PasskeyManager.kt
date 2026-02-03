package com.example.passkeyprfpoc.passkey

import com.example.passkeyprfpoc.api.AuthenticationOptionsResponse
import com.example.passkeyprfpoc.api.RegistrationOptionsResponse
import com.passkeyvault.platform.PlatformContext
import com.passkeyvault.util.decodeBase64Url
import com.passkeyvault.util.encodeBase64Url
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import platform.AuthenticationServices.ASAuthorization
import platform.AuthenticationServices.ASAuthorizationController
import platform.AuthenticationServices.ASAuthorizationControllerDelegateProtocol
import platform.AuthenticationServices.ASAuthorizationControllerPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASAuthorizationPlatformPublicKeyCredentialAssertion
import platform.AuthenticationServices.ASAuthorizationPlatformPublicKeyCredentialDescriptor
import platform.AuthenticationServices.ASAuthorizationPlatformPublicKeyCredentialProvider
import platform.AuthenticationServices.ASAuthorizationPlatformPublicKeyCredentialRegistration
import platform.AuthenticationServices.ASAuthorizationPublicKeyCredentialAttestationKindNone
import platform.AuthenticationServices.ASAuthorizationPublicKeyCredentialPRFAssertionInput
import platform.AuthenticationServices.ASAuthorizationPublicKeyCredentialPRFAssertionInputValues
import platform.AuthenticationServices.ASAuthorizationPublicKeyCredentialPRFRegistrationInput
import platform.AuthenticationServices.ASAuthorizationPublicKeyCredentialUserVerificationPreferenceDiscouraged
import platform.AuthenticationServices.ASAuthorizationPublicKeyCredentialUserVerificationPreferencePreferred
import platform.AuthenticationServices.ASAuthorizationPublicKeyCredentialUserVerificationPreferenceRequired
import platform.AuthenticationServices.ASAuthorizationRequest
import platform.Foundation.NSClassFromString
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSProcessInfo
import platform.Foundation.dataWithBytes
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val MIN_PRF_IOS_VERSION = 18

actual class PasskeyManager {
    actual suspend fun createPasskey(
        platformContext: PlatformContext,
        options: RegistrationOptionsResponse,
    ): Result<String> =
        runCatching {
            requirePrfSupport()

            val provider = ASAuthorizationPlatformPublicKeyCredentialProvider(options.rp.id)
            val challengeData = decodeBase64Url(options.challenge).toNSData()
            val userIdData = decodeBase64Url(options.user.id).toNSData()

            val request =
                provider.createCredentialRegistrationRequestWithChallenge(
                    challenge = challengeData,
                    name = options.user.name,
                    userID = userIdData,
                )

            request.userVerificationPreference =
                mapUserVerificationPreference(options.authenticatorSelection.userVerification)
            request.attestationPreference = mapAttestationPreference(options.attestation)
            request.prf = ASAuthorizationPublicKeyCredentialPRFRegistrationInput.checkForSupport()

            val authorization = performAuthorization(platformContext, request)
            val credential =
                authorization.credential as? ASAuthorizationPlatformPublicKeyCredentialRegistration
                    ?: error("Unexpected credential type returned from registration")

            buildRegistrationResponseJson(credential)
        }

    actual suspend fun authenticateWithPrf(
        platformContext: PlatformContext,
        options: AuthenticationOptionsResponse,
        prfSalt: ByteArray,
    ): Result<PasskeyAuthResult> =
        runCatching {
            requirePrfSupport()

            val provider = ASAuthorizationPlatformPublicKeyCredentialProvider(options.rpId)
            val challengeData = decodeBase64Url(options.challenge).toNSData()
            val request = provider.createCredentialAssertionRequestWithChallenge(challengeData)

            if (options.allowCredentials.isNotEmpty()) {
                val descriptors =
                    options.allowCredentials.map { cred ->
                        val credentialId = decodeBase64Url(cred.id).toNSData()
                        ASAuthorizationPlatformPublicKeyCredentialDescriptor(credentialId)
                    }
                request.allowedCredentials = descriptors
            }

            request.userVerificationPreference =
                mapUserVerificationPreference(options.userVerification)

            val prfInputValues =
                ASAuthorizationPublicKeyCredentialPRFAssertionInputValues(
                    prfSalt.toNSData(),
                    null,
                )
            request.prf = ASAuthorizationPublicKeyCredentialPRFAssertionInput(prfInputValues, null)

            val authorization = performAuthorization(platformContext, request)
            val assertion =
                authorization.credential as? ASAuthorizationPlatformPublicKeyCredentialAssertion
                    ?: error("Unexpected credential type returned from assertion")

            val responseJson = buildAssertionResponseJson(assertion)
            val prfOutput = assertion.prf()?.first?.toByteArray()

            PasskeyAuthResult(responseJson, prfOutput)
        }
}

private suspend fun performAuthorization(
    platformContext: UIViewController,
    request: ASAuthorizationRequest,
): ASAuthorization {
    val anchor = resolvePresentationAnchor(platformContext)

    return suspendCancellableCoroutine { continuation ->
        val handler = AuthorizationHandler(anchor, continuation)
        val controller = ASAuthorizationController(listOf(request))
        handler.controller = controller
        controller.delegate = handler
        controller.presentationContextProvider = handler
        controller.performRequests()

        continuation.invokeOnCancellation { controller.cancel() }
    }
}

private class AuthorizationHandler(
    private val anchor: UIWindow,
    private val continuation: CancellableContinuation<ASAuthorization>,
) : NSObject(),
    ASAuthorizationControllerDelegateProtocol,
    ASAuthorizationControllerPresentationContextProvidingProtocol {
    var controller: ASAuthorizationController? = null

    override fun presentationAnchorForAuthorizationController(controller: ASAuthorizationController): UIWindow? = anchor

    override fun authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithAuthorization: ASAuthorization,
    ) {
        if (continuation.isActive) {
            continuation.resume(didCompleteWithAuthorization)
        }
    }

    override fun authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithError: NSError,
    ) {
        if (continuation.isActive) {
            continuation.resumeWithException(AuthorizationException(didCompleteWithError))
        }
    }
}

private class AuthorizationException(
    error: NSError,
) : Exception(error.localizedDescription)

private fun resolvePresentationAnchor(platformContext: UIViewController): UIWindow {
    val keyWindow =
        UIApplication.sharedApplication.windows
            .filterIsInstance<UIWindow>()
            .firstOrNull { it.isKeyWindow() }
    return platformContext.view.window
        ?: keyWindow
        ?: error("Unable to find a presentation window for passkey authorization")
}

private fun requirePrfSupport() {
    if (!isPrfSupported()) {
        throw UnsupportedOperationException("PRF requires iOS 18 or newer.")
    }
}

@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
private fun isPrfSupported(): Boolean {
    val version = NSProcessInfo.processInfo.operatingSystemVersion.useContents { majorVersion }
    if (version < MIN_PRF_IOS_VERSION) return false
    return NSClassFromString("ASAuthorizationPublicKeyCredentialPRFAssertionInput") != null
}

private fun mapUserVerificationPreference(value: String?): String? =
    when (value?.lowercase()) {
        "required" -> ASAuthorizationPublicKeyCredentialUserVerificationPreferenceRequired
        "preferred" -> ASAuthorizationPublicKeyCredentialUserVerificationPreferencePreferred
        "discouraged" -> ASAuthorizationPublicKeyCredentialUserVerificationPreferenceDiscouraged
        else -> null
    }

private fun mapAttestationPreference(value: String?): String? =
    when (value?.lowercase()) {
        "none" -> ASAuthorizationPublicKeyCredentialAttestationKindNone
        "direct" -> platform.AuthenticationServices.ASAuthorizationPublicKeyCredentialAttestationKindDirect
        "indirect" -> platform.AuthenticationServices.ASAuthorizationPublicKeyCredentialAttestationKindIndirect
        "enterprise" -> platform.AuthenticationServices.ASAuthorizationPublicKeyCredentialAttestationKindEnterprise
        else -> null
    }

private fun buildRegistrationResponseJson(credential: ASAuthorizationPlatformPublicKeyCredentialRegistration): String {
    val credentialId = credential.credentialID().toByteArray()
    val clientData = credential.rawClientDataJSON().toByteArray()
    val attestationObject =
        requireNotNull(credential.rawAttestationObject()) {
            "Missing attestation object from registration"
        }.toByteArray()

    val responseJson =
        buildJsonObject {
            put("clientDataJSON", encodeBase64Url(clientData))
            put("attestationObject", encodeBase64Url(attestationObject))
        }

    val prfOutput = credential.prf()
    val extensionResults =
        prfOutput?.let { output ->
            buildJsonObject {
                putJsonObject("prf") {
                    put("enabled", output.isSupported)
                    val first = output.first?.toByteArray()
                    val second = output.second?.toByteArray()
                    if (first != null || second != null) {
                        putJsonObject("results") {
                            first?.let { put("first", encodeBase64Url(it)) }
                            second?.let { put("second", encodeBase64Url(it)) }
                        }
                    }
                }
            }
        }

    return buildJsonObject {
        val idB64 = encodeBase64Url(credentialId)
        put("id", idB64)
        put("rawId", idB64)
        put("type", "public-key")
        put("response", responseJson)
        extensionResults?.let { put("clientExtensionResults", it) }
    }.toString()
}

private fun buildAssertionResponseJson(assertion: ASAuthorizationPlatformPublicKeyCredentialAssertion): String {
    val credentialId = assertion.credentialID().toByteArray()
    val clientData = assertion.rawClientDataJSON().toByteArray()
    val authenticatorData =
        requireNotNull(assertion.rawAuthenticatorData()) {
            "Missing authenticator data from assertion"
        }.toByteArray()
    val signature =
        requireNotNull(assertion.signature()) { "Missing signature from assertion" }
            .toByteArray()

    val responseJson =
        buildJsonObject {
            put("clientDataJSON", encodeBase64Url(clientData))
            put("authenticatorData", encodeBase64Url(authenticatorData))
            put("signature", encodeBase64Url(signature))
            assertion.userID()?.let { put("userHandle", encodeBase64Url(it.toByteArray())) }
        }

    val prfOutput = assertion.prf()
    val extensionResults =
        prfOutput?.let { output ->
            buildJsonObject {
                putJsonObject("prf") {
                    putJsonObject("results") {
                        put("first", encodeBase64Url(output.first.toByteArray()))
                        output.second?.let { put("second", encodeBase64Url(it.toByteArray())) }
                    }
                }
            }
        }

    return buildJsonObject {
        val idB64 = encodeBase64Url(credentialId)
        put("id", idB64)
        put("rawId", idB64)
        put("type", "public-key")
        put("response", responseJson)
        extensionResults?.let { put("clientExtensionResults", it) }
    }.toString()
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData =
    usePinned { pinned ->
        NSData.dataWithBytes(pinned.addressOf(0), size.toULong())
    }

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val pointer = if (size == 0) null else bytes?.reinterpret<ByteVar>()
    return if (size == 0 || pointer == null) {
        ByteArray(0)
    } else {
        pointer.readBytes(size)
    }
}
