package io.peekandpoke.klang.strudel.ksp

import com.google.auto.service.AutoService
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

/**
 * Provider for the Strudel documentation generator KSP processor.
 *
 * Registered via @AutoService for automatic discovery by KSP.
 */
@Suppress("unused")
@AutoService(SymbolProcessorProvider::class)
class StrudelDocsProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return StrudelDocsProcessor(environment)
    }
}
