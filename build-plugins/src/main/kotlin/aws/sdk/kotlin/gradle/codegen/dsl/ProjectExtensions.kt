/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.gradle.codegen.dsl

import aws.sdk.kotlin.gradle.codegen.tasks.GenerateSmithyBuild
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.named
import software.amazon.smithy.gradle.tasks.SmithyBuildTask

internal const val TASK_GENERATE_SMITHY_BUILD = "generateSmithyBuild"
internal const val TASK_GENERATE_SMITHY_PROJECTIONS = "generateSmithyProjections"

public val TaskContainer.generateSmithyBuild: TaskProvider<GenerateSmithyBuild>
    get() = named<GenerateSmithyBuild>(TASK_GENERATE_SMITHY_BUILD)

public val TaskContainer.generateSmithyProjections: TaskProvider<SmithyBuildTask>
    get() = named<SmithyBuildTask>(TASK_GENERATE_SMITHY_PROJECTIONS)
