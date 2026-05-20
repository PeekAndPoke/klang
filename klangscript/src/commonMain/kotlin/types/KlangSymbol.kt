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
) {
    /**
     * Merge another [KlangSymbol] of the same name into this one.
     *
     * Variants are concatenated and deduplicated by `(name, receiver/owner.simpleName, library)`,
     * so two libraries' variants for the same script name co-exist (e.g. stdlib's
     * `Math.abs` + sprudel's top-level `abs`), while a re-registration from the same
     * source doesn't double up.
     *
     * Used by both [io.peekandpoke.klang.script.docs.KlangDocsRegistry.register] and by
     * KSP-generated `buildMap` composition so member-property variants survive
     * same-name overwrites from chunk maps.
     */
    fun mergeWith(other: KlangSymbol): KlangSymbol {
        val merged = (variants + other.variants).distinctBy { variant ->
            when (variant) {
                is KlangCallable -> Triple(variant.name, variant.receiver?.simpleName, variant.library)
                is KlangProperty -> Triple(variant.name, variant.owner?.simpleName, variant.library)
            }
        }
        return copy(
            variants = merged,
            tags = (tags + other.tags).distinct(),
            aliases = (aliases + other.aliases).distinct(),
        )
    }
}

/**
 * Merge [doc] into this mutable map under its [KlangSymbol.name].
 *
 * If a symbol of the same name already exists, [KlangSymbol.mergeWith] combines their
 * variants. Used by KSP-generated `buildMap` composition so chunked emissions don't
 * lose variants to overwrite.
 */
fun MutableMap<String, KlangSymbol>.putOrMerge(doc: KlangSymbol) {
    val existing = this[doc.name]
    this[doc.name] = existing?.mergeWith(doc) ?: doc
}
