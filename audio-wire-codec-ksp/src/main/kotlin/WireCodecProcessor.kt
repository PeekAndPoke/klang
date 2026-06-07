package io.peekandpoke.klang.audio_bridge.ksp

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration

/**
 * Generates the "trust the input" JS-object wire codec for `@WireFormat`-annotated types and their transitive
 * graph (see `docs/tasks/worklet-codec-ksp.md`).
 *
 * PHASE 1: discovery only — finds the annotated roots and logs them, no code generation yet. This verifies KSP
 * runs on `audio_bridge`'s **JS** target (the `kspJs` wiring) before any generator logic is written.
 */
class WireCodecProcessor(
    environment: SymbolProcessorEnvironment,
) : SymbolProcessor {

    private val logger: KSPLogger = environment.logger

    companion object {
        private const val ANN_WIRE_FORMAT = "io.peekandpoke.klang.audio_bridge.WireFormat"
    }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val roots = resolver.getSymbolsWithAnnotation(ANN_WIRE_FORMAT)
            .filterIsInstance<KSClassDeclaration>()
            .toList()

        // logger.warn so it surfaces in normal Gradle output during Phase-1 verification.
        logger.warn(
            "[wire-codec] @WireFormat roots (${roots.size}): " +
                    roots.joinToString { it.qualifiedName?.asString() ?: it.simpleName.asString() }
        )

        return emptyList()
    }
}
