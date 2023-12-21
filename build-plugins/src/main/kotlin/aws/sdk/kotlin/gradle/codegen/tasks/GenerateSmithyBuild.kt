/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.gradle.codegen.tasks

import aws.sdk.kotlin.gradle.codegen.dsl.SmithyProjection
import aws.sdk.kotlin.gradle.codegen.dsl.withObjectMember
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*
import software.amazon.smithy.model.node.Node

private const val SMITHY_BUILD_CONFIG_FILENAME = "smithy-build.json"

/**
 * Task that generates `smithy-build.json` from a set of projections
 */
abstract class GenerateSmithyBuild : DefaultTask() {

    /**
     * The list of projections to generate
     */
    @get:Input
    public abstract val projections: ListProperty<SmithyProjection>

    /**
     * The output directory for the generated `smithy-build.json` configuration file.
     * Defaults to the project build directory.
     */
    @OutputDirectory
    @Optional
    public val outputDir: DirectoryProperty = project.layout.buildDirectory

    @get:OutputFile
    public val smithyBuildConfig: Provider<RegularFile>
        get() = outputDir.file(SMITHY_BUILD_CONFIG_FILENAME)

    /**
     * Generate `smithy-build.json`
     */
    @TaskAction
    fun generateSmithyBuild() {
        val buildConfig = smithyBuildConfig.get().asFile
        if (buildConfig.exists()) {
            buildConfig.delete()
        }

        buildConfig.parentFile.mkdirs()
        val contents = projections.get().let(::generateSmithyBuild)
        buildConfig.writeText(contents)
    }
}

private fun generateSmithyBuild(projections: Collection<SmithyProjection>): String {
    val buildConfig = Node.objectNodeBuilder()
        .withMember("version", "1.0")
        .withObjectMember("projections") {
            projections.forEach { projection ->
                withMember(projection.name, projection.toNode())
            }
        }
        .build()

    return Node.prettyPrintJson(buildConfig)
}
