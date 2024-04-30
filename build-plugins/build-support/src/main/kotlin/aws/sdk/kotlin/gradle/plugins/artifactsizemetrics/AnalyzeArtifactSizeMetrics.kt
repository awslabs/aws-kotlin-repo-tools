/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.gradle.plugins.artifactsizemetrics

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
internal abstract class AnalyzeArtifactSizeMetrics : DefaultTask() {
    /**
     * File containing the project's current computed artifact size metrics.
     */
    @get:InputFile
    abstract val metricsFile: RegularFileProperty

    /**
     * File containing the results of analyzing the artifact size metrics.
     */
    @get:OutputFile
    abstract val analysisFile: RegularFileProperty

    /**
     * File containing either "true" or "false".
     */
    @get:OutputFile
    abstract val hasSignificantChangeFile: RegularFileProperty

    init {
        metricsFile.convention(project.layout.buildDirectory.file(OUTPUT_PATH + "artifact-size-metrics.csv"))
        analysisFile.convention(project.layout.buildDirectory.file(OUTPUT_PATH + "artifact-analysis.md"))
        hasSignificantChangeFile.convention(project.layout.buildDirectory.file(OUTPUT_PATH + "has-significant-change.txt"))
    }

    private val pluginConfig = project.rootProject.extensions.getByType(ArtifactSizeMetricsPluginConfig::class.java)

    @TaskAction
    fun analyze() {
        val latestReleaseMetricsFile =
            project.layout.buildDirectory.file(OUTPUT_PATH + "latest-release-artifact-size-metrics.csv").get().asFile
        writeLatestReleaseMetrics(latestReleaseMetricsFile)

        val latestReleaseMetrics = latestReleaseMetricsFile.toMap()
        val currentMetrics = metricsFile.get().asFile.toMap()
        val analysis = analyzeArtifactSizeMetrics(latestReleaseMetrics, currentMetrics)

        hasSignificantChangeFile.get().asFile.writeText(analysis.significantChange.toString())
        val diffTables = createDiffTables(analysis)
        val output = if (analysis.hasDelta) diffTables else noDiffMessage

        this.analysisFile.get().asFile.writeText(output)
    }

    private fun writeLatestReleaseMetrics(file: File) = runBlocking {
        S3Client.fromEnvironment().use { s3 ->
            s3.getObject(
                GetObjectRequest {
                    bucket = S3_ARTIFACT_SIZE_METRICS_BUCKET
                    key = "${pluginConfig.projectRepositoryName}-latest-release.csv"
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
    ): ArtifactSizeMetricsAnalysis {
        val artifactNames = releaseMetrics.keys + currentMetrics.keys
        val artifactSizeMetrics = artifactNames.associateWith { artifact ->
            val current = currentMetrics[artifact] ?: 0
            val release = releaseMetrics[artifact] ?: 0

            val delta = current - release
            val percentage = if (current == 0L || release == 0L) Double.NaN else delta.toDouble() / release.toDouble() * 100

            ArtifactSizeMetric(
                current,
                release,
                delta,
                percentage,
            )
        }

        val changeHappened = artifactSizeMetrics.values.any { it.delta.isNotaFluctuation() }
        val significantChange = artifactSizeMetrics.values.any {
            (it.percentage > pluginConfig.significantChangeThresholdPercentage) || // Increase in size above threshold
                (it.latestReleaseSize == 0L) // New artifact
        }

        return ArtifactSizeMetricsAnalysis(artifactSizeMetrics, significantChange, changeHappened)
    }

    // There are small fluctuations in artifact size that are not real delta
    private fun Long.isNotaFluctuation() = abs(this) > 5L

    private data class ArtifactSizeMetricsAnalysis(
        val metrics: Map<String, ArtifactSizeMetric>,
        val significantChange: Boolean,
        val hasDelta: Boolean,
    )

    private fun createDiffTables(analysis: ArtifactSizeMetricsAnalysis): String = buildString {
        appendLine("Affected Artifacts\n=")

        val requiresAttention = StringBuilder()
            .appendLine("Significantly increased in size")
            .appendLine()
            .append(tableHeader)
        var artifactRequiresAttention = false

        val ordinary = StringBuilder()
            .appendLine("<details>") // See: https://tiny.amazon.com/zhuf9fhk/docsgithengetswritworkorga
            .appendLine("<summary>Changed in size</summary>")
            .appendLine()
            .append(tableHeader)
        var artifactOrdinaryChange = false

        analysis.metrics
            .toList()
            .sortedBy { it.second.percentage }.toMap()
            .forEach { metric ->
                if (metric.value.delta.isNotaFluctuation()) {
                    val row = buildString {
                        append("|")
                        append(metric.key)
                        append("|")
                        if (metric.value.currentSize == 0L) append("(does not exist)") else append("%,d".format(metric.value.currentSize))
                        append("|")
                        if (metric.value.latestReleaseSize == 0L) append("(does not exist)") else append("%,d".format(metric.value.latestReleaseSize))
                        append("|")
                        append("%,d".format(metric.value.delta))
                        append("|")
                        append("%.2f".format(metric.value.percentage))
                        append("%")
                        if (metric.value.requiresAttention()) append("⚠️")
                        appendLine("|")
                    }

                    if (metric.value.requiresAttention()) {
                        requiresAttention.append(row)
                        artifactRequiresAttention = true
                    } else {
                        ordinary.append(row)
                        artifactOrdinaryChange = true
                    }
                }
            }

        ordinary
            .appendLine()
            .append("</details>")

        if (artifactRequiresAttention) appendLine(requiresAttention)
        if (artifactOrdinaryChange) appendLine(ordinary)
    }

    private fun ArtifactSizeMetric.requiresAttention() =
        this.percentage > pluginConfig.significantChangeThresholdPercentage || this.percentage.isNaN()

    private data class ArtifactSizeMetric(
        val currentSize: Long,
        val latestReleaseSize: Long,
        val delta: Long,
        val percentage: Double,
    )

    private fun File.toMap(): Map<String, Long> {
        val metrics = this
            .readLines()
            .drop(1) // Ignoring header
            .map { metricLine ->
                metricLine.split(",").map { it.trim() } // e.g. ["S3-jvm.jar", "103948"]
            }

        return metrics.associate { metric ->
            metric[0] to metric[1].toLong()
        }
    }

    private val noDiffMessage = """
        Affected Artifacts
        =
        No artifacts changed size
    """.trimIndent()

    private val tableHeader = buildString {
        appendLine("| Artifact |Pull Request (bytes) | Latest Release (bytes) | Delta (bytes) | Delta (percentage) |")
        appendLine("| -------- | ------------------: | ---------------------: | ------------: | -----------------: |")
    }
}
