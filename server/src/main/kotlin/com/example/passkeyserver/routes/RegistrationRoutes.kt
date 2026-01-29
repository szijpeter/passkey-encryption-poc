package com.example.passkeyserver.routes

import com.example.passkeyserver.model.RegistrationOptionsRequest
import com.example.passkeyserver.model.RegistrationVerifyRequest
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

fun Route.registrationRoutes() {
    route("/register") {

        /** GET /register/options Generate registration options for passkey creation */
        post("/options") {
            val request = call.receive<RegistrationOptionsRequest>()

            println("Registration options requested for user: ${request.userId}")

            val options =
                    webAuthnService.generateRegistrationOptions(
                            userId = request.userId,
                            userName = request.userName
                    )

            call.respond(options)
        }

        /** POST /register/verify Verify registration response and store credential */
        post("/verify") {
            val request = call.receive<RegistrationVerifyRequest>()

            // For simplicity, extract userId from the request somehow
            // In real apps, this would come from session
            val userId = call.request.queryParameters["userId"] ?: "default-user"

            println("Verifying registration for user: $userId")

            val result = webAuthnService.verifyRegistration(request, userId)

            if (result.success) {
                call.respond(HttpStatusCode.OK, result)
            } else {
                call.respond(HttpStatusCode.BadRequest, result)
            }
        }
    }
}
