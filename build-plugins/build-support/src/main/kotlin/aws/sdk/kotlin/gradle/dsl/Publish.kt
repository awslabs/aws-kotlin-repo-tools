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

private object SystemProperties {
    const val SKIP_PUBLISHING = "skipPublish"

    object JReleaser {
        const val GROUP_ID = "jreleaser.project.java.group.id"
        const val MAVEN_CENTRAL_USERNAME = "jreleaser.mavencentral.username"
        const val MAVEN_CENTRAL_TOKEN = "jreleaser.mavencentral.token"
        const val GPG_PASSPHRASE = "jreleaser.gpg.passphrase"
        const val GPG_PUBLIC_KEY = "jreleaser.gpg.public.key"
        const val GPG_SECRET_KEY = "jreleaser.gpg.secret.key"
    }
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
    extra.set(SystemProperties.SKIP_PUBLISHING, true)
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

        if (project.hasProperty(SystemProperties.JReleaser.GPG_SECRET_KEY) && project.hasProperty(SystemProperties.JReleaser.GPG_PASSPHRASE)) {
            apply(plugin = "signing")
            extensions.configure<SigningExtension> {
                useInMemoryPgpKeys(
                    project.property(SystemProperties.JReleaser.GPG_SECRET_KEY) as String,
                    project.property(SystemProperties.JReleaser.GPG_PASSPHRASE) as String,
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

    /*
    Creates a dummy JAR for the version catalog
    The `version-catalog` plugin doesn't generate one because it isn't needed but JReleaser requires a jar for publishing
    https://docs.gradle.org/current/userguide/version_catalogs.html#sec:version-catalog-plugin

    Consuming published version catalogs with the dummy JAR still work
    https://docs.gradle.org/current/userguide/version_catalogs.html#sec:importing-published-catalog
     */
    tasks.register<Jar>("versionCatalogJar") {
        archiveBaseName.set("version-catalog")
        from("gradle/libs.versions.toml") // Could be anything
    }
}

/**
 * Configure JReleaser publishing plugin. This (conditionally) enables the `org.jreleaser` plugin and configures it.
 */
fun Project.configureJReleaser() {
    verifyRootProject { "JReleaser configuration must be applied to the root project only" }

    var missingSystemProperties = false
    listOf(
        SystemProperties.JReleaser.GROUP_ID,
        SystemProperties.JReleaser.MAVEN_CENTRAL_USERNAME,
        SystemProperties.JReleaser.MAVEN_CENTRAL_TOKEN,
        SystemProperties.JReleaser.GPG_PASSPHRASE,
        SystemProperties.JReleaser.GPG_PUBLIC_KEY,
        SystemProperties.JReleaser.GPG_SECRET_KEY,
    ).forEach {
        if (!project.hasProperty(it)) {
            missingSystemProperties = true
            logger.warn("Skipping JReleaser configuration, missing required system property: $it")
        }
    }
    if (missingSystemProperties) return

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
        deploy {
            maven {
                mavenCentral {
                    create("maven-central") {
                        active = Active.ALWAYS
                        url = "https://central.sonatype.com/api/v1/publisher"
                        stagingRepository(rootProject.layout.buildDirectory.dir("m2").get().toString())
                    }
                }
            }
        }
    }
}

private fun isAvailableForPublication(project: Project, publication: MavenPublication): Boolean {
    var shouldPublish = true

    // Check SKIP_PUBLISH_PROP
    if (project.extra.has(SystemProperties.SKIP_PUBLISHING)) shouldPublish = false

    // Validate publishGroupName
    val publishGroupName = System.getenv(SystemProperties.JReleaser.GROUP_ID)
    shouldPublish = shouldPublish && (publishGroupName == null || publication.groupId.startsWith(publishGroupName))

    // Validate publication name is allowed to be published
    shouldPublish = shouldPublish && ALLOWED_PUBLICATION_NAMES.any { publication.name.equals(it, ignoreCase = true) }

    return shouldPublish
}
