package com.example.passkeyprfpoc.platform

import androidx.compose.runtime.Composable
import com.passkeyvault.platform.PlatformContext

@Composable
expect fun rememberPlatformContext(): PlatformContext
