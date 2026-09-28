/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

/**
 * The LFO waveform catalogue: every shape the tremolo can take (`tremolo(rate, depth, x => x.shape("square"))`
 * on the Ignitor, `tremolo(shape = "square")` on a pattern), as a closed, ordered list whose POSITION the
 * Ignitor `Tremolo` node carries as a knob (phase 3 step 3b, 2026-09-25).
 *
 * The vocabulary is the OSCILLATOR vocabulary on purpose (see the backend's `LfoShape`): no LFO-only
 * names. An unknown name and a bad index are both [SINE_INDEX], the shipped tremolo.
 */
object LfoShapes {

    /**
     * The canonical names, in their index order. **Append only, never reorder**: see
     * [DistortionShapes.names]. The order is the backend enum's (`LfoShape`), pinned by a spec.
     */
    val names: List<String> = listOf("sine", "triangle", "square", "sawtooth", "ramp")

    /** The alternative spellings, each onto its canonical name, mirroring the Ignitor registry's aliases. */
    val aliases: Map<String, String> = mapOf(
        "sin" to "sine",
        "tri" to "triangle",
        "sqr" to "square",
        "pulse" to "square",
        "saw" to "sawtooth",
    )

    /** The index of `sine`, the fallback for an unknown name and for a bad index. */
    const val SINE_INDEX: Int = 0

    /** Every name and alias onto its index, built once. */
    private val indexByName: Map<String, Int> = buildCatalogueIndex(names, aliases)

    /**
     * The INDEX of an LFO shape name: its position in [names], its canonical name's for an alias,
     * or [SINE_INDEX] for an unknown name or none at all. Case-insensitive.
     */
    fun indexOf(shape: String?): Double {
        if (shape == null) {
            return SINE_INDEX.toDouble()
        }

        return (indexByName[shape.lowercase()] ?: SINE_INDEX).toDouble()
    }

    /** The position in [names] a knob value selects, or [SINE_INDEX]. See [catalogueIndexAt]. */
    fun indexAt(index: Double): Int = catalogueIndexAt(index, names.size, SINE_INDEX)
}
