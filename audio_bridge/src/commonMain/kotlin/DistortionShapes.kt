/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

import kotlin.math.round

/**
 * The waveshaper catalogue: every transfer function `shape(...)` and `distort(..., shape, ...)` can
 * name, as a closed, ordered list, so a shape can travel as the INDEX of its position and the Ignitor
 * `Shape` and `Distort` nodes carry it as a knob (phase 3 step 3b, 2026-09-25, the way
 * [BodyMaterials] does for a body material since Katalyst step 5a-2).
 *
 * Lives in `audio_bridge` so that every reader of a shape NAME reaches the same table: both Ignitor
 * doors (the Kotlin door here, the script door in `klangscript-libs`), and the backend, whose
 * `parseDistortionShape` (the voice strip) and node build (the Ignitor) both resolve through
 * [indexOf] and [indexAt]. The DSP of each shape lives in the backend (`DistortionShape`,
 * `applyDistortionShape`); this file knows names and positions only.
 *
 * **An unknown name and a bad index are both [SOFT_INDEX]** (`soft`, the tanh), which is what an
 * unknown name has always rendered on both hosts. Nothing here throws: a name is user input.
 */
object DistortionShapes {

    /**
     * The canonical names, in their index order.
     *
     * **Append only, never reorder.** A name's POSITION is the value the `shape` knob carries, so it
     * is the wire encoding of a shape. The order is the backend enum's (`DistortionShape`), and a
     * spec in `audio_be` pins that the two agree position by position.
     */
    val names: List<String> = listOf(
        "soft", "hard", "gentle", "cubic", "diode", "fold", "chebyshev", "rectify", "exp",
        "softsat", "tube", "linearfold", "zerosquare", "sineshaper", "asym", "stompbox",
    )

    /** The alternative spellings, each onto its canonical name. The ones the backend has always accepted. */
    val aliases: Map<String, String> = mapOf(
        "soft_sat" to "softsat",
        "linear_fold" to "linearfold",
        "lfold" to "linearfold",
        "zero_square" to "zerosquare",
        "square" to "zerosquare",
        "sine_shaper" to "sineshaper",
        "sshape" to "sineshaper",
        "stomp_box" to "stompbox",
        "stomp" to "stompbox",
    )

    /** The index of `soft`, the fallback for an unknown name and for a bad index. */
    const val SOFT_INDEX: Int = 0

    /** Every name and alias onto its index, built once: the lookup is on the note path. */
    private val indexByName: Map<String, Int> = buildCatalogueIndex(names, aliases)

    /**
     * The INDEX of a shape name, for the `shape` knob: its position in [names], the position of its
     * canonical name for an alias, or [SOFT_INDEX] for an unknown one. Case-insensitive.
     */
    fun indexOf(shape: String): Double = (indexByName[shape.lowercase()] ?: SOFT_INDEX).toDouble()

    /**
     * The position in [names] a knob value selects, or [SOFT_INDEX] when it selects none. See
     * [catalogueIndexAt] for the rounding and the fallbacks.
     */
    fun indexAt(index: Double): Int = catalogueIndexAt(index, names.size, SOFT_INDEX)
}

/** Name and alias to position, for a catalogue whose aliases all name one of its [names]. */
internal fun buildCatalogueIndex(names: List<String>, aliases: Map<String, String>): Map<String, Int> {
    val byName = names.withIndex().associate { (i, name) -> name to i }

    return byName + aliases.mapValues { (_, canonical) -> byName.getValue(canonical) }
}

/**
 * The one resolution rule of a shape knob: the nearest position (a TIE rounds to the even one, which
 * is what `kotlin.math.round` does on both platforms, the [BodyMaterials.modesAt] rule), and
 * [fallback] for a non-finite value, a negative position, or one past the end. So a knob that arrives
 * as 2.0 and one that arrives as 1.999 are the same shape, and no value can select nothing.
 */
internal fun catalogueIndexAt(index: Double, size: Int, fallback: Int): Int {
    // NaN-guard on a value the author can write: a non-finite index was never set.
    if (!index.isFinite()) {
        return fallback
    }

    val rounded = round(index)

    if (rounded < 0.0 || rounded >= size.toDouble()) {
        return fallback
    }

    return rounded.toInt()
}
