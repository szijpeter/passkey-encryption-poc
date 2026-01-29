package com.example.passkeyprfpoc.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.passkeyprfpoc.MainViewModel
import com.example.passkeyprfpoc.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel, activity: Activity) {
    val uiState by viewModel.uiState.collectAsState()
    var serverUrlInput by remember { mutableStateOf(uiState.serverUrl) }

    Scaffold(
            topBar = {
                TopAppBar(
                        title = { Text("Passkey PRF POC") },
                        colors =
                                TopAppBarDefaults.topAppBarColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                )
            }
    ) { padding ->
        Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Server Configuration
            ServerConfigCard(
                    serverUrl = serverUrlInput,
                    isConfigured = uiState.isServerConfigured,
                    onUrlChange = { serverUrlInput = it },
                    onSave = { viewModel.setServerUrl(serverUrlInput) }
            )

            // Status Card
            StatusCard(uiState = uiState)

            // Action Buttons
            ActionButtons(
                    uiState = uiState,
                    onCreatePasskey = { viewModel.createPasskey(activity) },
                    onEncrypt = { viewModel.encryptData(activity) },
                    onDecrypt = { viewModel.decryptData(activity) },
                    onReset = { viewModel.resetAll() }
            )

            // Results Card
            if (uiState.encryptedDataB64 != null || uiState.decryptedText != null) {
                ResultsCard(uiState = uiState)
            }

            // Debug Logs
            LogsCard(logs = uiState.logs, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun ServerConfigCard(
        serverUrl: String,
        isConfigured: Boolean,
        onUrlChange: (String) -> Unit,
        onSave: () -> Unit
) {
    Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                    CardDefaults.cardColors(
                            containerColor =
                                    if (isConfigured) MaterialTheme.colorScheme.secondaryContainer
                                    else MaterialTheme.colorScheme.errorContainer
                    )
    ) {
        Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                    text = "Server Configuration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                    value = serverUrl,
                    onValueChange = onUrlChange,
                    label = { Text("Server URL (ngrok)") },
                    placeholder = { Text("https://abc123.ngrok.io") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
            )

            Button(onClick = onSave, modifier = Modifier.align(Alignment.End)) { Text("Save") }
        }
    }
}

@Composable
fun StatusCard(uiState: UiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                    text = "Status",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
            )

            Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusIndicator(label = "Server", isActive = uiState.isServerConfigured)
                StatusIndicator(label = "Passkey", isActive = uiState.hasPasskey)
                StatusIndicator(label = "Data", isActive = uiState.hasEncryptedData)
            }

            if (uiState.lastPrfHash != null) {
                Text(
                        text = "PRF Hash: ${uiState.lastPrfHash}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                )
            }
            if (uiState.lastKeyHash != null) {
                Text(
                        text = "Key Hash: ${uiState.lastKeyHash}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun StatusIndicator(label: String, isActive: Boolean) {
    Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
                modifier =
                        Modifier.size(12.dp)
                                .background(
                                        color =
                                                if (isActive) Color(0xFF4CAF50)
                                                else Color(0xFFBDBDBD),
                                        shape = RoundedCornerShape(6.dp)
                                )
        )
        Text(text = label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun ActionButtons(
        uiState: UiState,
        onCreatePasskey: () -> Unit,
        onEncrypt: () -> Unit,
        onDecrypt: () -> Unit,
        onReset: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                    onClick = onCreatePasskey,
                    enabled =
                            uiState.isServerConfigured && !uiState.isLoading && !uiState.hasPasskey,
                    modifier = Modifier.weight(1f)
            ) { Text("Create Passkey") }

            Button(
                    onClick = onEncrypt,
                    enabled = uiState.hasPasskey && !uiState.isLoading,
                    modifier = Modifier.weight(1f)
            ) { Text("Encrypt") }
        }

        Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                    onClick = onDecrypt,
                    enabled = uiState.hasEncryptedData && !uiState.isLoading,
                    modifier = Modifier.weight(1f)
            ) { Text("Decrypt") }

            OutlinedButton(
                    onClick = onReset,
                    enabled = !uiState.isLoading,
                    modifier = Modifier.weight(1f)
            ) { Text("Reset All") }
        }

        if (uiState.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun ResultsCard(uiState: UiState) {
    Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                    CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
    ) {
        Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                    text = "Results",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
            )

            uiState.encryptedDataB64?.let { encrypted ->
                Text(text = "Encrypted:", style = MaterialTheme.typography.labelMedium)
                Text(
                        text = if (encrypted.length > 60) "${encrypted.take(60)}..." else encrypted,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                )
            }

            uiState.decryptedText?.let { decrypted ->
                Divider()
                Text(text = "Decrypted:", style = MaterialTheme.typography.labelMedium)
                Text(
                        text = decrypted,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun LogsCard(logs: List<String>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new logs are added
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Card(
            modifier = modifier.fillMaxWidth(),
            colors =
                    CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                    text = "Debug Log",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(logs) { log ->
                    val color =
                            when {
                                log.contains("✅") -> Color(0xFF4CAF50)
                                log.contains("❌") -> Color(0xFFE53935)
                                log.contains("⚠️") -> Color(0xFFFF9800)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                    Text(
                            text = log,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = color
                    )
                }
            }
        }
    }
}
