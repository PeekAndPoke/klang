/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.types

/**
 * Represents a type in the KlangScript type documentation system.
 *
 * @param simpleName The script-facing display name (e.g., "Osc", "OscSlot", "String").
 *   For `@KlangScript.Object`-bound classes this is the script name, not the Kotlin
 *   simple class name; for primitives / `RuntimeValue` subtypes this is the canonical
 *   script display name (e.g. `"Number"`, `"String"`).
 * @param fqcn The fully-qualified Kotlin class name of the underlying type, when
 *   known (e.g. `"io.peekandpoke.klang.script.stdlib.KlangScriptOscSlot"`). Null
 *   for primitives and types that don't map to a single Kotlin declaration (e.g.
 *   unions). Cross-module references use this as the canonical identity key.
 * @param isTypeAlias Whether this type is an alias for another type
 * @param isNullable Whether this type is nullable
 * @param unionMembers Members of a union type (e.g., String | Number), or null if not a union
 * @param supertypes Transitive script-registered supertypes of this type (`kotlin.*`
 *   ancestors excluded), emitted by KSP for return/property types. Lets the static
 *   type-inferrer resolve a base-type method on a narrowed subtype receiver — e.g.
 *   `Osc.supersaw()` returns `IgnitorDsl.SuperSaw`, yet `.lowpass()`/`.adsr()` are
 *   registered on `IgnitorDsl`. Mirrors the runtime's reflective supertype walk
 *   (`Environment.getAllRegisteredSupertypes`). Empty for primitives and types built
 *   by hand (e.g. in tests), so receiver matching is unchanged for those.
 */
data class KlangType(
    val simpleName: String,
    val fqcn: String? = null,
    val isTypeAlias: Boolean = false,
    val isNullable: Boolean = false,
    val unionMembers: List<KlangType>? = null,
    val supertypes: List<KlangType> = emptyList(),
) {
    /** Whether this type is a union type. */
    val isUnion: Boolean get() = !unionMembers.isNullOrEmpty()

    /**
     * Render this type as a display string.
     *
     * @return The rendered type name with nullable suffix if applicable
     */
    fun render(): String = buildString {
        append(simpleName)
        if (isNullable) append("?")
    }

    override fun toString(): String = render()
}
