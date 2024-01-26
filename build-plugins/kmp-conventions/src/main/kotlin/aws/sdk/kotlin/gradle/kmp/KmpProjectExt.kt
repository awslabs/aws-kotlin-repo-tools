/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.gradle.kmp

import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.the
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Allows configuration from parent projects subprojects/allprojects block when they haven't configured the KMP
 * plugin but the subproject has applied it. The extension is otherwise not visible.
 */
fun Project.kotlin(block: KotlinMultiplatformExtension.() -> Unit) {
    configure(block)
}
val Project.kotlin: KotlinMultiplatformExtension get() = the()
