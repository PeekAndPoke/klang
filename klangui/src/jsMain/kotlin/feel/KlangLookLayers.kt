/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.feel

/**
 * The stack the Klang look is resolved from (KLANG-00337, KLANG-00334 items 26 to 29).
 *
 * The roles' defaults lie at the bottom; above them the [Layer]s in their declared order, a higher layer winning.
 * Clearing a layer restores what lies below it, so nothing a layer covers is lost. Immutable: every change returns
 * a new stack, and every value in it has passed [with]'s check: the constructor that takes a map is private.
 */
class KlangLookLayers private constructor(
    private val layers: Map<Layer, Map<KlangRole, String>>,
) {

    /** The empty stack: every role at its default. */
    constructor() : this(layers = emptyMap())

    /** The layers above the defaults, lowest first. */
    enum class Layer {
        /** The user's own theme, the one they edit and share. */
        User,

        /** A song's look, over the user's (prepared; nothing sets it yet). */
        Song,

        /** What a visual script sets while a song plays, KLANG-00336 (prepared; nothing sets it yet). */
        Script,
    }

    /**
     * This stack with [layer] holding [values], in place of what it held.
     *
     * A value its role does not accept ([KlangRole.accepts]) is dropped, so the role falls through to the layer
     * below: user input is coerced, never asserted.
     */
    fun with(layer: Layer, values: Map<KlangRole, String>): KlangLookLayers {
        val accepted = values.filter { (role, value) -> role.accepts(value) }

        return KlangLookLayers(layers = layers + (layer to accepted))
    }

    /** This stack with [layer] empty. */
    fun cleared(layer: Layer): KlangLookLayers = KlangLookLayers(layers = layers - layer)

    /** What [layer] holds, after the check. */
    operator fun get(layer: Layer): Map<KlangRole, String> = layers[layer] ?: emptyMap()

    /** Every role's value: the highest layer that sets it, else its default. */
    fun resolve(): KlangLook {
        val values = mutableMapOf<KlangRole, String>()

        for (layer in Layer.entries) {
            values.putAll(get(layer))
        }

        return KlangLook(values)
    }
}
