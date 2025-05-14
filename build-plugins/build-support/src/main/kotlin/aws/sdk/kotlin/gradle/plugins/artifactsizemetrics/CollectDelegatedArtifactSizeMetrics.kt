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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Collects the delegated artifact size metrics for a projects subprojects from S3, combines them and outputs them to a file.
 * This task should typically be run after codebuild gathers metrics and puts them in S3 during CI but can also be used to
 * query the metrics bucket if you modify the file key filter.
 */
internal abstract class CollectDelegatedArtifactSizeMetrics : DefaultTask() {
    /**
     * The file where the artifact size metrics will be stored, defaults to build/reports/metrics/artifact-size-metrics.csv
     */
    @get:OutputFile
    abstract val metricsFile: RegularFileProperty

    init {
        metricsFile.convention(project.layout.buildDirectory.file(OUTPUT_PATH + "artifact-size-metrics.csv"))
    }

    private val pluginConfig = project.rootProject.extensions.getByType(ArtifactSizeMetricsPluginConfig::class.java)

    @TaskAction
    fun collect() {
        val pullRequestNumber = project.findProperty("pullRequest")?.toString()?.takeIf { it.isNotEmpty() }
        val releaseTag = project.findProperty("release")?.toString()?.takeIf { it.isNotEmpty() }
        val identifier = pullRequestNumber ?: releaseTag ?: throw AwsSdkGradleException("Please specify a pull request or release number")

        val artifactSizeMetricsFileKeys = getFileKeys(identifier) ?: throw AwsSdkGradleException("Unable to list objects from artifact size metrics bucket")
        val artifactSizeMetricsFiles = getFiles(artifactSizeMetricsFileKeys)
        val combined = combine(artifactSizeMetricsFiles)

        metricsFile.asFile.get().writeText(combined)
    }

    private fun getFileKeys(identifier: String): List<String>? = runBlocking {
        val prefixes = pluginConfig.bucketPrefixOverride?.let { listOf(it) } ?: listOf(
            "[TEMP]${pluginConfig.projectRepositoryName}-$identifier-",
            "[TEMP]private-${pluginConfig.projectRepositoryName}-staging-$identifier-",
        )

        return@runBlocking prefixes.firstNotNullOfOrNull { prefix ->
            S3Client.fromEnvironment().use { s3 ->
                s3.listObjects {
                    bucket = S3_ARTIFACT_SIZE_METRICS_BUCKET
                    this.prefix = prefix
                }.contents?.map {
                    it.key ?: throw AwsSdkGradleException("A file from the artifact size metrics bucket is missing a key")
                }
            }
        }
    }

    private fun getFiles(keys: List<String>): List<String> = runBlocking {
        S3Client.fromEnvironment().use { s3 ->
            keys.map { key ->
                async { s3.getObjectAsText(key) }
            }.awaitAll()
        }
    }

    private suspend fun S3Client.getObjectAsText(objectKey: String) = getObject(
        GetObjectRequest {
            bucket = S3_ARTIFACT_SIZE_METRICS_BUCKET
            key = objectKey
        },
    ) { it.body?.decodeToString() ?: throw AwsSdkGradleException("Metrics file $objectKey is missing a body") }

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
