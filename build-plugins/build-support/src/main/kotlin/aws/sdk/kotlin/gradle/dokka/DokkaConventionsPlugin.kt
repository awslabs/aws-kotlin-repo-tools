/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.gradle.dokka

import aws.sdk.kotlin.gradle.dokka.extensions.FilterInternalApis
import aws.sdk.kotlin.gradle.dokka.extensions.NoOpSearchbarDataInstaller
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.plugability.DokkaPlugin
import org.jetbrains.dokka.plugability.DokkaPluginApiPreview
import org.jetbrains.dokka.plugability.PluginApiPreviewAcknowledgement

class DokkaConventionsPlugin : DokkaPlugin() {
    init {
        println("${this::class.qualifiedName} loaded!")
    }

    val dokkaBase by lazy { plugin<DokkaBase>() }

    val filterInternalApis by extending {
        dokkaBase.preMergeDocumentableTransformer providing ::FilterInternalApis
    }

    // FIXME Re-enable search once Dokka addresses performance issues
    // https://github.com/Kotlin/dokka/issues/2741
    val disableSearch by extending {
        dokkaBase.htmlPreprocessors providing ::NoOpSearchbarDataInstaller override dokkaBase.baseSearchbarDataInstaller
    }

    @DokkaPluginApiPreview
    override fun pluginApiPreviewAcknowledgement() = PluginApiPreviewAcknowledgement
}
