/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
    kotlin("jvm")
    `java-gradle-plugin`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    // make our custom lint rules available to the buildscript classpath
    runtimeOnly(project(":ktlint-rules")) {
        // Ensure that kotlin-compiler-embeddable isn't included in the buildscript classpath
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-compiler-embeddable")
    }

    implementation(libs.nexusPublishPlugin)
    compileOnly(gradleApi())
    implementation("aws.sdk.kotlin:s3:1.4.+")
    implementation("aws.sdk.kotlin:cloudwatch:1.4.+")
    testImplementation(libs.junit.jupiter)
}

gradlePlugin {
    plugins {
        create("artifact-size-metrics") {
            id = "aws.sdk.kotlin.gradle.artifactsizemetrics"
            implementationClass = "aws.sdk.kotlin.gradle.plugins.artifactsizemetrics.ArtifactSizeMetricsPlugin"
        }
    }
}

val generateKtlintVersion by tasks.registering {
    // generate the version of the runtime to use as a resource.
    // this keeps us from having to manually change version numbers in multiple places
    val resourcesDir = layout.buildDirectory.dir("resources/main/aws/sdk/kotlin/gradle/dsl").get()

    val versionCatalog = rootProject.file("gradle/libs.versions.toml")
    inputs.file(versionCatalog)

    val versionFile = file("$resourcesDir/ktlint-version.txt")
    outputs.file(versionFile)

    val version = libs.ktlint.cli.ruleset.core.get().version
    sourceSets.main.get().output.dir(resourcesDir)
    doLast {
        versionFile.writeText("$version")
    }
}

tasks.withType<KotlinCompile> {
    dependsOn(generateKtlintVersion)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
        freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
    }
}

tasks.withType<JavaCompile> {
    sourceCompatibility = JavaVersion.VERSION_1_8.toString()
    targetCompatibility = JavaVersion.VERSION_1_8.toString()
}

tasks.test {
    useJUnitPlatform()
}
