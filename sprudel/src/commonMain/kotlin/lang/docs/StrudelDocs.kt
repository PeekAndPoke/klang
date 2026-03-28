package io.peekandpoke.klang.sprudel.lang.docs

import io.peekandpoke.klang.script.docs.KlangDocsRegistry

/**
 * Registers all Sprudel DSL function documentation with the KlangScript documentation registry.
 *
 * This should be called during initialization to make Sprudel documentation available
 * for IDE completion, CodeMirror autocomplete, and documentation pages.
 *
 * @param registry The registry to register docs into (defaults to global registry)
 */
fun registerSprudelDocs(registry: KlangDocsRegistry = KlangDocsRegistry.global) {
    // Register auto-generated docs (all @SprudelDsl functions)
    registry.registerAll(generatedSprudelKlangSymbols)
}
