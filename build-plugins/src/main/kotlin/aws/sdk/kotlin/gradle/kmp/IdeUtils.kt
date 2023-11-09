/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.gradle.kmp

import aws.sdk.kotlin.gradle.util.prop
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget

/**
 * Whether Intellij is active or not
 */
val IDEA_ACTIVE = System.getProperty("idea.active") == "true"

val OS_NAME = System.getProperty("os.name").toLowerCase()

val HOST_NAME = when {
    OS_NAME.startsWith("linux") -> "linux"
    OS_NAME.startsWith("windows") -> "windows"
    OS_NAME.startsWith("mac") -> "macos"
    else -> error("Unknown os name `$OS_NAME`")
}

val Project.JVM_ENABLED get() = prop("aws.kotlin.jvm")?.let { it == "true" } ?: true
val Project.NATIVE_ENABLED get() = prop("aws.kotlin.native")?.let { it == "true" } ?: true

/**
 * Scope down the native target enabled when working in intellij
 */
val KotlinMultiplatformExtension.ideaTarget: KotlinNativeTarget
    get() = when (HostManager.host) {
        is KonanTarget.LINUX_X64 -> linuxX64()
        is KonanTarget.LINUX_ARM64 -> linuxArm64()
        is KonanTarget.MACOS_X64 -> macosX64()
        is KonanTarget.MACOS_ARM64 -> macosArm64()
        is KonanTarget.MINGW_X64 -> mingwX64()
        else -> error("Unsupported target ${HostManager.host}")
    }
