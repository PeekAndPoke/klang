package io.peekandpoke.klang.sprudel.ksp

import com.google.auto.service.AutoService
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

/**
 * Provider for the Sprudel documentation generator KSP processor.
 *
 * Registered via @AutoService for automatic discovery by KSP.
 */
@Suppress("unused")
@AutoService(SymbolProcessorProvider::class)
class SprudelDocsProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return SprudelDocsProcessor(environment)
    }
}
