rootProject.name = "aws-kotlin-repo-tools"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }

    val kotlinVersion: String by settings
    // configure default plugin versions
    plugins {
        id("org.jetbrains.kotlin.jvm") version kotlinVersion
    }
}


include(":build-plugins")
include(":ktlint-rules")