import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
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
val serverHost = serverUrl
        .removePrefix("https://")
        .removePrefix("http://")
        .substringBefore("/")

val iosGeneratedDir = layout.buildDirectory.dir("generated/iosMain/kotlin")
val generateIosServerConfig by tasks.registering {
    outputs.dir(iosGeneratedDir)

    doLast {
        val targetDir = iosGeneratedDir.get().asFile
        val packageDir = File(targetDir, "com/example/passkeyprfpoc/platform")
        packageDir.mkdirs()

        val sanitizedUrl = serverUrl.replace("\\", "\\\\").replace("\"", "\\\"")
        val outputFile = File(packageDir, "IosServerConfig.kt")
        outputFile.writeText(
            """
            package com.example.passkeyprfpoc.platform

            internal object IosServerConfig {
                const val DEFAULT_SERVER_URL: String = "$sanitizedUrl"
            }
            """.trimIndent()
        )
    }
}

kotlin {
    applyDefaultHierarchyTemplate()

    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    val iosTargets =
            listOf(
                    iosX64(),
                    iosArm64(),
                    iosSimulatorArm64()
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
            dependencies {
                implementation(libs.activity.compose)
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

tasks.matching { it.name.startsWith("compileKotlinIos") }.configureEach {
    dependsOn(generateIosServerConfig)
}

android {
    namespace = "com.example.passkeyprfpoc"
    compileSdk = 36

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")

    defaultConfig {
        applicationId = "com.example.passkeyprfpoc"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        // Server URL from local.properties (set by start-server.sh)
        buildConfigField("String", "SERVER_URL", "\"$serverUrl\"")
        manifestPlaceholders["SERVER_HOST"] = serverHost
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}
