/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.gradle.kmp

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Implements shared configuration logic for Kotlin Multiplatform Projects (KMP).
 */
class KmpDefaultsPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            logger.info("applying kmp defaults plugin to $target")
            project.configureKmpTargets()
        }
    }
}
