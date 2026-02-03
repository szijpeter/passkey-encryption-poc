import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

// Read server URL from local.properties
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}
val serverUrl = localProperties.getProperty("server.url", "https://YOUR_NGROK_URL")

val sanitizedServerUrl = serverUrl.replace("\\", "\\\\").replace("\"", "\\\"")
val iosGeneratedDir = layout.buildDirectory.dir("generated/iosMain/kotlin")
val androidGeneratedDir = layout.buildDirectory.dir("generated/androidMain/kotlin")
val generateIosServerConfig by tasks.registering {
    outputs.dir(iosGeneratedDir)

    doLast {
        val targetDir = iosGeneratedDir.get().asFile
        val packageDir = File(targetDir, "com/example/passkeyprfpoc/platform")
        packageDir.mkdirs()

        val outputFile = File(packageDir, "IosServerConfig.kt")
        outputFile.writeText(
            """
            package com.example.passkeyprfpoc.platform

            internal object IosServerConfig {
                const val DEFAULT_SERVER_URL: String = "$sanitizedServerUrl"
            }
            """.trimIndent() + "\n",
        )
    }
}

val generateAndroidServerConfig by tasks.registering {
    outputs.dir(androidGeneratedDir)

    doLast {
        val targetDir = androidGeneratedDir.get().asFile
        val packageDir = File(targetDir, "com/example/passkeyprfpoc/platform")
        packageDir.mkdirs()

        val outputFile = File(packageDir, "AndroidServerConfig.kt")
        outputFile.writeText(
            """
            package com.example.passkeyprfpoc.platform

            internal object AndroidServerConfig {
                const val DEFAULT_SERVER_URL: String = "$sanitizedServerUrl"
            }
            """.trimIndent() + "\n",
        )
    }
}

kotlin {
    applyDefaultHierarchyTemplate()

    android {
        namespace = "com.example.passkeyprfpoc.shared"
        compileSdk = 36
        minSdk = 28

        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }

        lint {
            abortOnError = true
            checkDependencies = true
        }
    }

    val iosTargets =
        listOf(
            iosArm64(),
            iosSimulatorArm64(),
        )
    iosTargets.forEach { target ->
        target.binaries.framework {
            baseName = "ComposeApp"
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":passkey-encryption"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.client.serialization.json)
                implementation(libs.ktor.client.logging)
                implementation(libs.multiplatform.settings.no.arg)

                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
            }
        }
        val androidMain by getting {
            kotlin.srcDir(androidGeneratedDir)
            dependencies {
                implementation(libs.credentials)
                implementation(libs.credentials.play.services)
                implementation(libs.ktor.client.android)
                implementation(libs.kotlinx.coroutines.android)
            }
        }
        val iosMain by getting {
            kotlin.srcDir(iosGeneratedDir)
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
    }
}

tasks
    .matching {
        it.name.startsWith("compileIos") || it.name.startsWith("compileKotlinIos")
    }.configureEach {
        dependsOn(generateIosServerConfig)
    }

tasks.matching { it.name.startsWith("compileAndroid") }.configureEach {
    dependsOn(generateAndroidServerConfig)
}

tasks
    .matching {
        it.name == "runKtlintCheckOverAndroidMainSourceSet" ||
            it.name == "ktlintAndroidMainSourceSetCheck" ||
            it.name == "runKtlintFormatOverAndroidMainSourceSet" ||
            it.name == "ktlintAndroidMainSourceSetFormat"
    }.configureEach {
        dependsOn(generateAndroidServerConfig)
    }

tasks
    .matching {
        it.name == "runKtlintCheckOverIosMainSourceSet" ||
            it.name == "ktlintIosMainSourceSetCheck" ||
            it.name == "runKtlintFormatOverIosMainSourceSet" ||
            it.name == "ktlintIosMainSourceSetFormat"
    }.configureEach {
        dependsOn(generateIosServerConfig)
    }
