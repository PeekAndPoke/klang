package io.peekandpoke.klang.script.types

/**
 * A documented KlangScript symbol (function, method, property, etc.).
 *
 * @param name Display name of the symbol
 * @param variants List of callable/property declarations (overloads)
 * @param category Grouping category (e.g., "pattern", "effect")
 * @param tags Searchable tags for discovery
 * @param library The library this symbol belongs to (empty string for globals)
 * @param aliases Alternative names this symbol is known by
 */
data class KlangSymbol(
    val name: String,
    val variants: List<KlangDecl>,
    val category: String,
    val tags: List<String> = emptyList(),
    val library: String = "",
    val aliases: List<String> = emptyList(),
)
