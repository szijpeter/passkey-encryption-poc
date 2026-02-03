package com.example.passkeyprfpoc

import com.example.passkeyprfpoc.api.ApiClient
import com.example.passkeyprfpoc.logging.AppLogLevel
import com.example.passkeyprfpoc.logging.AppLogger
import com.example.passkeyprfpoc.passkey.PasskeyManager
import com.example.passkeyprfpoc.platform.defaultServerUrl
import com.example.passkeyprfpoc.storage.LocalStorage
import com.passkeyvault.PasskeyVault
import com.passkeyvault.model.CredentialDescriptor
import com.passkeyvault.model.EncryptedBlob
import com.passkeyvault.model.EncryptionSession
import com.passkeyvault.platform.PlatformContext
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel {

    companion object {
        private const val TEST_PLAINTEXT =
                "Hello, this is a secret message encrypted with a passkey-derived key! 🔐"
        private const val VAULT_SALT_ID = "demo-encryption"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val storage = LocalStorage()
    private val passkeyManager = PasskeyManager()
    private val vault = PasskeyVault()
    private var apiClient: ApiClient? = null
    private var currentSession: EncryptionSession? = null
    private val uiLogSink: (String) -> Unit = { message ->
        scope.launch { appendLog(message) }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        AppLogger.sink = uiLogSink
        val savedUrl = storage.serverUrl.ifEmpty { defaultServerUrl() }
        if (savedUrl.isNotEmpty() && savedUrl != "https://YOUR_NGROK_URL") {
            storage.serverUrl = savedUrl
            apiClient = ApiClient(savedUrl)
            _uiState.value =
                    _uiState.value.copy(
                            serverUrl = savedUrl,
                            isServerConfigured = true,
                            hasPasskey = storage.hasPasskey,
                            hasEncryptedData = storage.hasEncryptedData()
                    )
        }
    }

    /** Configure the server URL. */
    fun setServerUrl(url: String) {
        val cleanUrl = url.trimEnd('/')
        storage.serverUrl = cleanUrl
        apiClient?.close()
        apiClient = ApiClient(cleanUrl)

        _uiState.value = _uiState.value.copy(serverUrl = cleanUrl, isServerConfigured = true)
        addLog("Server configured: $cleanUrl")
    }

    /** Create a new passkey. */
    fun createPasskey(platformContext: PlatformContext) {
        val client =
                apiClient
                        ?: run {
                            addLog("Error: Server not configured")
                            return
                        }

        scope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            addLog("Starting passkey registration...")

            try {
                val userId = "user-${Random.nextInt(1_000_000)}"
                val userName = "POC User"

                addLog("Fetching registration options...")
                val options = client.getRegistrationOptions(userId, userName)
                addLog("Got challenge: ${options.challenge.take(20)}...")
                addLog("RP ID: ${options.rp.id}")

                addLog("Creating passkey with PRF extension...")
                val result = passkeyManager.createPasskey(platformContext, options)

                result.fold(
                        onSuccess = { credentialJson ->
                            addLog("Passkey created locally, verifying with server...")

                            val verifyResult = client.verifyRegistration(userId, credentialJson)

                            if (verifyResult.success) {
                                storage.hasPasskey = true
                                storage.userId = userId
                                storage.credentialId = verifyResult.credentialId

                                _uiState.value =
                                        _uiState.value.copy(hasPasskey = true, isLoading = false)
                                addLog("✅ Passkey registered successfully!")
                                addLog("Credential ID: ${verifyResult.credentialId?.take(20)}...")
                            } else {
                                addLog("❌ Server verification failed: ${verifyResult.message}")
                                _uiState.value = _uiState.value.copy(isLoading = false)
                            }
                        },
                        onFailure = { error ->
                            addLog("❌ Failed to create passkey: ${error.message}")
                            _uiState.value = _uiState.value.copy(isLoading = false)
                        }
                )
            } catch (e: Exception) {
                addLog("❌ Error: ${e.message}")
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    /** Encrypt test data using PasskeyVault SDK. */
    fun encryptData(platformContext: PlatformContext) {
        val client =
                apiClient
                        ?: run {
                            addLog("Error: Server not configured")
                            return
                        }

        scope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            addLog("Starting encryption with PasskeyVault SDK...")

            try {
                addLog("Fetching auth options...")
                val options = client.getAuthenticationOptions()
                addLog("Got challenge: ${options.challenge.take(20)}...")

                val credentials = options.allowCredentials.map { cred ->
                    CredentialDescriptor(id = cred.id, transports = cred.transports)
                }

                addLog("Authenticating for encryption via SDK...")
                val sessionResult = vault.authenticateForEncryption(
                    platformContext = platformContext,
                    challenge = options.challenge,
                    rpId = options.rpId,
                    allowCredentials = credentials,
                    saltId = VAULT_SALT_ID
                )

                sessionResult.fold(
                        onSuccess = { session ->
                            currentSession = session
                            addLog("✅ Encryption session created!")
                            addLog("Session key hash: ${session.keyHash}")

                            addLog("Encrypting test data...")
                            val encrypted = vault.encrypt(session, TEST_PLAINTEXT)

                            val blobBytes = encrypted.toBytes()
                            storage.saveEncryptedBlob(blobBytes)

                            val ciphertextB64 = encrypted.ciphertext.encodeBase64()

                            _uiState.value =
                                    _uiState.value.copy(
                                            isLoading = false,
                                            hasEncryptedData = true,
                                            encryptedDataB64 = ciphertextB64,
                                            lastKeyHash = session.keyHash
                                    )
                            addLog("✅ Encryption complete!")
                            addLog("Ciphertext: ${ciphertextB64.take(40)}...")
                        },
                        onFailure = { error ->
                            addLog("❌ SDK encryption failed: ${error.message}")
                            _uiState.value = _uiState.value.copy(isLoading = false)
                        }
                )
            } catch (e: Exception) {
                addLog("❌ Error: ${e.message}")
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    /** Decrypt stored data using PasskeyVault SDK. */
    fun decryptData(platformContext: PlatformContext) {
        val client =
                apiClient
                        ?: run {
                            addLog("Error: Server not configured")
                            return
                        }

        if (!storage.hasEncryptedData()) {
            addLog("No encrypted data to decrypt")
            return
        }

        scope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            addLog("Starting decryption with PasskeyVault SDK...")

            try {
                addLog("Fetching auth options...")
                val options = client.getAuthenticationOptions()

                val credentials = options.allowCredentials.map { cred ->
                    CredentialDescriptor(id = cred.id, transports = cred.transports)
                }

                addLog("Authenticating for decryption via SDK...")
                val sessionResult = vault.authenticateForEncryption(
                    platformContext = platformContext,
                    challenge = options.challenge,
                    rpId = options.rpId,
                    allowCredentials = credentials,
                    saltId = VAULT_SALT_ID
                )

                sessionResult.fold(
                        onSuccess = { session ->
                            currentSession = session
                            addLog("✅ Decryption session created!")
                            addLog("Session key hash: ${session.keyHash}")

                            val lastHash = _uiState.value.lastKeyHash
                            if (lastHash != null && lastHash == session.keyHash) {
                                addLog("✅ Key hash matches encryption session!")
                            }

                            val blobBytes = storage.getEncryptedBlob()
                                ?: run {
                                    addLog("❌ No encrypted blob found")
                                    _uiState.value = _uiState.value.copy(isLoading = false)
                                    return@fold
                                }
                            val blob = EncryptedBlob.fromBytes(blobBytes)

                            addLog("Decrypting data...")
                            val plaintext = vault.decryptToString(session, blob)

                            _uiState.value =
                                    _uiState.value.copy(
                                            isLoading = false,
                                            decryptedText = plaintext,
                                            lastKeyHash = session.keyHash
                                    )
                            addLog("✅ Decryption successful!")
                            addLog("Plaintext: $plaintext")
                        },
                        onFailure = { error ->
                            addLog("❌ SDK decryption failed: ${error.message}")
                            _uiState.value = _uiState.value.copy(isLoading = false)
                        }
                )
            } catch (e: Exception) {
                addLog("❌ Error: ${e.message}")
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    /** Reset all data. */
    fun resetAll() {
        currentSession?.clear()
        currentSession = null
        apiClient?.close()
        apiClient = null
        storage.clear()
        _uiState.value = UiState()
        addLog("All data cleared")
    }

    private fun addLog(message: String) {
        AppLogger.log(message, level = message.toLogLevel())
    }

    private fun appendLog(message: String) {
        _uiState.value = _uiState.value.copy(logs = _uiState.value.logs + message)
    }

    fun close() {
        currentSession?.clear()
        apiClient?.close()
        if (AppLogger.sink === uiLogSink) {
            AppLogger.sink = null
        }
        scope.cancel()
    }
}

@OptIn(ExperimentalEncodingApi::class)
private fun ByteArray.encodeBase64(): String = Base64.Default.encode(this)

data class UiState(
        val serverUrl: String = "",
        val isServerConfigured: Boolean = false,
        val isLoading: Boolean = false,
        val hasPasskey: Boolean = false,
        val hasEncryptedData: Boolean = false,
        val encryptedDataB64: String? = null,
        val decryptedText: String? = null,
        val lastPrfHash: String? = null,
        val lastKeyHash: String? = null,
        val logs: List<String> = emptyList()
)

private fun String.toLogLevel(): AppLogLevel {
    return when {
        contains("❌") -> AppLogLevel.ERROR
        contains("⚠️") -> AppLogLevel.WARN
        contains("✅") -> AppLogLevel.INFO
        else -> AppLogLevel.DEBUG
    }
}
