package com.example.passkeyserver.routes

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Digital Asset Links for Android App Links verification. This allows the Android app to be
 * associated with this domain.
 */
@Serializable data class AssetLink(val relation: List<String>, val target: AssetLinkTarget)

@Serializable
data class AssetLinkTarget(
        val namespace: String,
        val package_name: String,
        val sha256_cert_fingerprints: List<String>
)

@Serializable
data class AppleAppSiteAssociation(
        val webcredentials: AppleWebCredentials
)

@Serializable
data class AppleWebCredentials(
        val apps: List<String>
)

private fun iosAppId(): String {
    val explicit = System.getenv("IOS_APP_ID")
    if (!explicit.isNullOrBlank()) return explicit

    val teamId = System.getenv("IOS_TEAM_ID") ?: "TEAMID"
    val bundleId = System.getenv("IOS_BUNDLE_ID") ?: "com.example.passkeyprfpoc.ios"
    return "$teamId.$bundleId"
}

fun Route.assetLinksRoute() {
    route("/.well-known") {
        get("/assetlinks.json") {
            // Get fingerprint from environment or use placeholder
            val fingerprint =
                    System.getenv("APP_SHA256_FINGERPRINT")
                            ?: "00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00"

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
                                                    package_name = "com.example.passkeyprfpoc",
                                                    sha256_cert_fingerprints = listOf(fingerprint)
                                            )
                            )
                    )

            call.respondText(
                    Json.encodeToString(
                            kotlinx.serialization.builtins.ListSerializer(AssetLink.serializer()),
                            assetLinks
                    ),
                    ContentType.Application.Json
            )
        }

        get("/apple-app-site-association") {
            val association =
                    AppleAppSiteAssociation(webcredentials = AppleWebCredentials(apps = listOf(iosAppId())))
            call.respondText(
                    Json.encodeToString(AppleAppSiteAssociation.serializer(), association),
                    ContentType.Application.Json
            )
        }
    }

    // Apple also allows the AASA file at the root path.
    get("/apple-app-site-association") {
        val association =
                AppleAppSiteAssociation(webcredentials = AppleWebCredentials(apps = listOf(iosAppId())))
        call.respondText(
                Json.encodeToString(AppleAppSiteAssociation.serializer(), association),
                ContentType.Application.Json
        )
    }

    // Health check
    get("/health") { call.respondText("OK") }

    // Info endpoint
    get("/") {
        call.respondText(
                """
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
        )
    }
}
