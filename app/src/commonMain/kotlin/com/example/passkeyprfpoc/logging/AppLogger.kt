package com.example.passkeyprfpoc.logging

enum class AppLogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR
}

expect fun platformLog(
        level: AppLogLevel,
        tag: String,
        message: String,
        throwable: Throwable? = null
)

object AppLogger {
    var sink: ((String) -> Unit)? = null

    fun log(
            message: String,
            level: AppLogLevel = AppLogLevel.INFO,
            tag: String = "App",
            throwable: Throwable? = null
    ) {
        val formatted = formatMessage(level, tag, message, throwable)
        platformLog(level, tag, formatted, throwable)
        sink?.invoke(formatted)
    }

    private fun formatMessage(
            level: AppLogLevel,
            tag: String,
            message: String,
            throwable: Throwable?
    ): String {
        val base = "[${level.name}][$tag] $message"
        return if (throwable != null) "$base (${throwable.message ?: "error"})" else base
    }
}
