/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.gradle.dsl

import aws.sdk.kotlin.gradle.util.verifyRootProject
import org.gradle.api.Project
import org.gradle.api.attributes.Bundling
import org.gradle.api.tasks.JavaExec
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register

/**
 * Configure lint rules for the project
 * @param lintPaths list of paths relative to the project root to lint (or not lint).
 */
fun Project.configureLinting(lintPaths: List<String>) {
    verifyRootProject { "Kotlin SDK lint configuration is expected to be configured on the root project" }

    val ktlint = configurations.create("ktlint") {
        attributes {
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        }
    }

    // TODO - is there anyway to align this with the version from libs.versions.toml in this project/repo
    val ktlintVersion = "0.48.1"
    dependencies {
        ktlint("com.pinterest:ktlint:$ktlintVersion")
    }

    // add the buildscript classpath which should pickup our custom ktlint-rules (via runtimeOnly dep on this plugin)
    // plus any custom rules added by consumer
    val execKtlintClaspath = ktlint + buildscript.configurations.getByName("classpath")
    tasks.register<JavaExec>("ktlint") {
        description = "Check Kotlin code style."
        group = "Verification"
        classpath = execKtlintClaspath
        mainClass.set("com.pinterest.ktlint.Main")
        args = lintPaths
        jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
    }

    tasks.register<JavaExec>("ktlintFormat") {
        description = "Auto fix Kotlin code style violations"
        group = "formatting"
        classpath = execKtlintClaspath
        mainClass.set("com.pinterest.ktlint.Main")
        args = listOf("-F") + lintPaths
        jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
    }
}
