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

// Props
private const val SKIP_PUBLISH_PROP = "skipPublish"

// Env vars
private const val J_RELEASER_STAGE_ENV_VAR = "JRELEASER_MAVENCENTRAL_STAGE"
private const val J_RELEASER_GROUP_ENV_VAR = "JRELEASER_PROJECT_JAVA_GROUP_ID"
private const val J_RELEASER_MAVEN_CENTRAL_USERNAME_ENV_VAR = "JRELEASER_MAVENCENTRAL_USERNAME"
private const val J_RELEASER_MAVEN_CENTRAL_TOKEN_ENV_VAR = "JRELEASER_MAVENCENTRAL_TOKEN"
private const val J_RELEASER_GPG_PASSPHRASE_ENV_VAR = "JRELEASER_GPG_PASSPHRASE"
private const val J_RELEASER_GPG_PUBLIC_KEY_ENV_VAR = "JRELEASER_GPG_PUBLIC_KEY"
private const val J_RELEASER_GPG_SECRET_KEY_ENV_VAR = "JRELEASER_GPG_SECRET_KEY"

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

        val gpgSecretKey = System.getenv(J_RELEASER_GPG_SECRET_KEY_ENV_VAR)
        val gpgPassphrase = System.getenv(J_RELEASER_GPG_PASSPHRASE_ENV_VAR)

        if (!gpgPassphrase.isNullOrBlank() && !gpgSecretKey.isNullOrBlank()) {
            apply(plugin = "signing")
            extensions.configure<SigningExtension> {
                useInMemoryPgpKeys(
                    gpgSecretKey,
                    gpgPassphrase,
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
    Creates a dummy JAR for version catalog publishing
    The `version-catalog` plugin doesn't generate one because it isn't needed but JReleaser requires a jar to be present in the version catalog component
    https://docs.gradle.org/current/userguide/version_catalogs.html#sec:version-catalog-plugin

    Consuming published version catalogs with the dummy JAR still work
    https://docs.gradle.org/current/userguide/version_catalogs.html#sec:importing-published-catalog
     */
    tasks.register<Jar>("versionCatalogJar") {
        archiveBaseName.set("version-catalog")
        from("gradle/libs.versions.toml")
    }
}

/**
 * Configure JReleaser publishing plugin. This (conditionally) enables the `org.jreleaser` plugin and configures it.
 */
fun Project.configureJReleaser() {
    verifyRootProject { "JReleaser configuration must be applied to the root project only" }

    var missingEnvVars = false
    listOf(
        J_RELEASER_STAGE_ENV_VAR,
        J_RELEASER_GROUP_ENV_VAR,
        J_RELEASER_MAVEN_CENTRAL_USERNAME_ENV_VAR,
        J_RELEASER_MAVEN_CENTRAL_TOKEN_ENV_VAR,
        J_RELEASER_GPG_PASSPHRASE_ENV_VAR,
        J_RELEASER_GPG_PUBLIC_KEY_ENV_VAR,
        J_RELEASER_GPG_SECRET_KEY_ENV_VAR,
    ).forEach {
        if (System.getenv(it).isNullOrBlank()) {
            missingEnvVars = true
            logger.warn("Skipping JReleaser configuration, missing or blank required env var: $it")
        }
    }
    if (missingEnvVars) return

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
    if (project.extra.has(SKIP_PUBLISH_PROP)) shouldPublish = false

    // Validate publishGroupName
    val publishGroupName = System.getenv(J_RELEASER_GROUP_ENV_VAR)
    shouldPublish = shouldPublish && (publishGroupName == null || publication.groupId.startsWith(publishGroupName))

    // Validate publication name is allowed to be published
    shouldPublish = shouldPublish && ALLOWED_PUBLICATIONS.any { publication.name.equals(it, ignoreCase = true) }

    return shouldPublish
}
