package com.example.passkeyserver.plugins

import com.example.passkeyserver.routes.authenticationRoutes
import com.example.passkeyserver.routes.registrationRoutes
import com.example.passkeyserver.routes.assetLinksRoute
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        assetLinksRoute()
        registrationRoutes()
        authenticationRoutes()
    }
}
