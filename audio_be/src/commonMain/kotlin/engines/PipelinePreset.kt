/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.engines

import io.peekandpoke.klang.audio_bridge.PipelineDsl

/**
 * Named preset for the voice filter pipeline topology.
 *
 * Each engine defines an ordering (and potentially a set) of `BlockRenderer` stages in the
 * voice's Filter stage. The song DSL selects a pipeline per voice via `.pipeline("name")`;
 * unknown or missing names fall back to [Modern], which is the only built-in since the `pedal`
 * preset was removed (2026-09-25, `builtin-instruments.md` D4).
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
     * FilterMod → Crush → Coarse → Distort → AudioFilter → Tremolo → Envelope
     * ```
     *
     * ADSR runs last, so the filter sees a steady-amplitude signal (the phaser lives on the BUS since 2026-08-24)
     * and don't smear the attack. Waveshapers still precede the filter so
     * their harmonics get cleaned up.
     */
    Modern("modern", PipelineDsl.modern);

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
