/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.gradle.dsl

import aws.sdk.kotlin.gradle.util.verifyRootProject
import org.gradle.api.Project
import org.gradle.api.attributes.Bundling
import org.gradle.api.tasks.JavaExec
import org.gradle.kotlin.dsl.*
import org.gradle.language.base.plugins.LifecycleBasePlugin

/**
 * Configure lint rules for the project
 * @param lintPaths list of paths relative to the project root to lint (or not lint).
 */
fun Project.configureLinting(lintPaths: List<String>) {
    verifyRootProject { "Kotlin SDK lint configuration is expected to be configured on the root project" }

    val ktlint by configurations.creating

    dependencies {
        val ktlintVersion = "1.3.0"
        ktlint("com.pinterest.ktlint:ktlint-cli:$ktlintVersion") {
            attributes {
                attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
            }

            // Ensure that kotlin-compiler-embeddable isn't included in the buildscript classpath in consuming modules
            exclude(group = "org.jetbrains.kotlin", module = "kotlin-compiler-embeddable")
        }
    }

    // add the buildscript classpath which should pickup our custom ktlint-rules (via runtimeOnly dep on this plugin)
    // plus any custom rules added by consumer
    val execKtlintClasspath = ktlint + buildscript.configurations.getByName("classpath")

    tasks.register<JavaExec>("ktlint") {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Check Kotlin code style."
        classpath = execKtlintClasspath
        mainClass.set("com.pinterest.ktlint.Main")
        args = lintPaths
    }

    tasks.register<JavaExec>("ktlintFormat") {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Auto fix Kotlin code style violations"
        classpath = execKtlintClasspath
        mainClass.set("com.pinterest.ktlint.Main")
        args = listOf("-F") + lintPaths
        jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    }
}
