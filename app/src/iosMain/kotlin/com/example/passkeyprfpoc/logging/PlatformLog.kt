package com.example.passkeyprfpoc.logging

import platform.Foundation.NSLog

actual fun platformLog(
        level: AppLogLevel,
        tag: String,
        message: String,
        throwable: Throwable?
) {
    if (throwable != null) {
        NSLog("%s", "${level.name}/$tag: $message\n${throwable}")
    } else {
        NSLog("%s", "${level.name}/$tag: $message")
    }
}
