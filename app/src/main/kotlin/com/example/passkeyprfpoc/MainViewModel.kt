package com.example.passkeyprfpoc

import android.app.Activity
import android.app.Application
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.passkeyprfpoc.api.ApiClient
import com.example.passkeyprfpoc.BuildConfig
import com.example.passkeyprfpoc.passkey.PasskeyManager
import com.example.passkeyprfpoc.storage.LocalStorage
import com.passkeyvault.PasskeyVault
import com.passkeyvault.model.CredentialDescriptor
import com.passkeyvault.model.EncryptedBlob
import com.passkeyvault.model.EncryptionSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
        private const val TEST_PLAINTEXT =
                "Hello, this is a secret message encrypted with a passkey-derived key! 🔐"
        private const val VAULT_SALT_ID = "demo-encryption"
    }

    private val storage = LocalStorage(application)
    private val passkeyManager = PasskeyManager()
    private var apiClient: ApiClient? = null
    
    // PasskeyVault SDK - the main encryption API
    private val vault = PasskeyVault(application)
    private var currentSession: EncryptionSession? = null

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        // Load saved state or use BuildConfig URL
        val savedUrl = storage.serverUrl.ifEmpty { BuildConfig.SERVER_URL }
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
        apiClient = ApiClient(cleanUrl)

        _uiState.value = _uiState.value.copy(serverUrl = cleanUrl, isServerConfigured = true)
        addLog("Server configured: $cleanUrl")
    }

    /** Create a new passkey. */
    fun createPasskey(activity: Activity) {
        val client =
                apiClient
                        ?: run {
                            addLog("Error: Server not configured")
                            return
                        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            addLog("Starting passkey registration...")

            try {
                // Generate a unique user ID
                val userId = "user-${System.currentTimeMillis()}"
                val userName = "POC User"

                // Get registration options from server
                addLog("Fetching registration options...")
                val options = client.getRegistrationOptions(userId, userName)
                addLog("Got challenge: ${options.challenge.take(20)}...")
                addLog("RP ID: ${options.rp.id}")

                // Create passkey using Credential Manager
                addLog("Creating passkey with PRF extension...")
                val result = passkeyManager.createPasskey(activity, options)

                result.fold(
                        onSuccess = { credentialJson ->
                            addLog("Passkey created locally, verifying with server...")

                            // Verify with server
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
                Log.e(TAG, "Registration error", e)
                addLog("❌ Error: ${e.message}")
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    /**
     * Encrypt test data using PasskeyVault SDK.
     * This demonstrates the SDK's simple API.
     */
    fun encryptData(activity: Activity) {
        val client =
                apiClient
                        ?: run {
                            addLog("Error: Server not configured")
                            return
                        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            addLog("Starting encryption with PasskeyVault SDK...")

            try {
                // Get authentication options from server
                addLog("Fetching auth options...")
                val options = client.getAuthenticationOptions()
                addLog("Got challenge: ${options.challenge.take(20)}...")

                // Build credential descriptors for SDK
                val credentials = options.allowCredentials.map { cred ->
                    CredentialDescriptor(id = cred.id, transports = cred.transports)
                }

                addLog("Authenticating for encryption via SDK...")
                
                // ========================================
                // THIS IS THE SDK API IN ACTION!
                // One call to get encryption capability
                // ========================================
                val sessionResult = vault.authenticateForEncryption(
                    activity = activity,
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

                            // Verify with server (optional for encryption, but good for audit)
                            // Note: SDK doesn't expose responseJson, so we skip server verify here
                            // In real apps, you'd verify auth separately if needed

                            // ========================================
                            // ENCRYPT WITH ONE LINE!
                            // ========================================
                            addLog("Encrypting test data...")
                            val encrypted = vault.encrypt(session, TEST_PLAINTEXT)

                            // Save encrypted blob
                            val blobBytes = encrypted.toBytes()
                            storage.saveEncryptedBlob(blobBytes)

                            val ciphertextB64 =
                                    Base64.encodeToString(encrypted.ciphertext, Base64.NO_WRAP)

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
                Log.e(TAG, "Encryption error", e)
                addLog("❌ Error: ${e.message}")
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    /**
     * Decrypt stored data using PasskeyVault SDK.
     */
    fun decryptData(activity: Activity) {
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

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            addLog("Starting decryption with PasskeyVault SDK...")

            try {
                // Get authentication options from server
                addLog("Fetching auth options...")
                val options = client.getAuthenticationOptions()

                // Build credential descriptors for SDK
                val credentials = options.allowCredentials.map { cred ->
                    CredentialDescriptor(id = cred.id, transports = cred.transports)
                }

                addLog("Authenticating for decryption via SDK...")

                // ========================================
                // SAME SDK CALL - GET ENCRYPTION SESSION
                // ========================================
                val sessionResult = vault.authenticateForEncryption(
                    activity = activity,
                    challenge = options.challenge,
                    rpId = options.rpId,
                    allowCredentials = credentials,
                    saltId = VAULT_SALT_ID  // Same salt = same key!
                )

                sessionResult.fold(
                        onSuccess = { session ->
                            currentSession = session
                            addLog("✅ Decryption session created!")
                            addLog("Session key hash: ${session.keyHash}")

                            // Check if key matches encryption
                            val lastHash = _uiState.value.lastKeyHash
                            if (lastHash != null && lastHash == session.keyHash) {
                                addLog("✅ Key hash matches encryption session!")
                            }

                            // Load encrypted blob
                            val blobBytes = storage.getEncryptedBlob()
                                ?: run {
                                    addLog("❌ No encrypted blob found")
                                    _uiState.value = _uiState.value.copy(isLoading = false)
                                    return@fold
                                }
                            val blob = EncryptedBlob.fromBytes(blobBytes)

                            // ========================================
                            // DECRYPT WITH ONE LINE!
                            // ========================================
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
                Log.e(TAG, "Decryption error", e)
                addLog("❌ Error: ${e.message}")
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    /** Reset all data. */
    fun resetAll() {
        currentSession?.clear()
        currentSession = null
        storage.clear()
        _uiState.value = UiState()
        addLog("All data cleared")
    }

    private fun addLog(message: String) {
        Log.d(TAG, message)
        val timestamp =
                java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                        .format(java.util.Date())
        val newLog = "[$timestamp] $message"
        _uiState.value = _uiState.value.copy(logs = _uiState.value.logs + newLog)
    }

    override fun onCleared() {
        super.onCleared()
        currentSession?.clear()
        apiClient?.close()
    }
}

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
