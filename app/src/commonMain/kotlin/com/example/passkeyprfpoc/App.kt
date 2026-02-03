@file:Suppress("FunctionNaming")

package com.example.passkeyprfpoc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.example.passkeyprfpoc.platform.rememberPlatformContext
import com.example.passkeyprfpoc.ui.MainScreen
import com.example.passkeyprfpoc.ui.theme.PasskeyPRFPOCTheme

@Composable
fun App() {
    val viewModel = remember { MainViewModel() }
    val platformContext = rememberPlatformContext()

    DisposableEffect(Unit) {
        onDispose { viewModel.close() }
    }

    PasskeyPRFPOCTheme { MainScreen(viewModel = viewModel, platformContext = platformContext) }
}
