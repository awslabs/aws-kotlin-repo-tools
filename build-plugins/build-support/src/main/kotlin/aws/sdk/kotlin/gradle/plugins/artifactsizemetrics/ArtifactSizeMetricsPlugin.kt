/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.gradle.plugins.artifactsizemetrics

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

private const val TASK_GROUP = "Verification"
internal const val OUTPUT_PATH = "reports/metrics/"
internal const val S3_ARTIFACT_SIZE_METRICS_BUCKET = "" // TODO: FILL IN BUCKET

/**
 * Facilitates the collection and analysis of artifact size metrics via the `artifactSizeMetrics` and `analyzeArtifactSizeMetrics` gradle tasks.
 * Includes additional tasks for CI to run.
 */
class ArtifactSizeMetricsPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        if (target != target.rootProject) {
            throw GradleException("${this::class.java} can only be applied to the root project")
        }

        target.extensions.create("artifactSizeMetrics", ArtifactSizeMetricsPluginConfig::class.java)

        val tasks = mutableListOf<TaskProvider<CollectArtifactSizeMetrics>>()
        target.subprojects { tasks.add(subprojectArtifactSizeMetricsTask()) }

        target.registerRootProjectArtifactSizeMetricsTask(tasks)

        target.tasks.register<CollectDelegatedArtifactSizeMetrics>("collectDelegatedArtifactSizeMetrics") { group = TASK_GROUP }
        target.tasks.register<AnalyzeArtifactSizeMetrics>("analyzeArtifactSizeMetrics") { group = TASK_GROUP }
        target.tasks.register<PutArtifactSizeMetricsInCloudWatch>("putArtifactSizeMetricsInCloudWatch") { group = TASK_GROUP }
    }
}

private fun Project.subprojectArtifactSizeMetricsTask(): TaskProvider<CollectArtifactSizeMetrics> =
    tasks.register<CollectArtifactSizeMetrics>("artifactSizeMetrics") {
        group = TASK_GROUP
        onlyIf { tasks.findByName("jvmJar") != null }
        dependsOn(tasks.withType<Jar>())
    }

private fun Project.registerRootProjectArtifactSizeMetricsTask(
    subProjects: List<TaskProvider<CollectArtifactSizeMetrics>>,
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
                    metricsFile
                        .readLines()
                        .drop(1) // Remove header
                        .forEach { metric ->
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
    var significantChangeThresholdPercentage: Double = 5.0
    var projectRepositoryName: String = ""
}
