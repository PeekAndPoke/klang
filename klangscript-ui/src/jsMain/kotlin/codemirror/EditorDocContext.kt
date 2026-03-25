package io.peekandpoke.klang.ui.codemirror

import io.peekandpoke.klang.script.KlangScriptLibrary
import io.peekandpoke.klang.script.ast.AstIndex
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
    autoImportedLibraries: List<KlangScriptLibrary> = emptyList(),
    private val debounceMs: Int = 300,
) {
    private val libsByName = availableLibraries.associateBy { it.name }
    private val autoImportedNames = autoImportedLibraries.map { it.name }.toSet()
    private val activeRegistry = KlangDocsRegistry()
    private var lastImports: Set<String> = emptySet()
    private var debounceTimer: Int? = null

    init {
        // Load auto-imported library docs immediately
        if (autoImportedNames.isNotEmpty()) {
            rebuildRegistry(emptySet())
        }
    }

    /** Cached AST index from the last successful parse (stale on parse error, which is fine for hover). */
    var lastAstIndex: AstIndex? = null
        private set

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
        // Always parse to keep the AST index up-to-date for hover/tool badges
        val program = try {
            KlangScriptParser.parse(code)
        } catch (_: Throwable) {
            null // Keep lastAstIndex as-is (stale AST is better than none for hover)
        }

        if (program != null) {
            lastAstIndex = AstIndex.build(program, code)
        }

        val imports = if (program != null) {
            program.statements
                .filterIsInstance<ImportStatement>()
                .map { it.libraryName }
                .toSet()
        } else {
            lastImports
        }

        if (imports != lastImports) {
            lastImports = imports
            rebuildRegistry(imports)
        }
    }

    private fun rebuildRegistry(imports: Set<String>) {
        activeRegistry.clear()
        for (libName in autoImportedNames + imports) {
            val lib = libsByName[libName] ?: continue
            activeRegistry.registerAll(lib.docs.symbols)
        }
    }
}
