/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.gradle.plugins.artifactsizemetrics

import aws.sdk.kotlin.gradle.util.AwsSdkGradleException
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.putObject
import aws.smithy.kotlin.runtime.content.ByteStream
import kotlinx.coroutines.runBlocking
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction

/**
 * Puts artifact size metrics in S3 to save them.
 * We put them in CloudWatch also (as metrics) but CloudWatch only keeps metrics temporarily.
 */
internal abstract class SaveArtifactSizeMetrics : DefaultTask() {
    /**
     * File containing the project's artifact size metrics.
     */
    @get:InputFile
    abstract val metricsFile: RegularFileProperty

    init {
        metricsFile.convention(project.layout.buildDirectory.file(OUTPUT_PATH + "artifact-size-metrics.csv"))
    }

    private val pluginConfig = project.rootProject.extensions.getByType(ArtifactSizeMetricsPluginConfig::class.java)

    @TaskAction
    fun save() {
        val releaseTag = project.findProperty("release")?.toString()?.also {
            check(it.isNotEmpty()) { "The release property is set to empty \"-Prelease=\" (no value set). Please specify a value." }
        } ?: throw AwsSdkGradleException("The release property is not set. Please set a value: \"-Prelease=YOUR_RELEASE_VALUE\"")

        runBlocking {
            S3Client.fromEnvironment().use { s3 ->
                s3.putObject {
                    bucket = S3_ARTIFACT_SIZE_METRICS_BUCKET
                    key = "${pluginConfig.projectRepositoryName}-latest-release.csv"
                    body = ByteStream.fromString(metricsFile.get().asFile.readText())
                }

                s3.putObject {
                    bucket = S3_ARTIFACT_SIZE_METRICS_BUCKET
                    key = "${pluginConfig.projectRepositoryName}-$releaseTag-release.csv"
                    body = ByteStream.fromString(metricsFile.get().asFile.readText())
                }
            }
        }
    }
}
