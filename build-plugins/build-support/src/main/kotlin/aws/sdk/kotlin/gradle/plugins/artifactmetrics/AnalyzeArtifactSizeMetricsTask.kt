/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.gradle.plugins.artifactmetrics

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.smithy.kotlin.runtime.content.decodeToString
import aws.smithy.kotlin.runtime.io.use
import kotlinx.coroutines.runBlocking
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import kotlin.math.abs

/**
 * Gradle task that analyzes/compares a project's local artifact size metrics to
 * ones from a project's latest GitHub release. Outputs the results into various files.
 */
internal abstract class AnalyzeArtifactSizeMetricsTask : DefaultTask() {
    /**
     * The project's current computed artifact size metrics.
     */
    @get:InputFile
    abstract val metricsFile: RegularFileProperty

    /**
     * The results of analyzing the artifact size metrics.
     */
    @get:OutputFile
    abstract val analysis: RegularFileProperty

    /**
     * File containing either "true" or "false".
     */
    @get:OutputFile
    abstract val hasSignificantChange: RegularFileProperty

    init {
        metricsFile.convention(project.layout.buildDirectory.file(OUTPUT_PATH + "artifact-size-metrics.csv"))
        analysis.convention(project.layout.buildDirectory.file(OUTPUT_PATH + "artifact-analysis.md"))
        hasSignificantChange.convention(project.layout.buildDirectory.file(OUTPUT_PATH + "has-significant-change.txt"))
    }

    @TaskAction
    fun analyze() {
        val latestReleaseMetricsFile =
            project.layout.buildDirectory.file(OUTPUT_PATH + "latest-release-metrics.csv").get().asFile
        writeLatestReleaseMetrics(latestReleaseMetricsFile)

        val latestReleaseMetrics = latestReleaseMetricsFile.toMap()
        val currentMetrics = metricsFile.get().asFile.toMap()
        val analysis = analyzeArtifactSizeMetrics(latestReleaseMetrics, currentMetrics)

        hasSignificantChange.get().asFile.writeText(analysis.significantChange.toString())
        val diffTable = createDiffTable(analysis)
        val output = if (analysis.delta) diffTable else noDiffMessage

        this.analysis.get().asFile.writeText(output)
    }

    private fun writeLatestReleaseMetrics(file: File) = runBlocking {
        S3Client.fromEnvironment().use { s3 ->
            s3.getObject(
                GetObjectRequest {
                    bucket = "artifact-size-metrics-aws-sdk-kotlin" // TODO: Point to artifact size metrics bucket
                    key = "artifact-size-metrics.csv" // TODO: Point to artifact size metrics for latest release
                },
            ) { latestReleaseMetrics ->
                file.writeText(
                    latestReleaseMetrics.body?.decodeToString() ?: throw GradleException("Metrics from latest release are empty"),
                )
            }
        }
    }

    private fun analyzeArtifactSizeMetrics(
        releaseMetrics: Map<String, Long>,
        currentMetrics: Map<String, Long>,
    ): ArtifactAnalysis {
        val pluginConfig = this.project.rootProject.extensions.getByType(ArtifactSizeMetricsPluginConfig::class.java)

        val artifactNames = releaseMetrics.keys + currentMetrics.keys
        val artifactSizeMetrics = mutableMapOf<String, ArtifactSizeMetric>()

        var significantChange = false
        var changeHappened = false

        artifactNames.forEach { artifact ->
            val current = currentMetrics[artifact] ?: 0
            val release = releaseMetrics[artifact] ?: 0

            val delta = current - release
            val percentage = if (current == 0L || release == 0L) Double.NaN else delta.toDouble() / release.toDouble() * 100

            if (delta != 0L) changeHappened = true
            if (abs(percentage) > pluginConfig.significantChangeThresholdPercentage && delta > 0L) significantChange = true

            artifactSizeMetrics[artifact] =
                ArtifactSizeMetric(
                    current,
                    release,
                    delta,
                    percentage,
                )
        }

        return ArtifactAnalysis(artifactSizeMetrics, significantChange, changeHappened)
    }

    private data class ArtifactAnalysis(
        val metrics: Map<String, ArtifactSizeMetric>,
        val significantChange: Boolean,
        val delta: Boolean,
    )

    private fun createDiffTable(analysis: ArtifactAnalysis): String = buildString {
        appendLine("Affected Artifacts\n=")
        appendLine("| Artifact |Pull Request (bytes) | Latest Release (bytes) | Delta (bytes) | Delta (percentage) |")
        appendLine("| -------- | ------------------: | ---------------------: | ------------: | -----------------: |")
        analysis.metrics.forEach { metric ->
            if (metric.value.delta != 0L) {
                append("|")
                append(metric.key)
                append("|")
                append("%,d".format(metric.value.currentSize))
                append("|")
                append("%,d".format(metric.value.latestReleaseSize))
                append("|")
                append("%,d".format(metric.value.delta))
                append("|")
                append("%.2f".format(metric.value.percentage))
                append("%")
                appendLine("|")
            }
        }
    }

    private data class ArtifactSizeMetric(
        val currentSize: Long,
        val latestReleaseSize: Long,
        val delta: Long,
        val percentage: Double,
    )

    private fun File.toMap(): Map<String, Long> {
        val lines = this.readLines()
        val metrics = mutableMapOf<String, Long>()

        lines.forEachIndexed { index, line ->
            if (index > 0) { // Skipping header row
                val metric = line.split(",").map { it.trim() } // e.g. ["S3-jvm.jar", "103948"]

                val artifact = metric[0]
                val size = metric[1].toLong()

                metrics[artifact] = size
            }
        }

        return metrics
    }

    private val noDiffMessage = buildString {
        appendLine("Affected Artifacts")
        appendLine("=")
        appendLine("No artifacts changed size")
    }
}
