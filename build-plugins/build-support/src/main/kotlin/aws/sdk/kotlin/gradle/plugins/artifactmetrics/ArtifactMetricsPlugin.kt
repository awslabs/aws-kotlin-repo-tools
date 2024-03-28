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
 * Facilitates the collection and analysis of artifact size metrics via the `artifactMetrics` and `analyzeArtifactMetrics` tasks.
 */
class ArtifactMetricsPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        if (target != target.rootProject) {
            throw GradleException("${this::class.java} can only be applied to the root project")
        }

        val tasks = mutableListOf<TaskProvider<CollectMetricsTask>>()
        target.subprojects {
            if (
                (target.rootProject.name == "aws-sdk-kotlin" && (path.startsWith(":services") || path.startsWith(":aws-runtime"))) ||
                (target.rootProject.name == "smithy-kotlin" && (path.startsWith(":codegen") || path.startsWith(":runtime"))) ||
                (target.rootProject.name == "aws-crt-kotlin-parent" && path.startsWith(":aws-crt-kotlin"))
            ) {
                logger.info("registering artifact metrics tasks for $name at $path")
                tasks.add(subprojectMetricsTask())
            }
        }

        target.registerRootProjectMetricsTask(tasks)

        target.tasks.register<AnalyzeMetricsTask>("analyzeArtifactMetrics") {
            group = TASK_GROUP
        }
    }
}

private fun Project.subprojectMetricsTask(): TaskProvider<CollectMetricsTask> =
    tasks.register<CollectMetricsTask>("artifactMetrics") {
        group = TASK_GROUP
        onlyIf { tasks.findByName("jvmJar") != null }
        dependsOn(tasks.withType<Jar>())
    }

private fun Project.registerRootProjectMetricsTask(
    subProjects: List<TaskProvider<CollectMetricsTask>>,
) {
    tasks.register("artifactMetrics") {
        group = TASK_GROUP
        dependsOn(subProjects)
        val combinedMetrics = layout.buildDirectory.file(OUTPUT_PATH + "artifactSizeMetrics.csv")
        outputs.file(combinedMetrics)

        doLast {
            val headers = mutableListOf<String>()
            val values = mutableListOf<String>()

            subProjects
                .map { it.get().metricsFile.asFile.get() }
                .filter { it.exists() && it.length() > 0 }
                .forEach { file ->
                    val lines = file.readLines()
                    headers.add(lines[0])
                    values.add(lines[1])
                }

            combinedMetrics.get().asFile
                .writeText("${headers.joinToString(separator = ",")}\n${values.joinToString(separator = ",")}")
        }
    }
}
