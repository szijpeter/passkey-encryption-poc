package com.example.passkeyserver.storage

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory storage for credentials and challenges. For POC purposes only - not for production use.
 */
object CredentialStorage {
    // Store challenges temporarily (challenge -> userId)
    private val challenges = ConcurrentHashMap<String, ChallengeData>()

    // Store credentials (credentialId -> CredentialRecord)
    private val credentials = ConcurrentHashMap<String, CredentialRecord>()

    // Store user credentials mapping (userId -> List<credentialId>)
    private val userCredentials = ConcurrentHashMap<String, MutableList<String>>()

    data class ChallengeData(
        val challenge: ByteArray,
        val userId: String,
        val timestamp: Long = System.currentTimeMillis(),
    )

    data class CredentialRecord(
        val credentialId: ByteArray,
        val userId: String,
        val publicKey: ByteArray,
        val signCount: Long,
        val transports: List<String>?,
        val createdAt: Long = System.currentTimeMillis(),
    )

    fun storeChallenge(
        challenge: ByteArray,
        userId: String,
    ) {
        val challengeB64 =
            java.util.Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(challenge)
        challenges[challengeB64] = ChallengeData(challenge, userId)
    }

    fun getChallenge(challengeB64: String): ChallengeData? = challenges.remove(challengeB64)

    fun getChallengeByUserId(userId: String): ChallengeData? {
        val entry = challenges.entries.find { it.value.userId == userId }
        return entry?.let {
            challenges.remove(it.key)
            it.value
        }
    }

    fun storeCredential(record: CredentialRecord) {
        val credIdB64 =
            java.util.Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(record.credentialId)
        credentials[credIdB64] = record

        userCredentials.getOrPut(record.userId) { mutableListOf() }.add(credIdB64)

        println("Stored credential: $credIdB64 for user: ${record.userId}")
    }

    fun getCredential(credentialIdB64: String): CredentialRecord? = credentials[credentialIdB64]

    fun getCredentialsForUser(userId: String): List<CredentialRecord> {
        val credIds = userCredentials[userId] ?: return emptyList()
        return credIds.mapNotNull { credentials[it] }
    }

    fun getAllCredentialIds(): List<String> = credentials.keys.toList()

    fun updateSignCount(
        credentialIdB64: String,
        newSignCount: Long,
    ) {
        credentials[credentialIdB64]?.let { existing ->
            credentials[credentialIdB64] = existing.copy(signCount = newSignCount)
        }
    }

    fun clear() {
        challenges.clear()
        credentials.clear()
        userCredentials.clear()
    }
}
