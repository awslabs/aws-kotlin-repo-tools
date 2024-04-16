/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.gradle.plugins.artifactsizemetrics

import aws.sdk.kotlin.gradle.util.AwsSdkGradleException
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.listObjects
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.smithy.kotlin.runtime.content.decodeToString
import kotlinx.coroutines.runBlocking
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Collects the artifact size metrics for a projects subprojects from S3, combines them and outputs them to a file
 */
internal abstract class CollectDelegatedArtifactSizeMetrics : DefaultTask() {
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
        val pullRequestNumber = if (project.hasProperty("pullRequest")) {
            project.property("pullRequest")
                .toString()
                .let { releaseProperty ->
                    releaseProperty.ifEmpty { // "-PpullRequest=" (no value set)
                        null
                    }
                }
        } else null

        val releaseTag = if (project.hasProperty("release")) {
            project.property("release")
                .toString()
                .let { releaseProperty ->
                    releaseProperty.ifEmpty { // "-Prelease=" (no value set)
                        null
                    }
                }
        } else null

        val identifier = pullRequestNumber ?: releaseTag ?: throw AwsSdkGradleException("Please specify a pull request or release number")

        val artifactSizeMetricsFileKeys = getArtifactSizeMetricsFileKeys() ?: throw AwsSdkGradleException("Unable to list objects from artifact size metrics bucket")

        val pluginConfig = this.project.rootProject.extensions.getByType(ArtifactSizeMetricsPluginConfig::class.java)

        val relevantArtifactSizeMetricsFileKeys = artifactSizeMetricsFileKeys.filter {
            it?.startsWith("[TEMP]${pluginConfig.projectRepositoryName}-$identifier-") == true
        }

        val artifactSizeMetricsFiles = getArtifactSizeMetricsFiles(relevantArtifactSizeMetricsFileKeys)
        val combined = combine(artifactSizeMetricsFiles)

        metricsFile.asFile.get().writeText(combined)
    }

    private fun getArtifactSizeMetricsFileKeys(): List<String?>? = runBlocking {
        S3Client.fromEnvironment().use { s3 ->
            return@runBlocking s3.listObjects {
                bucket = S3_ARTIFACT_SIZE_METRICS_BUCKET
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
                                bucket = S3_ARTIFACT_SIZE_METRICS_BUCKET
                                key = k
                            },
                        ) { file ->
                            files.add(
                                file.body?.decodeToString() ?: throw AwsSdkGradleException("Metrics file $file is missing a body")
                            )
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
                    if (metric.isNotEmpty()) {
                        appendLine(metric)
                    }
                }
        }
    }
}
