package aws.sdk.kotlin.gradle.kmp

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Implements shared configuration logic for Kotlin Multiplatform Projects (KMP).
 */
class KmpDefaultsPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        println("applying kmp defaults to $target")
        target.configureKmpTargets()
    }
}
