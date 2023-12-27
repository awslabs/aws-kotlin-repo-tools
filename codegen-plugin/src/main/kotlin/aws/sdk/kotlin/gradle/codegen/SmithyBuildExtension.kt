/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.gradle.codegen

import aws.sdk.kotlin.gradle.codegen.dsl.SmithyProjection
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import software.amazon.smithy.gradle.SmithyUtils
import java.nio.file.Path

/**
 * Register and build Smithy projections
 */
open class SmithyBuildExtension(private val project: Project) {

    val projections = project.objects.domainObjectContainer(SmithyProjection::class.java) { name ->
        SmithyProjection(name)
    }

    /**
     * Get the output projection path for the given projection and plugin name
     *
     * @param projectionName the name of the projection to get the output path for
     * @param pluginName the name of the plugin to get the output path for
     */
    public fun getProjectionPath(projectionName: String, pluginName: String): Provider<Path> =
        SmithyUtils.getProjectionOutputDirProperty(project).map {
            // FIXME - bug in smithy gradle base? it expects the input file to pass "isDirectory"
            // but that flag is only true IFF the path _exists_ AND is a directory
            // should probably check if file exists before checking if it's a directory
            it.asFile.mkdirs()
            SmithyUtils.getProjectionPluginPath(it.asFile, projectionName, pluginName)
        }
}

// smithy-kotlin specific extensions

/**
 * Get the projection path for the given projection name for the `smithy-kotlin` plugin.
 * This is equivalent to `smithyBuild.getProjectionPath(projectionName, "kotlin-codegen")
 *
 * @param projectionName the name of the projection to use
 */
public fun SmithyBuildExtension.smithyKotlinProjectionPath(projectionName: String): Provider<Path> =
    getProjectionPath(projectionName, "kotlin-codegen")

/**
 * Get the default generated kotlin source directory for the `smithy-kotlin` plugin.
 * This is equivalent to `smithyBuild.getProjectionPath(projectionName, "kotlin-codegen")
 *
 * @param projectionName the name of the projection to use
 */
public fun SmithyBuildExtension.smithyKotlinProjectionSrcDir(projectionName: String): Provider<Path> =
    smithyKotlinProjectionPath(projectionName).map { it.resolve("src/main/kotlin") }
