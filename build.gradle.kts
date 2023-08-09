/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
allprojects {
    repositories {
        mavenCentral()
    }
}

// chicken and egg problem, we can't use the kotlinter gradle plugin here AND use our custom rules
val ktlint by configurations.creating {
    attributes {
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
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
