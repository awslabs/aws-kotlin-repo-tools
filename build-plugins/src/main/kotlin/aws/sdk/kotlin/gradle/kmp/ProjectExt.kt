/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.gradle.kmp

import org.gradle.api.Project
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.the
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import java.io.File
import java.util.*

/**
 * Allows configuration from parent projects subprojects/allprojects block when they haven't configured the KMP
 * plugin but the subproject has applied it. The extension is otherwise not visible.
 */
fun Project.kotlin(block: KotlinMultiplatformExtension.() -> Unit) {
    configure(block)
}
val Project.kotlin: KotlinMultiplatformExtension get() = the()

public fun <T> ExtraPropertiesExtension.getOrNull(name: String): T? {
    if (!has(name)) return null
    @Suppress("UNCHECKED_CAST")
    return get(name) as? T
}

/**
 * Attempts to load and merge all properties from:
 *  * `local.properties` file in the root project directory (project specific)
 *  * `local.properties` in the root project parent directory (workspace specific)
 *  * `~/.sdkdev/local.properties` in the user home directory (user specific)
 */
public fun Project.localProperties(): Map<String, Any> {
    val props = Properties()

    listOf(
        File(rootProject.projectDir, "local.properties"), // Project-specific local properties
        File(rootProject.projectDir.parent, "local.properties"), // Workspace-specific local properties
        File(System.getProperty("user.home"), ".sdkdev/local.properties"), // User-specific local properties
    )
        .filter(File::exists)
        .map(File::inputStream)
        .forEach(props::load)

    return props.mapKeys { (k, _) -> k.toString() }
}

/**
 * Convenience to get a property from the following (in order):
 * * project property
 * * property from [localProperties]
 * * property from extras
 * @return property if it exists or null
 */
public fun Project.prop(name: String): Any? =
    properties[name] ?: localProperties()[name] ?: extra[name]

inline fun <reified T> Project.typedProp(name: String): T? {
    val any = prop(name)

    return when (T::class) {
        String::class -> any?.toString() as? T
        Boolean::class -> any?.toString()?.toBoolean() as? T
        else -> error("unknown type ${T::class} for property $name")
    }
}
