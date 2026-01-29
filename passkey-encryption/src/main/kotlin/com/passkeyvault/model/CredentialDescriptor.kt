package com.passkeyvault.model

/**
 * Credential descriptor for allowCredentials list. Minimal type that SDK users can easily create.
 */
data class CredentialDescriptor(
        /** Base64URL-encoded credential ID */
        val id: String,
        /** Optional transport hints (e.g., "internal", "hybrid") */
        val transports: List<String>? = null
)
