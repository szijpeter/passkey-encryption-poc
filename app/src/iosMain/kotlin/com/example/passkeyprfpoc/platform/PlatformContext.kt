package com.example.passkeyprfpoc.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.uikit.LocalUIViewController
import com.passkeyvault.platform.PlatformContext

@Composable
actual fun rememberPlatformContext(): PlatformContext {
    return LocalUIViewController.current
}
