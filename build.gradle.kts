/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
plugins {
    `maven-publish`
}

val releaseVersion = findProperty("release.version") as? String
if (releaseVersion == null) logger.warn("no release version set")

val s3Url = propertyOrEnv("release.s3.url", "RELEASE_S3_URL")
if (s3Url == null) logger.warn("S3 repository not configured, missing S3 url")

subprojects {
    group = "aws.sdk.kotlin.gradle"
    version = releaseVersion ?: "0.0.1"

    apply(plugin = "maven-publish")
    publishing {
        repositories {
            maven {
                name = "testLocal"
                url = rootProject.layout.buildDirectory.dir("m2").get().asFile.toURI()
            }

            if (s3Url != null) {
                maven {
                    name = "release"
                    url = java.net.URI(s3Url)
                    credentials(AwsCredentials::class.java) {
                        accessKey = propertyOrEnv("aws.accessKeyId", "AWS_ACCESS_KEY_ID")
                        secretKey = propertyOrEnv("aws.secretAccessKey", "AWS_SECRET_ACCESS_KEY")
                        sessionToken = propertyOrEnv("aws.sessionToken", "AWS_SESSION_TOKEN")
                    }
                }
            }
        }
    }
}

fun propertyOrEnv(propName: String, envName: String): String? {
    val env = System.getenv()
    return findProperty(propName) as? String ?: env[envName]
}

// chicken and egg problem, we can't use the kotlinter gradle plugin here AND use our custom rules
val ktlint by configurations.creating {
    attributes {
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.SHADOWED))
    }
}

dependencies {
    ktlint(libs.ktlint)
    ktlint(project(":ktlint-rules"))
}

val lintPaths = listOf(
    "**/*.{kt,kts}",
)

tasks.register<JavaExec>("ktlint") {
    description = "Check Kotlin code style."
    group = "Verification"
    classpath = configurations.getByName("ktlint")
    mainClass.set("com.pinterest.ktlint.Main")
    args = lintPaths
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
}

tasks.register<JavaExec>("ktlintFormat") {
    description = "Auto fix Kotlin code style violations"
    group = "formatting"
    classpath = configurations.getByName("ktlint")
    mainClass.set("com.pinterest.ktlint.Main")
    args = listOf("-F") + lintPaths
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
}
