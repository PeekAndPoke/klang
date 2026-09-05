/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices.strip.filter

import io.peekandpoke.klang.audio_be.effects.PhaserCore
import io.peekandpoke.klang.audio_be.filters.WetDryMix
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer

/**
 * Per-voice phaser — thin [BlockRenderer] wrapper around a single mono [PhaserCore].
 *
 * **Output**: the shared C4 wet/dry law, correlated branch (p = 2), with a [floor]ed dry —
 * MUST stay identical to the cylinder-bus Phaser (one knob, one law). At the default
 * `floor = 1.0` it is purely additive (`dry + wet · sin²(depth·π/2)`), the `phaserDepth`
 * semantic where the knob adds an effect on top of the source; `phaserFloor < 1` turns the
 * same knob into a crossfade. The Ignitor-DSL phaser is the same law at `dryFloor = 0.0`.
 *
 * ⚠ NOT in any built-in preset (2026-08-24): the bus owns the phaser (see
 * [io.peekandpoke.klang.audio_be.effects.Phaser]). This renderer only runs when a CUSTOM
 * pipeline adds `StageDsl.Phaser` — deliberate per-voice phasing. The same knobs then drive
 * BOTH this pass and the bus pass, so with `phaserFloor < 1` the dry is floored twice
 * (`dryC²`). The two gates also differ (this one runs at `depth > 0`, the bus at
 * `Phaser.MIN_ACTIVE_DEPTH` = 0.01, inside `Phaser.process` since the katalyst gate moved
 * there) - pre-existing.
 *
 * `depth = 0` bypasses entirely (exact at every floor: `max(floor, cos(0)) = 1`).
 */
class StripPhaserRenderer(
    rate: Double,
    private val depth: Double,
    center: Double,
    sweep: Double,
    sampleRate: Int,
    private val floor: Double = 1.0,
) : BlockRenderer {
    private val core = PhaserCore(PhaserCore.DEFAULT_STAGES, sampleRate).apply {
        this.rate = rate
        this.center = center
        this.sweep = sweep
        // feedback uses PhaserCore's default (0.5) — voice-side phaser doesn't
        // expose feedback as a per-note parameter today.
    }

    override fun render(ctx: BlockContext) {
        if (depth <= 0.0) return

        val buf = ctx.audioBuffer
        // C4 (filter unification): shared wet/dry law, p = 2, floored dry — MUST stay
        // identical to the cylinder-bus Phaser (one knob, one law).
        val dryC = WetDryMix.dryCoeff(depth, floor = floor, p = 2)
        val wetC = WetDryMix.wetCoeff(depth, p = 2)

        // Control-rate: compute α at block boundaries once.
        core.prepareBlock(ctx.length)

        for (i in 0 until ctx.length) {
            val idx = ctx.offset + i
            val dry = buf[idx]
            val wet = core.step(dry)
            buf[idx] = dry * dryC + wet * wetC
        }
    }
}
