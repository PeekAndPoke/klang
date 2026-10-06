/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.types

import io.peekandpoke.klang.script.annotations.KlangScope
import io.peekandpoke.klang.script.docs.typeMatches

/**
 * A documented KlangScript symbol (function, method, property, etc.).
 *
 * @param name Display name of the symbol
 * @param variants List of callable/property declarations (overloads)
 * @param category Grouping category (e.g., "pattern", "effect")
 * @param tags Searchable tags for discovery
 * @param origin Where this symbol originates — a registered library, a local binding, or `null` when unknown
 * @param aliases Alternative names this symbol is known by
 * @param scope Where the thing runs: per voice, on the orbit bus, or on the master. `null` when it is not
 *   an audio setting at all (structure, pattern maths, sources).
 */
data class KlangSymbol(
    val name: String,
    val variants: List<KlangDecl>,
    val category: String,
    val tags: List<String> = emptyList(),
    val origin: Origin? = null,
    val aliases: List<String> = emptyList(),
    val scope: KlangScope? = null,
) {
    /**
     * Origin of a [KlangSymbol]. A library-registered symbol carries the library's name;
     * a script-local binding (let / const / export / function declaration) carries [Local].
     *
     * The owning [KlangSymbol.origin] is nullable — `null` means "we don't know" (e.g. a
     * test fixture or a synthesized symbol that hasn't been classified). Don't paper over
     * unknown origin with a fake `Library("")`.
     */
    sealed interface Origin {
        /** Registered via a [io.peekandpoke.klang.script.KlangScriptLibrary]. */
        data class Library(val name: String) : Origin

        /** Locally declared inside a KlangScript program (let / const / export / arrow parameter). */
        data class Local(val kind: LocalKind) : Origin
    }

    /** The declaration form that introduced a [Origin.Local] symbol — drives the popup chip label. */
    enum class LocalKind(val display: String) {
        LET("LET"),
        CONST("CONST"),
        EXPORT("EXPORT"),
        PARAM("PARAM"),
    }

    /** Convenience accessor — returns the library origin, or null when origin is unknown or [Origin.Local]. */
    fun getLibrary(): Origin.Library? = origin as? Origin.Library

    /**
     * The call form of a callable object (`perlin(from, to)`, `Katalyst(k => ...)`), or null when this
     * symbol is no callable object. KSP puts it next to the object itself on the object's own symbol:
     * the object is a top-level [KlangProperty], the call form a receiver-less [KlangCallable] of the
     * same name and library.
     */
    val callForm: KlangCallable?
        get() {
            val objects = variants.filterIsInstance<KlangProperty>().filter { it.owner == null && it.name == name }

            return variants.filterIsInstance<KlangCallable>().firstOrNull { callable ->
                callable.receiver == null && callable.name == name && objects.any { it.library == callable.library }
            }
        }

    /**
     * The call form ([callForm]) when this symbol is the object whose type [type] is: its top-level
     * property's type matches [type] (FQCN when both carry one, as the registry matches receivers).
     * A same-named symbol that is not that object (a function `Number` for a value of type `Number`)
     * gives null.
     */
    fun callFormOf(type: KlangType): KlangCallable? = if (holdsObjectOf(type)) callForm else null

    /** True when a top-level property of this symbol has the type [type] (FQCN when both carry one). */
    fun holdsObjectOf(type: KlangType): Boolean =
        variants.any { it is KlangProperty && it.owner == null && typeMatches(it.type, type) }

    /**
     * The callable variant an argument belongs to when the call's receiver type is unknown, as the
     * editor's param tools see it (`x => x.body(material = "oak")`: nothing types `x`).
     *
     * One rule, in variant order (library registration order, then each library's declaration
     * order): the first variant whose parameter for this argument ([KlangCallable.paramForArgument])
     * declares ui tools wins; with none, the first callable variant. Several libraries share a door
     * name (the Katalyst `body` and sprudel's `body`), and only the pattern language declares tools,
     * so taking the first variant blindly found the tool-less one.
     *
     * Known asymmetry: an untyped receiver is assumed to be a pattern, since the tools live there.
     * On an untyped Ignitor or Katalyst call this may pick a variant the call does not belong to;
     * `bindArgument` then reports the binding as not safe for a whole-call rewrite. A callable object's
     * [callForm] is never a member, so never a candidate here.
     */
    fun callableForArgument(argIndex: Int, argName: String?, functionArgs: List<Boolean>): KlangCallable? {
        val ownCallForm = callForm
        val callables = variants.filterIsInstance<KlangCallable>().filter { it != ownCallForm }

        return callables.firstOrNull { it.paramForArgument(argIndex, argName, functionArgs)?.uitools?.isNotEmpty() == true }
            ?: callables.firstOrNull()
    }

    /**
     * Merge another [KlangSymbol] of the same name into this one.
     *
     * Variants are concatenated and deduplicated by `(kind, name, receiver/owner.simpleName, library)`,
     * so two libraries' variants for the same script name co-exist (e.g. stdlib's
     * `Math.abs` + sprudel's top-level `abs`), and a callable object's two variants (the object and
     * its call form, both top-level and named alike) co-exist, while a re-registration from the same
     * source doesn't double up.
     *
     * Used by both [io.peekandpoke.klang.script.docs.KlangDocsRegistry.register] and by
     * KSP-generated `buildMap` composition so member-property variants survive
     * same-name overwrites from chunk maps.
     */
    fun mergeWith(other: KlangSymbol): KlangSymbol {
        val merged = (variants + other.variants).distinctBy { variant ->
            when (variant) {
                is KlangCallable -> listOf("callable", variant.name, variant.receiver?.simpleName, variant.library)
                is KlangProperty -> listOf("property", variant.name, variant.owner?.simpleName, variant.library)
            }
        }
        return copy(
            variants = merged,
            tags = (tags + other.tags).distinct(),
            aliases = (aliases + other.aliases).distinct(),
            // Same rule as `category`: this one wins, the other only fills a gap
            scope = scope ?: other.scope,
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
