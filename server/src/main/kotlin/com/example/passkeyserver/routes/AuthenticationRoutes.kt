package com.example.passkeyserver.routes

import com.example.passkeyserver.model.AuthenticationOptionsRequest
import com.example.passkeyserver.model.AuthenticationVerifyRequest
import com.example.passkeyserver.webauthn.WebAuthnService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

// These will be set via environment or config
private val rpId = System.getenv("RP_ID") ?: "localhost"
private val rpName = "Passkey PRF POC"
private val origin = System.getenv("ORIGIN") ?: "https://localhost:8080"
private val androidOrigin = System.getenv("ANDROID_ORIGIN")

private val webAuthnService = WebAuthnService(rpId, rpName, origin, androidOrigin)

fun Route.authenticationRoutes() {
    route("/authenticate") {

        /** POST /authenticate/options Generate authentication options (PRF handled by client) */
        post("/options") {
            call.receive<AuthenticationOptionsRequest>() // Still accept request body for future
            // extensibility

            println("Authentication options requested")

            val options = webAuthnService.generateAuthenticationOptions()

            call.respond(options)
        }

        /** POST /authenticate/verify Verify authentication assertion */
        post("/verify") {
            val request = call.receive<AuthenticationVerifyRequest>()
            val challengeB64 = call.request.queryParameters["challenge"] ?: ""

            println("Verifying authentication, credential: ${request.id}")

            val result = webAuthnService.verifyAuthentication(request, challengeB64)

            if (result.success) {
                call.respond(HttpStatusCode.OK, result)
            } else {
                call.respond(HttpStatusCode.Unauthorized, result)
            }
        }
    }
}
