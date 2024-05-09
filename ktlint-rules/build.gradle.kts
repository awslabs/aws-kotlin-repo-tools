/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

description = "Lint rules for the AWS SDK for Kotlin"

plugins {
    `maven-publish`
    kotlin("jvm")
}

kotlin {
    sourceSets {
        main {
            dependencies {
                implementation(libs.ktlint.rule.engine.core)
                implementation(libs.ktlint.cli.ruleset.core)
            }
        }

        test {
            dependencies {
                implementation(libs.ktlint.test)
            }
        }
    }
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}

tasks.withType<JavaCompile> {
    sourceCompatibility = JavaVersion.VERSION_1_8.toString()
    targetCompatibility = JavaVersion.VERSION_1_8.toString()
}

publishing {
    publications {
        create<MavenPublication>("ktlintRules") {
            from(components["kotlin"])
        }
    }
}
