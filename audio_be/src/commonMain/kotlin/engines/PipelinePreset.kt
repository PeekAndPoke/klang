/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.engines

import io.peekandpoke.klang.audio_bridge.PipelineDsl

/**
 * Named preset for the voice filter pipeline topology.
 *
 * Each engine defines a different ordering (and potentially a different set) of
 * `BlockRenderer` stages in the voice's Filter stage. The song DSL selects an
 * pipeline per voice via `.pipeline("name")` — unknown or missing names fall back
 * to [Modern].
 *
 * String-keyed lookup keeps the door open for a future `PipelineDsl` similar to
 * `IgnitorDsl` — a declarative, user-extensible way to build custom engines
 * without code changes.
 */
enum class PipelinePreset(val pipelineName: String, val dsl: PipelineDsl) {

    /**
     * Default engine. Classic subtractive ordering: **osc → waveshaper → VCF → VCA**.
     *
     * ```
     * FilterMod → Crush → Coarse → Distort → AudioFilter → Tremolo → Phaser → Envelope
     * ```
     *
     * ADSR runs last, so the filter and phaser see a steady-amplitude signal
     * and don't smear the attack. Waveshapers still precede the filter so
     * their harmonics get cleaned up.
     */
    Modern("modern", PipelineDsl.modern),

    /**
     * Guitar-pedal feel: envelope drives the waveshapers, so distortion
     * responds to dynamics.
     *
     * ```
     * FilterMod → Envelope → Crush → Coarse → Distort → AudioFilter → Tremolo → Phaser
     * ```
     *
     * Quiet attack stays clean, hot sustain saturates, release tail fades
     * through the drive. Filter is after the waveshapers but before the
     * modulation FX. Trades "no attack smearing" for "dynamics-responsive
     * distortion" — use when you want the pedal-chain character.
     */
    Pedal("pedal", PipelineDsl.pedal);

    companion object {
        /**
         * Resolves a user-facing engine name (case-insensitive) to an [PipelinePreset].
         * Unknown or null names return [Modern] — never throws.
         */
        fun fromName(name: String?): PipelinePreset {
            val key = name?.lowercase() ?: return Modern
            return entries.firstOrNull { it.pipelineName == key } ?: Modern
        }
    }
}
