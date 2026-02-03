// Top-level build file for Passkey PRF Encryption POC
import io.gitlab.arturbosch.detekt.Detekt
import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.multiplatform.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
}

fun Project.configureLintTasks() {
    tasks.matching { it.name == "check" }.configureEach {
        dependsOn(tasks.matching { it.name == "lint" || it.name == "lintDebug" })
    }
}

subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        apply(plugin = "org.jlleitschuh.gradle.ktlint")
        apply(plugin = "io.gitlab.arturbosch.detekt")
    }
    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        apply(plugin = "org.jlleitschuh.gradle.ktlint")
        apply(plugin = "io.gitlab.arturbosch.detekt")
    }
    plugins.withId("org.jetbrains.kotlin.android") {
        apply(plugin = "org.jlleitschuh.gradle.ktlint")
        apply(plugin = "io.gitlab.arturbosch.detekt")
    }

    pluginManager.withPlugin("org.jlleitschuh.gradle.ktlint") {
        extensions.configure<KtlintExtension> {
            filter {
                exclude("**/build/**")
                exclude("**/generated/**")
            }
        }

        tasks.matching { it.name == "check" }.configureEach {
            dependsOn(
                tasks.matching {
                    it.name == "ktlintCheck" || it.name == "ktlintKotlinScriptCheck"
                }
            )
        }
    }

    pluginManager.withPlugin("io.gitlab.arturbosch.detekt") {
        tasks.withType<Detekt>().configureEach {
            config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
            buildUponDefaultConfig = true
            parallel = true
            basePath = rootDir.absolutePath
            source = fileTree("src")
        }

        tasks.matching { it.name == "check" }.configureEach {
            dependsOn("detekt")
        }
    }

    plugins.withId("com.android.application") {
        configureLintTasks()
    }
    plugins.withId("com.android.library") {
        configureLintTasks()
    }
    plugins.withId("com.android.kotlin.multiplatform.library") {
        configureLintTasks()
    }
}
