/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.apiscan

import com.google.devtools.ksp.gradle.KspGradleSubplugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.add
import org.gradle.kotlin.dsl.get
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinSingleTargetExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePluginWrapper
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget

class ApiScanPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.allprojects {
            pluginManager.apply(KspGradleSubplugin::class.java)
            plugins.withType(KotlinBasePluginWrapper::class.java).configureEach {
                decorate(project.extensions.getByName("kotlin") as KotlinProjectExtension)
            }
        }
    }

    private fun decorate(kotlin: KotlinProjectExtension) {
        when (kotlin) {
            is KotlinSingleTargetExtension<*> -> decorate(kotlin.target)
            is KotlinMultiplatformExtension -> kotlin.targets.configureEach(::decorate)
        }
    }

    private fun decorate(target: KotlinTarget) {
        val sourceSet = target.compilations["main"]
        sourceSet.dependencies {
            val kspConfigName =
                if (target.name == "") {
                    "ksp" // Single target (i.e., non-KMP) mode
                } else {
                    when (target.platformType) {
                        KotlinPlatformType.common -> "kspCommonMainMetadata"
                        KotlinPlatformType.jvm -> "kspJvm"
                        KotlinPlatformType.native -> "kspNative"
                        else -> return@dependencies
                    }
                }

            project.dependencies.add(kspConfigName, "aws.sdk.kotlin.gradle:api-scan:0.4.1") {
                
            }
        }
    }
}
