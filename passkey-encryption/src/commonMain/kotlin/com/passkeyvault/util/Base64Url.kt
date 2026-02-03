package com.passkeyvault.util

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private const val BASE64_URL_BLOCK_SIZE = 4
private const val INVALID_BASE64_REMAINDER = 1

@OptIn(ExperimentalEncodingApi::class)
fun decodeBase64Url(value: String): ByteArray = Base64.UrlSafe.decode(value.withBase64UrlPadding())

@OptIn(ExperimentalEncodingApi::class)
fun encodeBase64Url(bytes: ByteArray): String = Base64.UrlSafe.encode(bytes)

private fun String.withBase64UrlPadding(): String {
    val remainder = length % BASE64_URL_BLOCK_SIZE
    if (remainder == 0) return this
    require(remainder != INVALID_BASE64_REMAINDER) { "Invalid base64url length" }
    val paddingSize = BASE64_URL_BLOCK_SIZE - remainder
    return this + "=".repeat(paddingSize)
}
