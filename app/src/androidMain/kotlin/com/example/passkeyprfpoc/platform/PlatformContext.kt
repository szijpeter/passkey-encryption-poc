package com.example.passkeyprfpoc.platform

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.passkeyvault.platform.PlatformContext

@Composable
actual fun rememberPlatformContext(): PlatformContext {
    val context = LocalContext.current
    return remember(context) {
        context.findActivity()
            ?: error("Passkey operations require an Activity context")
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
