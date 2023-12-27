/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
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
    // make our custom lint rules available to the buildscript classpath
    implementation(libs.smithy.model)
    implementation(libs.smithy.gradle.base.plugin)
    testImplementation(libs.junit.jupiter)
}

group = "aws.sdk.kotlin"

gradlePlugin {
    plugins {
        val awsKotlinSmithyBuildPlugin by creating {
            id = "aws.sdk.kotlin.gradle.smithybuild"
            implementationClass = "aws.sdk.kotlin.gradle.codegen.SmithyBuildPlugin"
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
