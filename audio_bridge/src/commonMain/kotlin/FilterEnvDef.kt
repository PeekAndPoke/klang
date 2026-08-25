/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge


/**
 * Filter envelope for dynamic filter cutoff modulation.
 *
 * Uses ADSR envelope parameters plus a depth parameter to control the amount of modulation.
 * The depth is in SEMITONES (C3 of the filter unification): the sweep is pitch-linear, the
 * way DAW filter envelopes work, and negative depths sweep down with no dead zone.
 *
 * ```
 * newCutoff = baseCutoff × 2^(depth/12 × envelopeValue)
 * ```
 *
 * | depth | envValue | baseCutoff=500      | Result                  |
 * |-------|----------|---------------------|-------------------------|
 * | 12    | 1.0      | 500 × 2^(12/12)     | 1000 Hz (1 octave up)   |
 * | 24    | 1.0      | 500 × 2^(24/12)     | 2000 Hz (2 octaves up)  |
 * | 7     | 0.5      | 500 × 2^(3.5/12)    | 612 Hz (subtle)         |
 * | -12   | 1.0      | 500 × 2^(-12/12)    | 250 Hz (1 octave down)  |
 */
data class FilterEnvDef(
    /** Attack time in seconds - time to reach peak modulation */
    val attack: Double? = null,
    /** Decay time in seconds - time to reach sustain level */
    val decay: Double? = null,
    /** Sustain level (0.0 to 1.0) - level maintained during note hold */
    val sustain: Double? = null,
    /** Release time in seconds - time to return to baseline after note off */
    val release: Double? = null,
    /**
     * Modulation depth in SEMITONES (C3 of the filter unification): the sweep is pitch-linear,
     * `cutoff = base * 2^(depth/12 * env)`. +12 doubles the cutoff at full envelope, -12
     * halves it; negative depths are first-class (no dead zone). No upper bound.
     */
    val depth: Double? = null,
) {
    /**
     * Resolved envelope with all non-null values.
     */
    data class Resolved(
        val attack: Double,
        val decay: Double,
        val sustain: Double,
        val release: Double,
        val depth: Double,
    )

    /**
     * Merges this envelope with a fallback, using fallback values for any null fields.
     */
    fun mergeWith(fallback: FilterEnvDef): FilterEnvDef {
        return FilterEnvDef(
            attack = attack ?: fallback.attack,
            decay = decay ?: fallback.decay,
            sustain = sustain ?: fallback.sustain,
            release = release ?: fallback.release,
            depth = depth ?: fallback.depth,
        )
    }

    /**
     * Resolves this envelope to non-null values using defaults.
     */
    fun resolve(): Resolved {
        return Resolved(
            attack = attack ?: 0.01,
            decay = decay ?: 0.1,
            sustain = sustain ?: 1.0,
            release = release ?: 0.1,
            depth = depth ?: 7.0, // C3: semitones (7 ~= the old 0.5 ratio: 12*log2(1.5))
        )
    }

    companion object {
        /**
         * Default filter envelope settings.
         */
        val default = FilterEnvDef(
            attack = 0.01,
            decay = 0.1,
            sustain = 1.0,
            release = 0.1,
            depth = 7.0, // C3: semitones (7 ~= the old 0.5 ratio at full envelope: 12*log2(1.5))
        )
    }
}
