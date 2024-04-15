package aws.sdk.kotlin.gradle.plugins.artifactsizemetrics

import aws.sdk.kotlin.gradle.util.AwsSdkGradleException
import aws.sdk.kotlin.services.cloudwatch.CloudWatchClient
import aws.sdk.kotlin.services.cloudwatch.model.Dimension
import aws.sdk.kotlin.services.cloudwatch.model.MetricDatum
import aws.sdk.kotlin.services.cloudwatch.model.StandardUnit
import aws.sdk.kotlin.services.cloudwatch.putMetricData
import aws.smithy.kotlin.runtime.time.Instant
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction

/**
 * Puts a projects local artifact size metrics in CloudWatch.
 */
internal abstract class PutArtifactSizeMetricsInCloudWatch: DefaultTask() {
    /**
     * File containing the project's current computed artifact size metrics.
     */
    @get:InputFile
    abstract val metricsFile: RegularFileProperty

    init {
        metricsFile.convention(project.layout.buildDirectory.file(OUTPUT_PATH + "artifact-size-metrics.csv"))
    }

    @TaskAction
    suspend fun put() {
        val release = project.property("release") ?: throw AwsSdkGradleException("Please specify a release number")
        val now = Instant.now()
        val metrics = metricsFile
            .get()
            .asFile
            .readLines()
            .drop(1) // Ignoring header

        CloudWatchClient.fromEnvironment().use { client ->
            metrics.forEach { metric ->
                val split = metric.split(",").map { it.trim() }
                val artifactName = split[0]
                val artifactSize = split[1].toDouble()

                client.putMetricData {
                    namespace = "Artifact Size Metrics"
                    metricData = listOf(
                        MetricDatum {
                            metricName = artifactName
                            timestamp = now
                            unit = StandardUnit.Bytes
                            value = artifactSize
                            dimensions = listOf(
                                Dimension {
                                    name = "Release"
                                    value = release.toString()
                                }
                            )
                        },
                        MetricDatum {
                            metricName = artifactName
                            timestamp = now
                            unit = StandardUnit.Bytes
                            value = artifactSize
                        },
                    )
                }
            }
        }
    }
}


