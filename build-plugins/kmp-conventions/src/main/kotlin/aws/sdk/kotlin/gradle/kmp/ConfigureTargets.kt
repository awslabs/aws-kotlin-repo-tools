/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.gradle.kmp

import aws.sdk.kotlin.gradle.util.prop
import org.gradle.api.Project
import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.HostManager
import java.io.File

internal fun <T> Project.tryGetClass(className: String): Class<T>? {
    val classLoader = buildscript.classLoader
    return try {
        @Suppress("UNCHECKED_CAST")
        Class.forName(className, false, classLoader) as Class<T>
    } catch (e: ClassNotFoundException) {
        null
    }
}

val Project.files: Array<File> get() = project.projectDir.listFiles() ?: emptyArray()

val Project.hasCommon: Boolean get() = files.any {
    // FIXME - this is somewhat specific to aws-sdk-kotlin to consider "generated-src" a common sourceSet root
    it.name == "common" || it.name == "generated-src"
}

// always configured with common
val Project.hasJvm: Boolean get() = hasCommon || hasJvmAndNative || files.any { it.name == "jvm" }
val Project.hasNative: Boolean get() = hasCommon || files.any { it.name == "native" }
val Project.hasJs: Boolean get() = hasCommon || files.any { it.name == "js" }
val Project.hasJvmAndNative: Boolean get() = hasCommon || files.any { it.name == "jvmAndNative" }

// less frequent, more explicit targets
val Project.hasDesktop: Boolean get() = hasNative || files.any { it.name == "desktop" }
val Project.hasLinux: Boolean get() = hasNative || hasJvmAndNative || files.any { it.name == "linux" }
val Project.hasApple: Boolean get() = hasNative || hasJvmAndNative || files.any { it.name == "apple" }
val Project.hasWindows: Boolean get() = hasNative || files.any { it.name == "windows" }

/**
 * Test if a project follows the convention and needs configured for KMP (used in handful of spots where we have a
 * subproject that is just a container for other projects but isn't a KMP project itself).
 */
public val Project.needsKmpConfigured: Boolean get() = hasCommon || hasJvm || hasNative || hasJs || hasJvmAndNative || hasDesktop || hasLinux || hasApple || hasWindows

@OptIn(ExperimentalKotlinGradlePluginApi::class)
fun Project.configureKmpTargets() {
    val kmpExtensionClass =
        tryGetClass<KotlinMultiplatformExtension>("org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension")

    if (kmpExtensionClass == null) {
        logger.info("$name: skipping KMP configuration for multiplatform; plugin has not been applied")
        return
    }

    pluginManager.withPlugin("kotlin-multiplatform") {
        val kmpExt = extensions.findByType(kmpExtensionClass)
        if (kmpExt == null) {
            logger.info("$name: skipping KMP configuration because multiplatform plugin has not been configured properly")
            return@withPlugin
        }

        // extend the default KMP target hierarchy
        // this does not actually enable the targets, just their relationships
        // see https://kotlinlang.org/docs/multiplatform-hierarchy.html#see-the-full-hierarchy-template
        kmpExt.applyDefaultHierarchyTemplate {
            if (hasJvmAndNative) {
                common {
                    group("jvmAndNative") {
                        withJvm()
                        withNative()
                    }
                }
            }

            if (hasWindows) {
                common {
                    group("windows") {
                        withMingw()
                    }
                }
            }

            if (hasDesktop) {
                common {
                    group("desktop") {
                        withLinux()
                        withMingw()
                        withMacos()
                    }
                }
            }
        }

        // enable the targets
        configureCommon()

        if (hasJvm && JVM_ENABLED) {
            configureJvm()
        }

        // FIXME Configure JS

        if (NATIVE_ENABLED) {
            if (hasApple) {
                kmpExt.apply { configureApple() }
            }
            if (hasWindows) {
                kmpExt.apply { configureWindows() }
            }
            if (hasLinux) {
                kmpExt.apply { configureLinux() }
            }
            if (hasDesktop) {
                kmpExt.apply {
                    configureLinux()
                    configureMacos()
                    configureWindows()
                }
            }
        }

        kmpExt.configureSourceSetsConvention()
    }
}

fun Project.configureCommon() {
    kotlin {
        sourceSets.named("commonTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-annotations-common"))
            }
        }
    }
}

fun Project.configureJvm() {
    kotlin {
        jvm()

        sourceSets.named("jvmTest") {
            dependencies {
                implementation(kotlin("test-junit5"))
            }
        }
    }

    tasks.named<Test>("jvmTest") {
        testLogging {
            events("passed", "skipped", "failed")
            showStandardStreams = true
            showStackTraces = true
            showExceptions = true
            exceptionFormat = TestExceptionFormat.FULL
        }
        useJUnitPlatform()
    }
}

fun Project.configureLinux() {
    kotlin {
        linuxX64()
        linuxArm64() // FIXME - Okio missing arm64 target support
    }
}

fun Project.configureApple() {
    kotlin {
        configureMacos()
        iosSimulatorArm64()
        iosArm64()
        iosX64()
    }
}

fun Project.configureMacos() {
    kotlin {
        macosX64()
        macosArm64()
    }
}

fun Project.configureWindows() {
    kotlin {
        // FIXME Set up Docker files and CMake tasks for Windows
//        mingwX64()
    }
}

fun KotlinMultiplatformExtension.configureSourceSetsConvention() {
    sourceSets.all {
        val srcDir = if (name.endsWith("Main")) "src" else "test"
        val resourcesPrefix = if (name.endsWith("Test")) "test-" else ""
        // the name is always the platform followed by a suffix of either "Main" or "Test" (e.g. jvmMain, commonTest, etc)
        val platform = name.dropLast(4)

        this.kotlin.srcDir("$platform/$srcDir")
        resources.srcDir("$platform/${resourcesPrefix}resources")
        languageSettings.progressiveMode = true
    }
}

val Project.JVM_ENABLED get() = prop("aws.kotlin.jvm")?.let { it == "true" } ?: true
val Project.NATIVE_ENABLED get() = prop("aws.kotlin.native")?.let { it == "true" } ?: true

/**
 * Kotlin/Native Linux and Windows targets are generally enabled on all hosts since
 * the Kotlin toolchain and backend compilers support cross compilation. We
 * are using cinterop and have to compile CRT for those platforms which sometimes
 * requires using docker which isn't always available in CI or setup in users environment.
 *
 * See [KT-30498](https://youtrack.jetbrains.com/issue/KT-30498)
 */
fun Project.disableCrossCompileTargets() {
    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        configure<KotlinMultiplatformExtension> {
            targets.withType<KotlinNativeTarget> {
                val knTarget = this
                when {
                    HostManager.hostIsMac && (knTarget.isLinux || knTarget.isWindows) -> disable(knTarget)
                    HostManager.hostIsLinux && knTarget.isApple -> disable(knTarget)
                    HostManager.hostIsMingw && (knTarget.isLinux || knTarget.isApple) -> disable(knTarget)
                }
            }
        }
    }
}

private val KotlinNativeTarget.isLinux: Boolean
    get() = konanTarget.family == Family.LINUX

private val KotlinNativeTarget.isApple: Boolean
    get() = konanTarget.family.isAppleFamily

private val KotlinNativeTarget.isWindows: Boolean
    get() = konanTarget.family == Family.MINGW


internal fun Project.disable(knTarget: KotlinNativeTarget) {
    logger.warn("disabling Kotlin/Native target: ${knTarget.name}")
    knTarget.apply {
        compilations.all {
            cinterops.all {
                tasks.named(interopProcessingTaskName).configure { enabled = false }
            }
            compileTaskProvider.configure { enabled = false }
        }

        binaries.all {
            linkTaskProvider.configure { enabled = false }
        }

        mavenPublication {
            tasks.withType<AbstractPublishToMaven>().configureEach {
                onlyIf { publication != this@mavenPublication }
            }
            tasks.withType<GenerateModuleMetadata>().configureEach {
                onlyIf { publication != this@mavenPublication }
            }
        }
    }
}
