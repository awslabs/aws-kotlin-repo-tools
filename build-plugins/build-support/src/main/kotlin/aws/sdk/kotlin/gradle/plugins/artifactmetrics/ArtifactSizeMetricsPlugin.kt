/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.gradle.plugins.artifactmetrics

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

private const val TASK_GROUP = "Metrics"
internal const val OUTPUT_PATH = "reports/metrics/"

/**
 * Facilitates the collection and analysis of artifact size metrics via the `artifactSizeMetrics` and `analyzeArtifactSizeMetrics` gradle tasks.
 */
class ArtifactSizeMetricsPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        if (target != target.rootProject) {
            throw GradleException("${this::class.java} can only be applied to the root project")
        }

        target.extensions.create("artifactSizeMetrics", ArtifactSizeMetricsPluginConfig::class.java)

        val tasks = mutableListOf<TaskProvider<CollectArtifactSizeMetricsTask>>()
        target.subprojects { tasks.add(subprojectArtifactSizeMetricsTask()) }

        target.registerRootProjectArtifactSizeMetricsTask(tasks)
        target.tasks.register<AnalyzeArtifactSizeMetricsTask>("analyzeArtifactSizeMetrics") { group = TASK_GROUP }
    }
}

private fun Project.subprojectArtifactSizeMetricsTask(): TaskProvider<CollectArtifactSizeMetricsTask> =
    tasks.register<CollectArtifactSizeMetricsTask>("artifactSizeMetrics") {
        group = TASK_GROUP
        onlyIf { tasks.findByName("jvmJar") != null }
        dependsOn(tasks.withType<Jar>())
    }

private fun Project.registerRootProjectArtifactSizeMetricsTask(
    subProjects: List<TaskProvider<CollectArtifactSizeMetricsTask>>,
) {
    tasks.register("artifactSizeMetrics") {
        group = TASK_GROUP
        dependsOn(subProjects)
        val artifactSizeMetricsFile = layout.buildDirectory.file(OUTPUT_PATH + "artifact-size-metrics.csv")
        outputs.file(artifactSizeMetricsFile)

        doLast {
            val subProjectArtifactSizeMetrics = mutableListOf<String>()

            subProjects
                .map { it.get().metricsFile.asFile.get() }
                .filter { it.exists() && it.length() > 0 }
                .forEach { metricsFile ->
                    val metrics = metricsFile.readLines().toMutableList()
                    metrics.removeAt(0) // Remove header

                    metrics.forEach { metric ->
                        subProjectArtifactSizeMetrics.add(metric)
                    }
                }

            val projectArtifactSizeMetrics = buildString {
                val header = "Artifact, Size"
                appendLine(header)

                subProjectArtifactSizeMetrics.forEach { entry ->
                    appendLine(entry)
                }
            }

            artifactSizeMetricsFile.get().asFile.writeText(projectArtifactSizeMetrics)
        }
    }
}

open class ArtifactSizeMetricsPluginConfig {
    var artifactPrefixes: Set<String> = emptySet()
    var closurePrefixes: Set<String> = emptySet()
    var significantChangeThresholdPercentage: Int = 5
}
