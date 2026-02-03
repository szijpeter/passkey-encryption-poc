import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
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

android {
    namespace = "com.example.passkeyprfpoc"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.passkeyprfpoc"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        // Server URL from local.properties (set by start-server.sh)
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
    }
}

dependencies {
    implementation(project(":app"))
    implementation(libs.activity.compose)
}
