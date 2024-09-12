/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.gradle.dokka.extensions

import org.jetbrains.dokka.base.transformers.documentables.SuppressedByConditionDocumentableFilterTransformer
import org.jetbrains.dokka.model.*
import org.jetbrains.dokka.model.properties.WithExtraProperties
import org.jetbrains.dokka.plugability.DokkaContext

/**
 * Filters out anything annotated with `InternalApi` or `InternalSdkApi`
 */
class FilterInternalApis(context: DokkaContext) : SuppressedByConditionDocumentableFilterTransformer(context) {
    override fun shouldBeSuppressed(d: Documentable): Boolean {
        val isInternal = when (d) {
            is DClass -> d.isInternal()
            is DObject -> d.isInternal()
            is DTypeAlias -> d.isInternal()
            is DFunction -> d.isInternal()
            is DProperty -> d.isInternal()
            is DEnum -> d.isInternal()
            is DEnumEntry -> d.isInternal()
            is DTypeParameter -> d.isInternal()
            else -> false
        }

        if (isInternal) context.logger.warn("Suppressing internal element '${d.name}'")

        return isInternal
    }
}

private val internalAnnotationNames = setOf("InternalApi", "InternalSdkApi")

private fun <T> T.isInternal() where T : WithExtraProperties<out Documentable> =
    extra[Annotations]?.directAnnotations.orEmpty().values.flatten().any { annotation ->
        annotation.dri.classNames in internalAnnotationNames
    }
