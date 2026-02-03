package com.passkeyvault.util

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
fun decodeBase64Url(value: String): ByteArray =
        Base64.UrlSafe.decode(value.withBase64UrlPadding())

@OptIn(ExperimentalEncodingApi::class)
fun encodeBase64Url(bytes: ByteArray): String = Base64.UrlSafe.encode(bytes)

private fun String.withBase64UrlPadding(): String {
    val remainder = length % 4
    if (remainder == 0) return this
    if (remainder == 1) {
        throw IllegalArgumentException("Invalid base64url length")
    }
    val paddingSize = 4 - remainder
    return this + "=".repeat(paddingSize)
}
