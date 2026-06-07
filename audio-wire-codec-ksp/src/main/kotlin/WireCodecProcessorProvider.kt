package io.peekandpoke.klang.audio_bridge.ksp

import com.google.auto.service.AutoService
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

/**
 * Provider for the worklet wire-codec processor. Registered via @AutoService for KSP discovery.
 */
@Suppress("unused")
@AutoService(SymbolProcessorProvider::class)
class WireCodecProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return WireCodecProcessor(environment)
    }
}
