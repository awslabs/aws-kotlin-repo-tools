package aws.sdk.kotlin.gradle.kmp

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Implements shared configuration logic for Kotlin Multiplatform Projects (KMP).
 *
 * NOTE: This plugin is meant to be applied at top level and will configure subprojects directly if/when
 * they apply the multiplatform plugin.
 */
class KmpDefaultsPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.logger.info("applying kmp defaults plugin to $target")
        target.configureKmpTargets()
    }
}
