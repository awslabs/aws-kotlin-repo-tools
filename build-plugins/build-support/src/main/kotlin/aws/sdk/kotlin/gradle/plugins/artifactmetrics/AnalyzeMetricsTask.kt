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
import kotlin.math.roundToInt

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
        runBlocking {
            S3Client.fromEnvironment().use { s3 ->
                s3.getObject(
                    GetObjectRequest {
                        bucket = "TODO"
                        key = "TODO"
                    },
                ) { latestReleaseMetrics ->
                    val remoteMetricsFile =
                        project.layout.buildDirectory.file(OUTPUT_PATH + "latestReleaseMetrics.csv").get().asFile

                    remoteMetricsFile.writeText(
                        latestReleaseMetrics.body?.decodeToString() ?: throw GradleException("Metrics from latest release are empty"),
                    )

                    val remoteMetrics = remoteMetricsFile.toMap()
                    val remoteLines = remoteMetricsFile.readLines()
                    val remoteHeaders = remoteLines[0].split(",").map { it.trim() }

                    val localMetrics = metricsFile.get().asFile.toMap()
                    val localLines = metricsFile.get().asFile.readLines()
                    val localHeaders = localLines[0].split(",").map { it.trim() }

                    val artifactNames = remoteHeaders.toSet() + localHeaders.toSet()
                    val metrics = mutableMapOf<String, DataPoint>()

                    var addedArtifact = false
                    var removedArtifact = false
                    var significantChange = false

                    artifactNames.forEach { artifact ->
                        val local = localMetrics[artifact] ?: 0
                        val remote = remoteMetrics[artifact] ?: 0

                        val diff = local - remote
                        val percentage = buildString {
                            if (remote == 0L || local == 0L) {
                                append("NaN")

                                if (remote == 0L) addedArtifact = true
                                if (local == 0L) removedArtifact = true

                                return@buildString
                            }

                            if (diff < 0) append("-")

                            val percentageValue = (abs(diff).toDouble() / remote.toDouble() * 100).roundToInt()
                            if (percentageValue > 5 && diff > 0) significantChange = true

                            append(percentageValue)
                            append("%")
                        }

                        metrics[artifact] =
                            DataPoint(
                                remote,
                                local,
                                diff,
                                percentage,
                            )
                    }

                    shouldFailWorkflow.get().asFile.writeText(significantChange.toString())

                    val diffTable = buildString {
                        appendLine("Affected Artifacts\n=")
                        appendLine("| Artifact |Pull Request (bytes) | Latest Release (bytes) | Delta (bytes / percentage) |")
                        appendLine("| -------- | ------------------- | ---------------------- | -------------------------- |")
                        metrics.forEach { artifact ->
                            if (artifact.value.diff != 0L) {
                                appendLine("|${artifact.key}|${"%,d".format(artifact.value.localSize)}|${"%,d".format(artifact.value.latestReleaseSize)}|${"%,d".format(artifact.value.diff)} / ${artifact.value.percentage}|")
                            }
                        }
                        appendLine()

                        when {
                            addedArtifact && !removedArtifact -> appendLine("note: artifact(s) were added ⚠️")
                            !addedArtifact && removedArtifact -> appendLine("note: artifact(s) were removed ⚠️")
                            addedArtifact && removedArtifact -> appendLine("note: artifact(s) were added and removed ⚠️")
                        }
                    }

                    summary.get().asFile.writeText(diffTable)
                }
            }
        }
    }

    private data class DataPoint(
        val latestReleaseSize: Long,
        val localSize: Long,
        val diff: Long,
        val percentage: String,
    )

    private fun File.toMap(): MutableMap<String, Long> {
        val lines = this.readLines()
        val headers = lines[0].split(",").map { it.trim() }
        val values = lines[1].split(",").map { it.trim() }
        val metrics = mutableMapOf<String, Long>()

        headers.forEachIndexed { index, header ->
            metrics[header] = values[index].toLong()
        }

        return metrics
    }
}
