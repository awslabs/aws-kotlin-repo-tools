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
    runtimeOnly(project(":ktlint-rules"))
    implementation(libs.nexusPublishPlugin)
    compileOnly(gradleApi())
    implementation("aws.sdk.kotlin:s3:1.1.+")
    implementation("aws.sdk.kotlin:cloudwatch:1.1.+")
    testImplementation(libs.junit.jupiter)
}

gradlePlugin {
    plugins {
        create("artifact-size-metrics") {
            id = "aws.sdk.kotlin.gradle.artifactsizemetrics"
            implementationClass = "aws.sdk.kotlin.gradle.plugins.artifactsizemetrics.ArtifactSizeMetricsPlugin"
            version = "100.0"
        }
    }
}

publishing {
    repositories {
        mavenLocal()
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
