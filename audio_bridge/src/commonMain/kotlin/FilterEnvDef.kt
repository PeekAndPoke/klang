/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

import io.peekandpoke.klang.audio_bridge.constants.FILTER_ENV_ATTACK_SEC
import io.peekandpoke.klang.audio_bridge.constants.FILTER_ENV_DECAY_SEC
import io.peekandpoke.klang.audio_bridge.constants.FILTER_ENV_DEPTH_SEMITONES
import io.peekandpoke.klang.audio_bridge.constants.FILTER_ENV_RELEASE_SEC
import io.peekandpoke.klang.audio_bridge.constants.FILTER_ENV_SUSTAIN_LEVEL


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
            // The one home of these numbers is `constants/FilterEnvelopeDefaults.kt`, which the
            // Ignitor filter nodes and their doors read as well: the two surfaces resolve the
            // same STAGE TIMES and the same DEPTH. The curve is not resolved here: the engine
            // hands this envelope `MOD_ENV_CURVE`, the tree's default too (decision D3). See
            // `IgnitorDsl.Lowpass.env`.
            attack = attack ?: FILTER_ENV_ATTACK_SEC,
            decay = decay ?: FILTER_ENV_DECAY_SEC,
            sustain = sustain ?: FILTER_ENV_SUSTAIN_LEVEL,
            release = release ?: FILTER_ENV_RELEASE_SEC,
            depth = depth ?: FILTER_ENV_DEPTH_SEMITONES,
        )
    }

    companion object {
        /**
         * Default filter envelope settings.
         */
        val default = FilterEnvDef(
            attack = FILTER_ENV_ATTACK_SEC,
            decay = FILTER_ENV_DECAY_SEC,
            sustain = FILTER_ENV_SUSTAIN_LEVEL,
            release = FILTER_ENV_RELEASE_SEC,
            depth = FILTER_ENV_DEPTH_SEMITONES,
        )
    }
}
