package io.peekandpoke.klang.codemirror

import de.peekandpoke.ultra.common.cache.fastCache
import io.peekandpoke.klang.script.KlangScriptLibrary
import io.peekandpoke.klang.script.ast.ImportStatement
import io.peekandpoke.klang.script.docs.KlangDocsRegistry
import io.peekandpoke.klang.script.parser.KlangScriptParser
import io.peekandpoke.klang.script.types.KlangSymbol
import kotlinx.browser.window

/**
 * Import-aware documentation context for the CodeMirror editor.
 *
 * Parses import statements from the editor code (debounced) and builds a merged
 * [KlangDocsRegistry] from the imported libraries. Both hover docs and code
 * completion query this context.
 */
class EditorDocContext(
    availableLibraries: List<KlangScriptLibrary>,
    private val debounceMs: Int = 300,
) {
    private val libsByName = availableLibraries.associateBy { it.name }
    private val activeRegistry = KlangDocsRegistry()
    private var lastImports: Set<String> = emptySet()
    private var debounceTimer: Int? = null

    /** Cache: code string → set of imported library names. */
    private val importCache = fastCache<String, Set<String>> {
        maxEntries(100)
    }

    /** Look up a symbol by name in the active (import-based) registry. */
    fun docProvider(name: String): KlangSymbol? = activeRegistry.get(name)

    /** The active registry for code completion queries. */
    val registry: KlangDocsRegistry get() = activeRegistry

    /** All available library names (for import statement completion). */
    val availableLibraryNames: Set<String> get() = libsByName.keys

    /**
     * Called on every code change. Debounces and then re-parses imports.
     */
    fun onCodeChanged(code: String) {
        debounceTimer?.let { window.clearTimeout(it) }
        debounceTimer = window.setTimeout({
            processCode(code)
        }, debounceMs)
    }

    /**
     * Immediately process code without debounce (e.g., on initial load).
     */
    fun processCodeImmediate(code: String) {
        processCode(code)
    }

    private fun processCode(code: String) {
        val imports = importCache.getOrPut(code) { extractImports(code) }

        if (imports == lastImports) return
        lastImports = imports
        rebuildRegistry(imports)
    }

    private fun extractImports(code: String): Set<String> {
        return try {
            val program = KlangScriptParser.parse(code)
            program.statements
                .filterIsInstance<ImportStatement>()
                .map { it.libraryName }
                .toSet()
        } catch (_: Throwable) {
            lastImports  // keep previous on parse error
        }
    }

    private fun rebuildRegistry(imports: Set<String>) {
        activeRegistry.clear()
        for (libName in imports) {
            val lib = libsByName[libName] ?: continue
            activeRegistry.registerAll(lib.docs.symbols)
        }
    }
}
