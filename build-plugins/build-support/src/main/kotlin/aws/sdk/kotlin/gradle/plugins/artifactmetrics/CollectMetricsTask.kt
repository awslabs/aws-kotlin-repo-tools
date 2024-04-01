/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.gradle.plugins.artifactmetrics

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.getByName

internal abstract class CollectMetricsTask : DefaultTask() {
    @get:OutputFile
    abstract val metricsFile: RegularFileProperty

    init {
        metricsFile.convention(project.layout.buildDirectory.file(OUTPUT_PATH + "artifactSizeMetrics.csv"))
    }

    @TaskAction
    fun generateMetrics() {
        val pluginConfig = this.project.rootProject.extensions.getByType(ArtifactMetricsConfig::class.java)
        if (!project.path.startsWith(pluginConfig.artifactPrefixes)) return

        val jvmJarTask = project.tasks.getByName<Jar>("jvmJar")
        val jarSize = jvmJarTask.archiveFile.get().asFile.length()
        val artifactName = buildString {
            append(jvmJarTask.archiveBaseName.get())
            append("-")
            append(jvmJarTask.archiveAppendix.orNull ?: "unknown")
            append(".")
            append(jvmJarTask.archiveExtension.get())
        }

        var closureSize: Long? = null

        if (project.path.startsWith(pluginConfig.closurePrefixes)) {
            closureSize = jarSize + project.configurations.getByName("jvmRuntimeClasspath").sumOf { it.length() }
        }

        val headers = buildString {
            append(artifactName)
            if (closureSize != null) append(", $artifactName closure")
        }

        val values = buildString {
            append(jarSize)
            if (closureSize != null) append(", $closureSize")
        }

        val metrics = "$headers\n$values"

        metricsFile.asFile.get().writeText(metrics)
    }
}

private fun String.startsWith(prefixes: Set<String>): Boolean {
    prefixes.forEach { prefix ->
        if (this.startsWith(prefix)) return true
    }
    return false
}
