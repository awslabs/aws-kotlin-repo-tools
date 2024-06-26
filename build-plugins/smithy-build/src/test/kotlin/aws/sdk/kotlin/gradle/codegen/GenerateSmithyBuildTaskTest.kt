/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.gradle.codegen

import aws.sdk.kotlin.gradle.codegen.dsl.SmithyBuildPluginSettings
import aws.sdk.kotlin.gradle.codegen.dsl.SmithyProjection
import aws.sdk.kotlin.gradle.codegen.tasks.GenerateSmithyBuild
import aws.sdk.kotlin.gradle.codegen.tasks.json
import org.gradle.kotlin.dsl.create
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import software.amazon.smithy.model.node.Node
import java.io.*

class GenerateSmithyBuildTaskTest {
    @Test
    fun testDefaults() {
        val testProj = ProjectBuilder.builder().build()
        val task = testProj.tasks.create<GenerateSmithyBuild>("generateSmithyBuild")
        assertEquals(task.generatedOutput.get().asFile.path, testProj.layout.buildDirectory.file("smithy-build.json").get().asFile.path)
    }

    @Test
    fun testGeneratedBuild() {
        val testProj = ProjectBuilder.builder().build()
        val testPlugin = object : SmithyBuildPluginSettings {
            override val pluginName: String = "plugin1"

            override fun toNode(): Node = Node.objectNodeBuilder()
                .withMember("key1", "value1")
                .build()
        }

        val smithyProjections = listOf(
            SmithyProjection("foo").apply {
                imports = listOf("i1")
                sources = listOf("s1")
                transforms = listOf("""{ "key": "value" }""")
                plugins["plugin1"] = testPlugin
            },
        )
        val task = testProj.tasks.create<GenerateSmithyBuild>("generateSmithyBuild") {
            smithyBuildConfig.set(smithyProjections.json)
        }

        task.generateSmithyBuild()
        assertTrue(task.generatedOutput.get().asFile.exists())
        val contents = task
            .generatedOutput
            .get()
            .asFile
            .readText()
            .replace("\r\n", "\n") // For windows
        val expected = """
            {
                "version": "1.0",
                "projections": {
                    "foo": {
                        "sources": [
                            "s1"
                        ],
                        "imports": [
                            "i1"
                        ],
                        "transforms": [
                            {
                                "key": "value"
                            }
                        ],
                        "plugins": {
                            "plugin1": {
                                "key1": "value1"
                            }
                        }
                    }
                }
            }
        """.trimIndent()
        assertEquals(expected, contents)
    }
}
