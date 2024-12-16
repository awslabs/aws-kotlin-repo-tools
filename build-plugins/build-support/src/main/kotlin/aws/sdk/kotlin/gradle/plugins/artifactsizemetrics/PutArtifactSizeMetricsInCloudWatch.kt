/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.gradle.plugins.artifactsizemetrics

import aws.sdk.kotlin.services.cloudwatch.CloudWatchClient
import aws.sdk.kotlin.services.cloudwatch.model.Dimension
import aws.sdk.kotlin.services.cloudwatch.model.MetricDatum
import aws.sdk.kotlin.services.cloudwatch.model.StandardUnit
import aws.sdk.kotlin.services.cloudwatch.putMetricData
import aws.smithy.kotlin.runtime.time.Instant
import kotlinx.coroutines.runBlocking
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction

/**
 * Puts a projects local artifact size metrics in CloudWatch.
 * This task should typically be run after gathering metrics for a release in our CI
 */
internal abstract class PutArtifactSizeMetricsInCloudWatch : DefaultTask() {
    /**
     * File containing the project's current computed artifact size metrics.
     */
    @get:InputFile
    abstract val metricsFile: RegularFileProperty

    init {
        metricsFile.convention(project.layout.buildDirectory.file(OUTPUT_PATH + "artifact-size-metrics.csv"))
    }

    @TaskAction
    fun put() {
        val currentTime = Instant.now()
        val pluginConfig = project.rootProject.extensions.getByType(ArtifactSizeMetricsPluginConfig::class.java)

        val metrics = metricsFile
            .get()
            .asFile
            .readLines()
            .drop(1) // Ignoring header
            .map { metric ->
                val split = metric.split(",").map { it.trim() }
                val artifactName = split[0]
                val artifactSize = split[1].toDouble() // CloudWatch's requires metric values to be double

                MetricDatum {
                    metricName = "${pluginConfig.projectRepositoryName}-$artifactName"
                    timestamp = currentTime
                    unit = StandardUnit.Bytes
                    value = artifactSize
                    dimensions = listOf(
                        Dimension {
                            name = "Project"
                            value = pluginConfig.projectRepositoryName
                        },
                    )
                }
            }

        runBlocking {
            CloudWatchClient.fromEnvironment().use { cloudWatch ->
                metrics
                    .chunked(1000) // CloudWatch allows up to 1000 metrics at a time
                    .forEach { chunk ->
                        cloudWatch.putMetricData {
                            namespace = "Artifact Size Metrics"
                            metricData = chunk
                        }
                    }
            }
        }
    }
}
