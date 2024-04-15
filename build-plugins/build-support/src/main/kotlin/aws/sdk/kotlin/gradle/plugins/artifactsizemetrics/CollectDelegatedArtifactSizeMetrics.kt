/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.gradle.plugins.artifactsizemetrics

import aws.sdk.kotlin.gradle.util.AwsSdkGradleException
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.listObjects
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import kotlinx.coroutines.runBlocking
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Collects the artifact size metrics for a projects subprojects from S3, combines them and outputs them to a file
 */
internal abstract class CollectDelegatedArtifactSizeMetricsTask : DefaultTask() {
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
        val pullRequestNumber = project.property("pullRequest")
        val releaseTag = project.property("release")
        val identifier = pullRequestNumber ?: releaseTag ?: throw AwsSdkGradleException("Please specify a pull request or release number")

        val artifactSizeMetricsFileKeys = getArtifactSizeMetricsFileKeys() ?: throw AwsSdkGradleException("Unable to list objects from artifact size metrics bucket")
        artifactSizeMetricsFileKeys.filter { it?.startsWith("[TEMP]${project.rootProject.name}-$identifier-") == true }

        val artifactSizeMetricsFiles = getArtifactSizeMetricsFiles(artifactSizeMetricsFileKeys)
        val combined = combine(artifactSizeMetricsFiles)

        metricsFile.asFile.get().writeText(combined)
    }

    private fun getArtifactSizeMetricsFileKeys(): List<String?>? = runBlocking {
        S3Client.fromEnvironment().use { s3 ->
            return@runBlocking s3.listObjects {
                bucket = ""
            }.contents?.map { it.key }
        }
    }

    private fun getArtifactSizeMetricsFiles(keys: List<String?>): List<String> {
        val files = mutableListOf<String>()

        runBlocking {
            S3Client.fromEnvironment().use { s3 ->
                keys.forEach { k ->
                    k?.let {
                        s3.getObject(
                            GetObjectRequest {
                                bucket = ""
                                key = k
                            },
                        ) { file ->
                            files.add(file.body.toString()) // Files are a few kb each
                        }
                    }
                }
            }
        }

        return files
    }

    private fun combine(metricsFiles: List<String>) = buildString {
        appendLine("Artifact, Size")
        metricsFiles.forEach { metricsFile ->
            metricsFile
                .split("\n")
                .drop(1) // Remove header
                .forEach { metric ->
                    appendLine(metric)
                }
        }
    }
}
