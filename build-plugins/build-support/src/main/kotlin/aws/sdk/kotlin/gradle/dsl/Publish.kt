/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.gradle.dsl

import aws.sdk.kotlin.gradle.util.verifyRootProject
import io.github.gradlenexus.publishplugin.NexusPublishExtension
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.*
import org.gradle.plugins.signing.Sign
import org.gradle.plugins.signing.SigningExtension
import java.time.Duration

private const val PUBLISH_GROUP_NAME_PROP = "publishGroupName"
private const val SKIP_PUBLISH_PROP = "skipPublish"
private const val SIGNING_KEY_PROP = "signingKey"
private const val SIGNING_PASSWORD_PROP = "signingPassword"
private const val SONATYPE_USERNAME_PROP = "sonatypeUsername"
private const val SONATYPE_PASSWORD_PROP = "sonatypePassword"

// Names of publications that are allowed to be published
private val ALLOWED_PUBLICATIONS = listOf(
    "common",
    "jvm",
    "metadata",
    "kotlinMultiplatform",
    "bom",
    "versionCatalog",
    "android", // aws-crt-kotlin
    "codegen",
    "codegen-testutils",

    // aws-sdk-kotlin:hll
    "hll-codegen",
    "dynamodb-mapper-codegen",
    "dynamodb-mapper-schema-generator-plugin",
    "dynamodb-mapper-schema-codegen",
    "dynamodb-mapper-schema-generatorPluginMarkerMaven",
)

/**
 * Mark this project as excluded from publishing
 */
fun Project.skipPublishing() {
    extra.set(SKIP_PUBLISH_PROP, true)
}

/**
 * Configure publishing for this project. This applies the `maven-publish` and `signing` plugins and configures
 * the publications.
 * @param repoName the repository name (e.g. `smithy-kotlin`, `aws-sdk-kotlin`, etc)
 * @param githubOrganization the name of the GitHub organization that [repoName] is located in
 */
fun Project.configurePublishing(repoName: String, githubOrganization: String = "awslabs") {
    val project = this
    apply(plugin = "maven-publish")

    // FIXME: create a real "javadoc" JAR from Dokka output
    val javadocJar = tasks.register<Jar>("emptyJar") {
        archiveClassifier.set("javadoc")
        destinationDirectory.set(layout.buildDirectory.dir("libs"))
        from()
    }

    extensions.configure<PublishingExtension> {
        repositories {
            maven {
                name = "testLocal"
                url = rootProject.layout.buildDirectory.dir("m2").get().asFile.toURI()
            }
        }

        publications.all {
            if (this !is MavenPublication) return@all

            project.afterEvaluate {
                pom {
                    name.set(project.name)
                    description.set(project.description)
                    url.set("https://github.com/$githubOrganization/$repoName")
                    licenses {
                        license {
                            name.set("Apache-2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    developers {
                        developer {
                            id.set(repoName)
                            name.set("AWS SDK Kotlin Team")
                        }
                    }
                    scm {
                        connection.set("scm:git:git://github.com/$githubOrganization/$repoName.git")
                        developerConnection.set("scm:git:ssh://github.com/$githubOrganization/$repoName.git")
                        url.set("https://github.com/$githubOrganization/$repoName")
                    }

                    artifact(javadocJar)
                }
            }
        }

        if (project.hasProperty(SIGNING_KEY_PROP) && project.hasProperty(SIGNING_PASSWORD_PROP)) {
            apply(plugin = "signing")
            extensions.configure<SigningExtension> {
                useInMemoryPgpKeys(
                    project.property(SIGNING_KEY_PROP) as String,
                    project.property(SIGNING_PASSWORD_PROP) as String,
                )
                sign(publications)
            }

            // FIXME - workaround for https://github.com/gradle/gradle/issues/26091
            val signingTasks = tasks.withType<Sign>()
            tasks.withType<AbstractPublishToMaven>().configureEach {
                mustRunAfter(signingTasks)
            }
        }
    }

    tasks.withType<AbstractPublishToMaven>().configureEach {
        onlyIf {
            isAvailableForPublication(project, publication).also {
                if (!it) {
                    logger.warn(
                        "Skipping publication, project=${project.name}; publication=${publication.name}; group=${publication.groupId}",
                    )
                }
            }
        }
    }
}

/**
 * Configure nexus publishing plugin. This (conditionally) enables the `gradle-nexus.publish-plugin` and configures it.
 */
fun Project.configureNexus() {
    verifyRootProject { "Kotlin SDK nexus configuration must be applied to the root project only" }

    val requiredProps = listOf(SONATYPE_USERNAME_PROP, SONATYPE_PASSWORD_PROP, PUBLISH_GROUP_NAME_PROP)
    val doConfigure = requiredProps.all { project.hasProperty(it) }
    if (!doConfigure) {
        logger.info("skipping nexus configuration, missing one or more required properties: $requiredProps")
        return
    }

    apply(plugin = "io.github.gradle-nexus.publish-plugin")
    extensions.configure<NexusPublishExtension> {
        val publishGroupName = project.property(PUBLISH_GROUP_NAME_PROP) as String
        group = publishGroupName
        packageGroup.set(publishGroupName)
        repositories {
            create("awsNexus") {
                nexusUrl.set(uri("https://aws.oss.sonatype.org/service/local/"))
                snapshotRepositoryUrl.set(uri("https://aws.oss.sonatype.org/content/repositories/snapshots/"))
                username.set(project.property(SONATYPE_USERNAME_PROP) as String)
                password.set(project.property(SONATYPE_PASSWORD_PROP) as String)
            }
        }

        transitionCheckOptions {
            maxRetries.set(180)
            delayBetween.set(Duration.ofSeconds(10))
        }
    }
}

private fun isAvailableForPublication(project: Project, publication: MavenPublication): Boolean {
    var shouldPublish = true

    // Check SKIP_PUBLISH_PROP
    if (project.extra.has(SKIP_PUBLISH_PROP)) shouldPublish = false

    // Validate publishGroupName
    val publishGroupName = project.findProperty(PUBLISH_GROUP_NAME_PROP) as? String
    shouldPublish = shouldPublish && (publishGroupName == null || publication.groupId.startsWith(publishGroupName))

    // Validate publication name is allowed to be published
    shouldPublish = shouldPublish && ALLOWED_PUBLICATIONS.any { publication.name.equals(it, ignoreCase = true) }

    return shouldPublish
}
