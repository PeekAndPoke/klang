/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.feel

/**
 * The resolved Klang look: one value for every [KlangRole] (KLANG-00337).
 *
 * Made by [KlangLookLayers.resolve]; a role no layer sets takes the role it [KlangRole.follows], else its
 * [KlangRole.default].
 */
class KlangLook internal constructor(private val values: Map<KlangRole, String>) {

    /** The value of [role]. */
    operator fun get(role: KlangRole): String = values[role] ?: role.follows?.let { get(it) } ?: role.default

    /** Every role as its CSS variable and value, in the order of [KlangRole.entries]. */
    fun cssVars(): List<Pair<String, String>> = KlangRole.entries.map { it.cssVar to get(it) }

    override fun equals(other: Any?): Boolean = other is KlangLook && cssVars() == other.cssVars()

    override fun hashCode(): Int = cssVars().hashCode()

    override fun toString(): String = "KlangLook(${cssVars().joinToString { (k, v) -> "$k=$v" }})"

    companion object {
        /** Every role at its default. */
        val defaults: KlangLook = KlangLook(emptyMap())
    }
}
