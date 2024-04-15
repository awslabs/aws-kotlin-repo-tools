/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.gradle.plugins.artifactsizemetrics

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.getByName

/**
 * Gradle task that collects artifact size metrics for configured projects and closures.
 * Outputs the results to a CSV file.
 */
internal abstract class CollectArtifactSizeMetricsTask : DefaultTask() {
    /**
     * The file where the artifact size metrics will be stored, defaults to /build/reports/metrics/artifact-size-metrics.csv
     */
    @get:OutputFile
    abstract val metricsFile: RegularFileProperty

    init {
        metricsFile.convention(project.layout.buildDirectory.file(OUTPUT_PATH + "artifact-size-metrics.csv"))
    }

    @TaskAction
    fun collect() {
        val pluginConfig = this.project.rootProject.extensions.getByType(ArtifactSizeMetricsPluginConfig::class.java)
        if (pluginConfig.artifactPrefixes.none { project.path.startsWith(it) }) return

        // TODO: Start collecting metrics for KMP artifacts
        val jvmJarTask = project.tasks.getByName<Jar>("jvmJar")
        val jarSize = jvmJarTask.archiveFile.get().asFile.length()
        val artifact = buildString {
            append(jvmJarTask.archiveBaseName.get())
            append("-")
            append(jvmJarTask.archiveAppendix.orNull ?: "unknown")
            append(".")
            append(jvmJarTask.archiveExtension.get())
        }

        var closureSize: Long? = null
        if (pluginConfig.closurePrefixes.any { project.path.startsWith(it) }) {
            closureSize = jarSize + project.configurations.getByName("jvmRuntimeClasspath").sumOf { it.length() }
        }

        val metrics = buildString {
            val header = "Artifact, Size"
            appendLine(header)

            append(artifact)
            append(",")
            appendLine(jarSize)

            if (closureSize != null) {
                append("$artifact closure")
                append(",")
                appendLine(closureSize)
            }
        }

        metricsFile.asFile.get().writeText(metrics)
    }
}
