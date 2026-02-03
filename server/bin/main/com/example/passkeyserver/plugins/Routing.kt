package com.example.passkeyserver.plugins

import com.example.passkeyserver.routes.assetLinksRoute
import com.example.passkeyserver.routes.authenticationRoutes
import com.example.passkeyserver.routes.registrationRoutes
import io.ktor.server.application.Application
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    routing {
        assetLinksRoute()
        registrationRoutes()
        authenticationRoutes()
    }
}
