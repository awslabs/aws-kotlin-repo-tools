/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
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
include(":codegen-plugin")
include(":ktlint-rules")
