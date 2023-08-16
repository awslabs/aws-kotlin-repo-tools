/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.gradle.kmp

import aws.sdk.kotlin.gradle.util.verifyRootProject
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Implements shared configuration logic for Kotlin Multiplatform Projects (KMP).
 *
 * NOTE: This plugin is meant to be applied at top level and will configure subprojects directly if/when
 * they apply the multiplatform plugin.
 */
class KmpDefaultsPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            logger.info("applying kmp defaults plugin to $target")
            verifyRootProject { "AWS SDK KmpDefaultsPlugin requires installation into root project" }
            subprojects {
                val subproject = this
                subproject.configureKmpTargets()
            }
        }
    }
}
