package io.peekandpoke.klang.script.ksp

import com.google.auto.service.AutoService
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

/**
 * Provider for the KlangScript annotation processor.
 *
 * Registered via @AutoService for automatic discovery by KSP.
 */
@Suppress("unused")
@AutoService(SymbolProcessorProvider::class)
class KlangScriptProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return KlangScriptProcessor(environment)
    }
}
