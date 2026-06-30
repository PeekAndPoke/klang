/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.docs

import io.peekandpoke.klang.script.types.KlangCallable
import io.peekandpoke.klang.script.types.KlangProperty
import io.peekandpoke.klang.script.types.KlangSymbol
import io.peekandpoke.klang.script.types.KlangType
import io.peekandpoke.klang.script.types.putOrMerge

/**
 * Registry for KlangScript symbol documentation.
 *
 * Stores and queries [KlangSymbol] entries by name, category, library, or free-text search.
 */
class KlangDocsRegistry {

    companion object {
        /** Global shared docs registry. */
        val global = KlangDocsRegistry()
    }

    private val _symbols = mutableMapOf<String, KlangSymbol>()

    /** Snapshot of all registered symbols keyed by name. */
    val symbols: Map<String, KlangSymbol>
        get() = _symbols.toMap()

    /**
     * Register a single symbol.
     *
     * When a symbol with the same name already exists, variants are merged via
     * [KlangSymbol.mergeWith] so that symbols from multiple libraries
     * (e.g. stdlib and sprudel) coexist.
     */
    fun register(doc: KlangSymbol) {
        _symbols.putOrMerge(doc)
    }

    /**
     * Register multiple symbols from a list.
     *
     * @param docs Symbols to register
     */
    fun registerAll(docs: List<KlangSymbol>) {
        docs.forEach { register(it) }
    }

    /**
     * Register multiple symbols from a map.
     *
     * @param docs Map of symbol names to symbols
     */
    fun registerAll(docs: Map<String, KlangSymbol>) {
        docs.values.forEach { register(it) }
    }

    /** Remove all registered symbols. */
    fun clear() {
        _symbols.clear()
    }

    /** Create a snapshot copy of this registry. Mutations to the original do not affect the copy. */
    fun snapshot(): KlangDocsRegistry = KlangDocsRegistry().also { copy ->
        copy._symbols.putAll(_symbols)
    }

    /**
     * Look up a symbol by name.
     *
     * @param name The symbol name
     * @return The symbol, or null if not found
     */
    fun get(name: String): KlangSymbol? =
        _symbols[name]

    /** Sorted list of all registered symbol names. */
    val symbolNames: List<String>
        get() = _symbols.keys.sorted()

    /** Sorted distinct list of all categories present in the registry. */
    val categories: List<String>
        get() = _symbols.values.map { it.category }.distinct().sorted()

    /** Sorted distinct list of all non-empty library names present in the registry. */
    val libraries: List<String>
        get() = _symbols.values.mapNotNull { it.getLibrary()?.name }.filter { it.isNotEmpty() }.distinct().sorted()

    /**
     * Get all symbols belonging to a category, sorted by name.
     *
     * @param category The category to filter by
     * @return Matching symbols sorted by name
     */
    fun getByCategory(category: String): List<KlangSymbol> =
        _symbols.values.filter { it.category == category }.sortedBy { it.name }

    /**
     * Get all symbols belonging to a library, sorted by name.
     *
     * @param library The library name to filter by
     * @return Matching symbols sorted by name
     */
    fun getByLibrary(library: String): List<KlangSymbol> =
        _symbols.values.filter { it.getLibrary()?.name == library }.sortedBy { it.name }

    /**
     * Search symbols by free-text query.
     *
     * Matches against name, aliases, tags, category, and library (case-insensitive).
     *
     * @param query The search query
     * @return Matching symbols sorted by name
     */
    fun search(query: String): List<KlangSymbol> {
        val lowerQuery = query.lowercase()
        return _symbols.values.filter { symbol ->
            symbol.name.lowercase().contains(lowerQuery) ||
                    symbol.aliases.any { it.lowercase().contains(lowerQuery) } ||
                    symbol.tags.any { it.lowercase().contains(lowerQuery) } ||
                    symbol.category.lowercase().contains(lowerQuery) ||
                    (symbol.getLibrary()?.name?.lowercase()?.contains(lowerQuery) == true)
        }.sortedBy { it.name }
    }

    /**
     * Find a [KlangCallable] variant matching the given receiver type.
     *
     * Matches by FQCN when both sides carry one; otherwise falls back to
     * [KlangType.simpleName] equality. FQCN is the canonical cross-module key —
     * even when KSP can't see the `@Object` annotation on a class from another
     * module, the resolved FQCN matches.
     *
     * @param name Symbol name
     * @param receiverType The receiver type to match, or null for top-level functions
     * @return The matching callable, or null
     */
    fun getCallable(name: String, receiverType: KlangType?): KlangCallable? {
        val symbol = _symbols[name] ?: return null
        val callables = symbol.variants.filterIsInstance<KlangCallable>()
        // Try the receiver's own type first, then its supertypes (most-specific wins),
        // so an inherited base-type method (e.g. `IgnitorDsl.lowpass`) resolves on a
        // narrowed subtype receiver (e.g. `IgnitorDsl.SuperSaw`).
        for (candidate in receiverChain(receiverType)) {
            callables.firstOrNull { typeMatches(it.receiver, candidate) }?.let { return it }
        }
        return null
    }

    /**
     * Get all symbols that have at least one variant with the given receiver type.
     * Used for code completion after `.` — returns all methods available on a type.
     *
     * @param receiverType The receiver type to filter by
     * @return Matching symbols sorted by name
     */
    fun getVariantsForReceiver(receiverType: KlangType): List<KlangSymbol> {
        val chain = receiverChain(receiverType)
        return _symbols.values.filter { symbol ->
            symbol.variants.any { variant ->
                val owner = when (variant) {
                    is KlangCallable -> variant.receiver
                    is KlangProperty -> variant.owner
                }
                chain.any { typeMatches(owner, it) }
            }
        }.sortedBy { it.name }
    }

    /**
     * Look up a symbol and filter its variants to those matching the receiver type.
     * Used for hover docs when the receiver context is known.
     *
     * Strict: returns `null` when the symbol exists but no variant matches the
     * receiver — callers should NOT fall back to the unfiltered symbol, otherwise
     * unrelated DSL variants (e.g. sprudel `String.distort`) leak into the popup
     * for a receiver they don't apply to.
     *
     * @param name Symbol name
     * @param receiverType The receiver type to filter by, or null to return all variants
     * @return The filtered symbol, or null if the name is not found or no variant matches
     */
    fun getSymbolWithReceiver(name: String, receiverType: KlangType?): KlangSymbol? {
        val symbol = _symbols[name] ?: return null
        if (receiverType == null) return symbol
        val chain = receiverChain(receiverType)
        val filtered = symbol.variants.filter { variant ->
            val owner = when (variant) {
                is KlangCallable -> variant.receiver
                is KlangProperty -> variant.owner
            }
            chain.any { typeMatches(owner, it) }
        }
        if (filtered.isEmpty()) return null
        // Promote the filtered variant's library into the symbol's origin so the
        // popup chip shows the right library (e.g. "STDLIB") instead of the merged
        // symbol's original origin (which would still point to whichever library
        // registered first, before the merge).
        val variantLibrary = filtered.firstOrNull()?.library
        val newOrigin = when {
            !variantLibrary.isNullOrBlank() -> KlangSymbol.Origin.Library(variantLibrary)
            else -> symbol.origin
        }
        return symbol.copy(variants = filtered, origin = newOrigin)
    }

    /**
     * The query type followed by its (script-registered) supertypes, most-specific
     * first. Receiver-matched lookups walk this chain so a narrowed subtype receiver
     * falls through to the base type that actually declares an inherited method
     * (e.g. `IgnitorDsl.SuperSaw` → `IgnitorDsl`). A null query yields a single null
     * element (top-level lookup).
     *
     * [KlangType.supertypes] is populated by KSP only for inferred return/property
     * types; hand-built query types carry none, so this collapses to `[query]` and
     * matching behaves exactly as before.
     */
    private fun receiverChain(query: KlangType?): List<KlangType?> =
        if (query == null) listOf(null) else listOf(query) + query.supertypes

    /**
     * Match a registered owner type against a query type.
     *
     * FQCN is the canonical cross-module identity key — when both sides carry
     * one, that's the only field consulted. Otherwise falls back to
     * [KlangType.simpleName]. Null query = top-level (owner must also be null).
     */
    private fun typeMatches(owner: KlangType?, query: KlangType?): Boolean {
        if (query == null) return owner == null
        if (owner == null) return false
        val ownerFqcn = owner.fqcn
        val queryFqcn = query.fqcn
        if (ownerFqcn != null && queryFqcn != null) {
            return ownerFqcn == queryFqcn
        }
        return owner.simpleName == query.simpleName
    }
}
