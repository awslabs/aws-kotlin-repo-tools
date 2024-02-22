/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
    `kotlin-dsl`
    kotlin("jvm")
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("com.google.devtools.ksp:symbol-processing-api:1.9.21-1.0.16")
    implementation("com.google.devtools.ksp:symbol-processing-gradle-plugin:1.9.21-1.0.16")
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin-api:1.9.21")
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.21")

    compileOnly(gradleApi())
    testImplementation(libs.junit.jupiter)
}

gradlePlugin {
    plugins {
        val awsKotlinApiScanPlugin by creating {
            id = "aws.sdk.kotlin.apiscan"
            implementationClass = "aws.sdk.kotlin.apiscan.ApiScanPlugin"
            description = "A plugin that scans your APIs"
        }
    }
}
