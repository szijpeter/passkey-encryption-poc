package com.example.passkeyserver

import com.example.passkeyserver.plugins.configureRouting
import com.example.passkeyserver.plugins.configureSerialization
import com.example.passkeyserver.plugins.configureCors
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    configureSerialization()
    configureCors()
    configureRouting()
}
