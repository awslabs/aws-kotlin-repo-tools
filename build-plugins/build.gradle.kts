/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    alias(libs.plugins.plugin.publish)
}

repositories {
    gradlePluginPortal()
}

dependencies {
    compileOnly(kotlin("gradle-plugin", "1.9.20"))
    // make our custom lint rules available to the buildscript classpath
    runtimeOnly(project(":ktlint-rules"))
    implementation(libs.nexusPublishPlugin)
    implementation(libs.smithy.model)
    implementation(libs.smithy.gradle.base.plugin)
    testImplementation(libs.junit.jupiter)
}

gradlePlugin {
    plugins {
        val awsKotlinRepoToolsPlugin by creating {
            id = "aws.sdk.kotlin.gradle.kmp"
            implementationClass = "aws.sdk.kotlin.gradle.kmp.KmpDefaultsPlugin"
            description = "Kotlin Multiplatform defaults and build settings for AWS Kotlin repositories"
        }

        val awsKotlinSmithyBuildPlugin by creating {
            id = "aws.sdk.kotlin.gradle.smithybuild"
            implementationClass = "aws.sdk.kotlin.gradle.codegen.SmithyBuildPlugin"
            description = "A plugin that wraps smithy gradle base plugin and provides a DSL for generating smithy-build.json dynamically"
        }
    }
}

tasks.withType<KotlinCompile> {
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
