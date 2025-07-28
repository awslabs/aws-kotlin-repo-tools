/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.gradle.dsl

import aws.sdk.kotlin.gradle.util.verifyRootProject
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.*
import org.gradle.plugins.signing.Sign
import org.gradle.plugins.signing.SigningExtension
import org.jreleaser.gradle.plugin.JReleaserExtension
import org.jreleaser.model.Active

private object Properties {
    const val SKIP_PUBLISHING = "skipPublish"
}

private object EnvironmentVariables {
    const val GROUP_ID = "JRELEASER_PROJECT_JAVA_GROUP_ID"
    const val MAVEN_CENTRAL_USERNAME = "JRELEASER_MAVENCENTRAL_USERNAME"
    const val MAVEN_CENTRAL_TOKEN = "JRELEASER_MAVENCENTRAL_TOKEN"
    const val GPG_PASSPHRASE = "JRELEASER_GPG_PASSPHRASE"
    const val GPG_PUBLIC_KEY = "JRELEASER_GPG_PUBLIC_KEY"
    const val GPG_SECRET_KEY = "JRELEASER_GPG_SECRET_KEY"
}

private val ALLOWED_PUBLICATION_NAMES = setOf(
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
    extra.set(Properties.SKIP_PUBLISHING, true)
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

        val secretKey = System.getenv(EnvironmentVariables.GPG_SECRET_KEY)
        val passphrase = System.getenv(EnvironmentVariables.GPG_PASSPHRASE)

        if (!secretKey.isNullOrBlank() && !passphrase.isNullOrBlank()) {
            apply(plugin = "signing")
            extensions.configure<SigningExtension> {
                useInMemoryPgpKeys(
                    secretKey,
                    passphrase,
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
                    logger.warn("Skipping publication, project=${project.name}; publication=${publication.name}; group=${publication.groupId}")
                }
            }
        }
    }
}

/**
 * Configure JReleaser publishing plugin. This (conditionally) enables the `org.jreleaser` plugin and configures it.
 */
fun Project.configureJReleaser() {
    verifyRootProject { "JReleaser configuration must be applied to the root project only" }

    var missingVariables = false
    listOf(
        EnvironmentVariables.MAVEN_CENTRAL_USERNAME,
        EnvironmentVariables.MAVEN_CENTRAL_TOKEN,
        EnvironmentVariables.GPG_PASSPHRASE,
        EnvironmentVariables.GPG_PUBLIC_KEY,
        EnvironmentVariables.GPG_SECRET_KEY,
    ).forEach {
        if (System.getenv(it).isNullOrBlank()) {
            missingVariables = true
            logger.warn("Skipping JReleaser configuration, missing required environment variable: $it")
        }
    }
    if (missingVariables) return

    // Get SDK version from gradle.properties
    val sdkVersion: String by project

    apply(plugin = "org.jreleaser")
    extensions.configure<JReleaserExtension> {
        project {
            version = sdkVersion
        }

        signing {
            active = Active.ALWAYS
            armored = true
        }

        // Used for creating a tagged release, uploading files and generating changelogs.
        // In the future we can set this up to push release tags to GitHub, but for now it's
        // set up to do nothing.
        // https://jreleaser.org/guide/latest/reference/release/index.html
        release {
            generic {
                enabled = true
                skipRelease = true
            }
        }

        // Used to announce a release to configured announcers.
        // https://jreleaser.org/guide/latest/reference/announce/index.html
        announce {
            active = Active.NEVER
        }

        deploy {
            maven {
                mavenCentral {
                    create("maven-central") {
                        active = Active.ALWAYS
                        url = "https://central.sonatype.com/api/v1/publisher"
                        stagingRepository(rootProject.layout.buildDirectory.dir("m2").get().toString())
                        artifacts {
                            artifactOverride {
                                artifactId = "version-catalog"
                                jar = false
                                verifyPom = false // jreleaser doesn't understand toml packaging
                            }
                        }
                        maxRetries = 100
                        retryDelay = 60 // seconds
                    }
                }
            }
        }
    }
}

private fun isAvailableForPublication(project: Project, publication: MavenPublication): Boolean {
    var shouldPublish = true

    // Check SKIP_PUBLISH_PROP
    if (project.extra.has(Properties.SKIP_PUBLISHING)) shouldPublish = false

    // Only publish publications with the configured group from JReleaser or everything if JReleaser group is not configured
    val publishGroupName = System.getenv(EnvironmentVariables.GROUP_ID)
    shouldPublish = shouldPublish && (publishGroupName == null || publication.groupId.startsWith(publishGroupName))

    // Validate publication name is allowed to be published
    shouldPublish = shouldPublish && ALLOWED_PUBLICATION_NAMES.any { publication.name.equals(it, ignoreCase = true) }

    return shouldPublish
}
