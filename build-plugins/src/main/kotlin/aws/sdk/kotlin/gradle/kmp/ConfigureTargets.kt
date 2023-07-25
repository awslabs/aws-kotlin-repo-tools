package aws.sdk.kotlin.gradle.kmp

import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.KotlinJsCompilerType
import java.io.File

fun <T> Project.tryGetClass(className: String): Class<T>? {
    val classLoader = buildscript.classLoader
    return try {
        @Suppress("UNCHECKED_CAST")
        Class.forName(className, false, classLoader) as Class<T>
    } catch (e: ClassNotFoundException) {
        null
    }
}


val Project.files: Array<File> get() = project.projectDir.listFiles() ?: emptyArray()

val Project.hasCommon: Boolean get() = files.any { it.name == "common" }

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


@OptIn(ExperimentalKotlinGradlePluginApi::class)
fun Project.configureKmpTargets() {
    val kmpExtensionClass =
        tryGetClass<KotlinMultiplatformExtension>("org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension")

    if (kmpExtensionClass == null) {
        logger.info("$name: skipping KMP configuration for multiplatform plugin has not been applied")
        return
    }

    subprojects {
        val subproject = this
        subproject.pluginManager.withPlugin("kotlin-multiplatform") {
            val kotlin = subproject.extensions.findByType(kmpExtensionClass)
            if (kotlin == null) {
                logger.info("$name: skipping KMP configuration because multiplatform plugin has not been configured properly")
                return@withPlugin
            }

            // configure the target hierarchy, this does not actually enable the targets, just their relationships
            // see https://kotlinlang.org/docs/multiplatform-hierarchy.html#see-the-full-hierarchy-template
            kotlin.targetHierarchy.default {
                if (hasJvmAndNative) {
                    group("jvmAndNative"){
                        withJvm()
                        withNative()
                    }
                }

                if (hasWindows) {
                    group("windows") {
                        withMingw()
                    }
                }

                if (hasDesktop) {
                    group("desktop") {
                        withLinux()
                        withMingw()
                        withMacos()
                    }
                }
            }

            // enable targets
            configureCommon(kotlin)

            if (hasJvm) {
                configureJvm(kotlin)
            }

            // TODO - common and jvm only flag

            with(kotlin) {
                if (hasJs) {
                    // FIXME - configure JS
                    // js(KotlinJsCompilerType.IR){
                    //     nodejs()
                    // }
                }

                // if (hasApple) {
                //     macosX64()
                //     macosArm64()
                //     ios()
                //     watchos()
                //     tvos()
                // }

                if (hasLinux) {
                    linuxX64()
                    linuxArm64()
                }

                if (hasWindows) {
                    mingwX64()
                }

                // if (hasDesktop) {
                //     linuxX64()
                //     linuxArm64()
                //     mingwX64()
                //     macosX64()
                //     macosArm64()
                // }

                configureSourceSetsConvention()
            }
        }
    }
}

internal fun Project.configureCommon(kotlin: KotlinMultiplatformExtension) {
    with(kotlin) {
        sourceSets.named("commonMain") {
            // TODO - common deps?
        }

        sourceSets.named("commonTest") {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

internal fun Project.configureJvm(kotlin: KotlinMultiplatformExtension) {
    kotlin.jvm()
    with(kotlin) {
        sourceSets.named("jvmMain"){
            dependencies {
                api(kotlin("stdlib"))
                // TODO - coroutines
            }

        }

        sourceSets.named("jvmTest"){
            dependencies {
                implementation(kotlin("test-junit5"))
                // TODO - junit jupiter, coroutines-debug, kotest-assertions-core-jvm

            }
        }
    }

    tasks.named<Test>("jvmTest"){
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


internal fun KotlinMultiplatformExtension.configureSourceSetsConvention() {
    explicitApi()
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
