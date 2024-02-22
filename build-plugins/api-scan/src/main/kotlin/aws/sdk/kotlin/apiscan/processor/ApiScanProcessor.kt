/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.apiscan.processor

import com.google.devtools.ksp.closestClassDeclaration
import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.visitor.KSTopDownVisitor

class ApiScanProcessor(private val env: SymbolProcessorEnvironment) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val owners = mutableListOf<Owner>()

        resolver.getAllFiles().forEach { file ->
            val scanner = Scanner()
            file.accept(scanner, Unit)
            val errorLocations = scanner.finalize()
            if (errorLocations.isNotEmpty()) {
                owners += Owner("In ${file.filePath}:", errorLocations)
            }
        }

        if (owners.isNotEmpty()) {
            log(owners)
            throw RuntimeException("Errors found during API scan")
        }

        return listOf()
    }

    private inner class Scanner : KSTopDownVisitor<Unit, Unit>() {
        private val locations = mutableListOf<Location>()

        override fun defaultHandler(node: KSNode, data: Unit) = data

        override fun visitFunctionDeclaration(function: KSFunctionDeclaration, data: Unit) {
            super.visitFunctionDeclaration(function, data)
            if (isPublic(function)) {
                val errors = exposedInternals(function)
                if (errors.isNotEmpty()) {
                    locations += Location("Function ${function.bestName} exposes internal types", errors)
                }
            }
        }

        private fun isPublic(declaration: KSDeclaration): Boolean =
            declaration.getVisibility() == Visibility.PUBLIC && declaration.annotations.none { it.isInternal } && (
                declaration.parentDeclaration?.let(::isPublic) ?: true
            )

        private fun exposedInternals(function: KSFunctionDeclaration): List<Error> = buildList {
            function.parameters.forEach { p ->
                val t = p.type.resolve().declaration
                t.annotations.map { it.shortName.getShortName() }.filter { "Internal" in it }.forEach { a ->
                    val paramName = p.name?.getShortName()
                    val typeName = t.simpleName.getShortName()
                    add(Error("Parameter `$paramName` type `$typeName` is annotated @$a"))
                }
            }

            function.returnType?.let { tRef ->
                val t = tRef.resolve().declaration
                t.annotations.map { it.shortName.getShortName() }.filter { "Internal" in it }.forEach { a ->
                    val typeName = t.simpleName.getShortName()
                    add(Error("Return type $typeName is annotated @$a"))
                }
            }
        }

        private val KSFunctionDeclaration.bestName: String
            get() {
                val qualifiedName = qualifiedName?.asString()
                if (qualifiedName != null) return qualifiedName

                val fName = simpleName.asString()

                val containingClass = closestClassDeclaration()?.let { c -> c.qualifiedName?.asString() }
                if (containingClass != null) return "$containingClass.$fName"

                val file = this.containingFile?.filePath?.let { "$fName in $it" }
                if (file != null) return file

                error("Unknown name for function $this")
            }

        private val KSAnnotation.isInternal: Boolean
            get() = "Internal" in shortName.asString()

        fun finalize(): List<Location> = locations.toList()
    }

    private fun log(errorOwners: List<Owner>) {
        val msg = errorOwners.joinToString("\n") { o ->
            "${o.name}\n" + o.locations.joinToString("\n") { l ->
                "  ${l.name}\n" + l.errors.joinToString("\n") { e ->
                    "    ${e.value}"
                }
            }
        }
        env.logger.error(msg)
    }
}

data class Owner(val name: String, val locations: List<Location>)

data class Location(val name: String, val errors: List<Error>)

@JvmInline
value class Error(val value: String)
