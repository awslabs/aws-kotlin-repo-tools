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
    implementation(kotlin("gradle-plugin", "1.8.22"))
}

group = "aws.sdk.kotlin"

val ktlint = configurations.create("ktlint") {
    attributes {
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
    }
}

dependencies {
    ktlint(libs.ktlint)
    ktlint(project(":ktlint-rules"))
}

gradlePlugin {
    plugins {
        val awsKotlinRepoToolsPlugin by creating {
            id = "aws.sdk.kotlin.kmp"
            implementationClass = "aws.sdk.kotlin.gradle.kmp.KmpDefaultsPlugin"
        }

        // create("buildDefaultsPlugin") {
        //     id = "aws.sdk.kotlin.build-defaults"
        //     implementationClass = "aws.sdk.kotlin.gradle.BuildDefaultsPlugin"
        // }
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
