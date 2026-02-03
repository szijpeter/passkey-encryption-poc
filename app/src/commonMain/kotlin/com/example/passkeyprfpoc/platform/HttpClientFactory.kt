package com.example.passkeyprfpoc.platform

import io.ktor.client.HttpClient

expect fun createHttpClient(): HttpClient
