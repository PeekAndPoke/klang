/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_be.effects.Phaser

/**
 * Phaser insert effect for the bus pipeline.
 *
 * Processes the mix buffer in-place with a multi-stage all-pass phaser. The depth gate lives in
 * [Phaser.process] itself (ONE gate, one threshold: [Phaser.MIN_ACTIVE_DEPTH]) — and sits BELOW
 * the LFO advance there, so a gated block still moves the sweep clock (block-framing ledger D2).
 * The old outer `< 0.01` gate here disagreed with the inner `<= 0.0` one and froze the LFO; both
 * problems end here.
 */
class KatalystPhaserEffect(
    val phaser: Phaser,
) : KatalystEffect {

    override fun process(ctx: KatalystContext) {
        phaser.process(ctx.mixBuffer, ctx.blockFrames)
    }
}
