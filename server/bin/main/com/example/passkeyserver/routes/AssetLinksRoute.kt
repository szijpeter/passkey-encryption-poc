package com.example.passkeyserver.routes

import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Digital Asset Links for Android App Links verification. This allows the Android app to be
 * associated with this domain.
 */
@Serializable
data class AssetLink(val relation: List<String>, val target: AssetLinkTarget)

@Serializable
data class AssetLinkTarget(
    val namespace: String,
    @SerialName("package_name") val packageName: String,
    @SerialName("sha256_cert_fingerprints") val sha256CertFingerprints: List<String>
)

@Serializable
data class AppleAppSiteAssociation(
    val webcredentials: AppleWebCredentials
)

@Serializable
data class AppleWebCredentials(
    val apps: List<String>
)

private const val DEFAULT_ANDROID_SHA256 =
    "00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:" +
        "00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00"

private fun iosAppId(): String {
    val explicit = System.getenv("IOS_APP_ID")
    if (!explicit.isNullOrBlank()) return explicit

    val teamId = System.getenv("IOS_TEAM_ID") ?: "TEAMID"
    val bundleId = System.getenv("IOS_BUNDLE_ID") ?: "com.example.passkeyprfpoc.ios"
    return "$teamId.$bundleId"
}

private fun assetLinksJson(fingerprint: String): String {
    val assetLinks =
        listOf(
            AssetLink(
                relation =
                    listOf(
                        "delegate_permission/common.handle_all_urls",
                        "delegate_permission/common.get_login_creds"
                    ),
                target =
                    AssetLinkTarget(
                        namespace = "android_app",
                        packageName = "com.example.passkeyprfpoc",
                        sha256CertFingerprints = listOf(fingerprint)
                    )
            )
        )

    return Json.encodeToString(ListSerializer(AssetLink.serializer()), assetLinks)
}

private fun appleAssociationJson(): String {
    val association =
        AppleAppSiteAssociation(webcredentials = AppleWebCredentials(apps = listOf(iosAppId())))
    return Json.encodeToString(AppleAppSiteAssociation.serializer(), association)
}

private suspend fun respondJson(call: ApplicationCall, json: String) {
    call.respondText(json, ContentType.Application.Json)
}

private fun infoResponse(): String {
    return """
        Passkey PRF POC Server

        Endpoints:
        - POST /register/options - Get registration options
        - POST /register/verify - Verify registration
        - POST /authenticate/options - Get authentication options
        - POST /authenticate/verify - Verify authentication
        - GET /.well-known/assetlinks.json - Digital Asset Links

        Environment:
        - RP_ID: ${System.getenv("RP_ID") ?: "localhost"}
        - ORIGIN: ${System.getenv("ORIGIN") ?: "https://localhost:8080"}
    """.trimIndent()
}

fun Route.assetLinksRoute() {
    route("/.well-known") {
        get("/assetlinks.json") {
            val fingerprint = System.getenv("APP_SHA256_FINGERPRINT") ?: DEFAULT_ANDROID_SHA256
            respondJson(call, assetLinksJson(fingerprint))
        }

        get("/apple-app-site-association") {
            respondJson(call, appleAssociationJson())
        }
    }

    // Apple also allows the AASA file at the root path.
    get("/apple-app-site-association") {
        respondJson(call, appleAssociationJson())
    }

    // Health check
    get("/health") { call.respondText("OK") }

    // Info endpoint
    get("/") { call.respondText(infoResponse()) }
}
