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

internal abstract class AnalyzeMetricsTask : DefaultTask() {
    @get:InputFile
    abstract val metricsFile: RegularFileProperty

    @get:OutputFile
    abstract val summary: RegularFileProperty

    @get:OutputFile
    abstract val shouldFailWorkflow: RegularFileProperty

    init {
        metricsFile.convention(project.layout.buildDirectory.file(OUTPUT_PATH + "artifactSizeMetrics.csv"))
        summary.convention(project.layout.buildDirectory.file(OUTPUT_PATH + "artifact-summary.md"))
        shouldFailWorkflow.convention(project.layout.buildDirectory.file(OUTPUT_PATH + "shouldFailWorkflow.txt"))
    }

    @TaskAction
    fun analyze() {
        val releaseMetricsFile =
            project.layout.buildDirectory.file(OUTPUT_PATH + "latestReleaseMetrics.csv").get().asFile
        writeReleaseMetrics(releaseMetricsFile)

        val releaseMetrics = releaseMetricsFile.toMap()
        val currentMetrics = metricsFile.get().asFile.toMap()
        val artifactNames = getArtifactNames(releaseMetricsFile)
        val analysis = analyzeMetrics(artifactNames, releaseMetrics, currentMetrics)

        shouldFailWorkflow.get().asFile.writeText(analysis.significantChange.toString())
        val diffTable = createDiffTable(analysis)
        val output = if (analysis.delta) diffTable else noDiffMessage

        summary.get().asFile.writeText(output)
    }

    private fun writeReleaseMetrics(releaseMetricsFile: File) = runBlocking {
        S3Client.fromEnvironment().use { s3 ->
            s3.getObject(
                GetObjectRequest {
                    bucket = "TODO"
                    key = "TODO"
                },
            ) { latestReleaseMetrics ->
                releaseMetricsFile.writeText(
                    latestReleaseMetrics.body?.decodeToString() ?: throw GradleException("Metrics from latest release are empty"),
                )
            }
        }
    }

    private fun analyzeMetrics(
        artifactNames: Set<String>,
        releaseMetrics: Map<String, Long>,
        currentMetrics: Map<String, Long>,
    ): Analysis {
        val metrics = mutableMapOf<String, Metric>()
        var addedArtifact = false
        var removedArtifact = false
        var significantChange = false
        var changeHappened = false

        artifactNames.forEach { artifact ->
            val current = currentMetrics[artifact] ?: 0
            val release = releaseMetrics[artifact] ?: 0

            val delta = current - release
            val percentage = if (current == 0L || release == 0L) Double.NaN else delta.toDouble() / release.toDouble() * 100

            if (delta != 0L) changeHappened = true
            if (abs(percentage) > 5 && delta > 0L) significantChange = true
            if (release == 0L) addedArtifact = true
            if (current == 0L) removedArtifact = true

            metrics[artifact] =
                Metric(
                    current,
                    release,
                    delta,
                    percentage,
                )
        }

        return Analysis(metrics, addedArtifact, removedArtifact, significantChange, changeHappened)
    }

    private data class Analysis(
        val metrics: Map<String, Metric>,
        val addedArtifact: Boolean,
        val removedArtifact: Boolean,
        val significantChange: Boolean,
        val delta: Boolean,
    )

    private fun getArtifactNames(releaseMetricsFile: File): Set<String> {
        val releaseLines = releaseMetricsFile.readLines()
        val releaseHeaders = releaseLines[0].split(",").map { it.trim() }

        val currentLines = metricsFile.get().asFile.readLines()
        val currentHeaders = currentLines[0].split(",").map { it.trim() }

        return releaseHeaders.toSet() + currentHeaders.toSet()
    }

    private fun createDiffTable(analysis: Analysis): String = buildString {
        appendLine("Affected Artifacts\n=")
        appendLine("| Artifact |Pull Request (bytes) | Latest Release (bytes) | Delta (bytes) | Delta (percentage) |")
        appendLine("| -------- | ------------------: | ---------------------: | ------------: | -----------------: |")
        analysis.metrics.forEach { metric ->
            if (metric.value.delta != 0L) {
                append('|')
                append(metric.key)
                append('|')
                append("%,d".format(metric.value.currentSize))
                append('|')
                append("%,d".format(metric.value.latestReleaseSize))
                append('|')
                append("%,d".format(metric.value.delta))
                append('|')
                append("%.2f".format(metric.value.percentage))
                append("%")
                appendLine('|')
            }
        }

        appendLine()

        when {
            analysis.addedArtifact && !analysis.removedArtifact ->
                appendLine("note: artifact(s) were added ⚠️")
            !analysis.addedArtifact && analysis.removedArtifact ->
                appendLine("note: artifact(s) were removed ⚠️")
            analysis.addedArtifact && analysis.removedArtifact ->
                appendLine("note: artifact(s) were added and removed ⚠️")
        }
    }

    private data class Metric(
        val currentSize: Long,
        val latestReleaseSize: Long,
        val delta: Long,
        val percentage: Double,
    )

    private fun File.toMap(): Map<String, Long> {
        val lines = this.readLines()
        val headers = lines[0].split(",").map { it.trim() }
        val values = lines[1].split(",").map { it.trim() }
        val metrics = mutableMapOf<String, Long>()

        headers.forEachIndexed { index, header ->
            metrics[header] = values[index].toLong()
        }

        return metrics
    }

    private val noDiffMessage = buildString {
        appendLine("Affected Artifacts")
        appendLine("=")
        appendLine("No artifacts changed size")
    }
}
