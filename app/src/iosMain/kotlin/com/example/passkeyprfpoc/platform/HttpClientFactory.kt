package com.example.passkeyprfpoc.platform

import com.example.passkeyprfpoc.logging.AppLogLevel
import com.example.passkeyprfpoc.logging.AppLogger
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import io.ktor.client.plugins.logging.LogLevel as KtorLogLevel

actual fun createHttpClient(): HttpClient =
    HttpClient(Darwin) {
        install(ContentNegotiation) {
            json(
                Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                },
            )
        }
        install(Logging) {
            logger =
                object : Logger {
                    override fun log(message: String) {
                        AppLogger.log(message, level = AppLogLevel.DEBUG, tag = "HTTP")
                    }
                }
            level = KtorLogLevel.ALL
        }
    }
