/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

description = "Lint rules for the AWS SDK for Kotlin"

plugins {
    kotlin("jvm")
}

kotlin {
    sourceSets {
        val main by getting {
            dependencies {
                implementation(libs.ktlint.core)
            }
        }

        val test by getting {
            dependencies {
                implementation(libs.ktlint.test)
            }
        }
    }
}
