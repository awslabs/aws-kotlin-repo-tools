/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    repositories {
        mavenCentral()
    }
}

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

repositories {
    gradlePluginPortal()
}

dependencies {
    implementation(gradleApi())
    implementation(kotlin("gradle-plugin", "1.9.10"))
    // make our custom lint rules available to the buildscript classpath
    runtimeOnly(project(":ktlint-rules"))
    implementation(libs.nexusPublishPlugin)
}

group = "aws.sdk.kotlin"

gradlePlugin {
    plugins {
        val awsKotlinRepoToolsPlugin by creating {
            id = "aws.sdk.kotlin.kmp"
            implementationClass = "aws.sdk.kotlin.gradle.kmp.KmpDefaultsPlugin"
        }
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs += listOf("-Xopt-in=kotlin.RequiresOptIn")
    }
}

tasks.withType<JavaCompile> {
    sourceCompatibility = JavaVersion.VERSION_1_8.toString()
    targetCompatibility = JavaVersion.VERSION_1_8.toString()
}
