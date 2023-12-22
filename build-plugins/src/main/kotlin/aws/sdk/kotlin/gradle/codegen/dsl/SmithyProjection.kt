/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.gradle.codegen.dsl

import software.amazon.smithy.model.node.ArrayNode
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.node.ObjectNode
import java.io.Serializable

/**
 * A container for settings related to a single Smithy projection.
 *
 * See https://awslabs.github.io/smithy/1.0/guides/building-models/build-config.html#projections
 *
 * @param name the name of the projection
 */
class SmithyProjection(val name: String) : Serializable {
    // NOTE: We implement Serializable because this is used in a task input property and is required by Gradle when used that way
    companion object {
        private val serialVersionUID: Long = 1L
    }

    /**
     * List of files/directories that contain models that are considered sources models of the build.
     */
    var sources: List<String> = emptyList()

    /**
     * List of files/directories to import when building the projection
     */
    var imports: List<String> = emptyList()

    /**
     * A list of transforms to apply
     *
     * See https://awslabs.github.io/smithy/1.0/guides/building-models/build-config.html#transforms
     */
    var transforms: List<String> = emptyList()

    /**
     * Plugin name to plugin settings. Plugins should provide an extension function to configure their own plugin settings
     */
    val plugins: MutableMap<String, SmithyBuildPluginSettings> = mutableMapOf()

    internal fun toNode(): Node {
        // escape windows paths for valid json
        val formattedImports = imports.map { it.replace("\\", "\\\\") }
        val formattedSources = sources.map { it.replace("\\", "\\\\") }

        val transformNodes = transforms.map { Node.parse(it) }
        val obj = ObjectNode.objectNodeBuilder()
            .withArrayMember("sources", formattedSources)
            .withArrayMember("imports", formattedImports)
            .withMember("transforms", ArrayNode.fromNodes(transformNodes))

        if (plugins.isNotEmpty()) {
            obj.withObjectMember("plugins") {
                plugins.forEach { (pluginName, pluginSettings) ->
                    withMember(pluginName, pluginSettings.toNode())
                }
            }
        }
        return obj.build()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SmithyProjection

        if (name != other.name) return false
        if (sources != other.sources) return false
        if (imports != other.imports) return false
        if (transforms != other.transforms) return false
        if (plugins != other.plugins) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + imports.hashCode()
        result = 31 * result + sources.hashCode()
        result = 31 * result + transforms.hashCode()
        result = 31 * result + plugins.hashCode()
        return result
    }

    // /**
    //  * Always treat de-serialization as a full-blown constructor, by
    //  * validating the final state of the de-serialized object.
    //  */
    // private fun readObject(inputStream: ObjectInputStream) {
    //     // always perform the default de-serialization first
    //     inputStream.defaultReadObject();
    // }
    //
    // /**
    //  * This is the default implementation of writeObject.
    //  * Customise if necessary.
    //  */
    // private fun writeObject( outputStream: ObjectOutputStream): Unit {
    //     // perform the default serialization for all non-transient, non-static fields
    //     outputStream.defaultWriteObject();
    // }
}
