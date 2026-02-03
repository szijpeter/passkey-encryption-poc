plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    applyDefaultHierarchyTemplate()

    android {
        namespace = "com.passkeyvault"
        compileSdk = 36
        minSdk = 28

        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.multiplatform.settings.no.arg)
                implementation(libs.cryptography.core)
                implementation(libs.cryptography.provider.optimal)
                implementation(libs.cryptography.random)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.credentials)
                implementation(libs.credentials.play.services)
            }
        }
    }
}
