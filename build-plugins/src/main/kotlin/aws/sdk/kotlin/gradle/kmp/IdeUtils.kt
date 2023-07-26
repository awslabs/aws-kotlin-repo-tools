package aws.sdk.kotlin.gradle.kmp

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.konan.target.HostManager

/**
 * Whether Intellij is active or not
 */
val IDEA_ACTIVE = System.getProperty("idea.active") == "true"

val OS_NAME = System.getProperty("os.name").toLowerCase()

val HOST_NAME = when {
    OS_NAME.startsWith("linux") -> "linux"
    OS_NAME.startsWith("windows") -> "windows"
    OS_NAME.startsWith("mac") -> "macos"
    else -> error("Unknown os name `$OS_NAME`")
}

// TODO - enable real logic when ready to add additional targets
// val Project.COMMON_JVM_ONLY get() = IDEA_ACTIVE && properties["aws.kotlin.ide.jvmAndCommonOnly"] == "true"
val Project.COMMON_JVM_ONLY get() = true


/**
 * Scope down the native target enabled when working in intellij
 */
val KotlinMultiplatformExtension.ideaTarget: KotlinNativeTarget
    get() = when(HostManager.host) {
        is KonanTarget.LINUX_X64 -> linuxX64()
        is KonanTarget.LINUX_ARM64 -> linuxArm64()
        is KonanTarget.MACOS_X64 -> macosX64()
        is KonanTarget.MACOS_ARM64 -> macosArm64()
        is KonanTarget.MINGW_X64 -> mingwX64()
        else -> error("Unsupported target ${HostManager.host}")
    }
