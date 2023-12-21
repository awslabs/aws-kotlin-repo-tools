/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.gradle.codegen

import aws.sdk.kotlin.gradle.codegen.dsl.generateSmithyBuild
import aws.sdk.kotlin.gradle.codegen.dsl.generateSmithyProjections
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SmithyBuildPluginTest {
    @Test
    fun testTaskAndConfigCreation() {
        val testProj = ProjectBuilder.builder().build()
        testProj.apply<SmithyBuildPlugin>()

        assertNotNull(testProj.tasks.findByName("generateSmithyBuild"))
        assertNotNull(testProj.tasks.findByName("generateSmithyProjections"))
        assertNotNull(testProj.configurations.findByName("codegen"))

        val generateProjectionTaskDeps = testProj
            .tasks
            .generateSmithyProjections
            .get()
            .taskDependencies
            .getDependencies(null)
            .map { it.name }

        assertTrue(generateProjectionTaskDeps.contains("generateSmithyBuild"))
    }

    @Test
    fun testDslAndGeneratedBuild() {
        val testProj = ProjectBuilder.builder().build()
        testProj.apply<SmithyBuildPlugin>()
        testProj.extensions.configure<SmithyBuildExtension> {
            projections.create("foo")
        }

        testProj.tasks.generateSmithyBuild.get().generateSmithyBuild()
        val contents = testProj.tasks.generateSmithyBuild.get().smithyBuildConfig.get().asFile.readText()
        val expected = """
            {
                "version": "1.0",
                "projections": {
                    "foo": {
                        "sources": [],
                        "imports": [],
                        "transforms": []
                    }
                }
            }
        """.trimIndent()

        assertEquals(expected, contents)
    }
}
