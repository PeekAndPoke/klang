/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_be.effects.Phaser

/**
 * Phaser insert effect for the bus pipeline.
 *
 * Processes the mix buffer in-place with a multi-stage all-pass phaser.
 * Short-circuits when depth is negligible (< 0.01).
 */
class KatalystPhaserEffect(
    val phaser: Phaser,
) : KatalystEffect {

    override fun process(ctx: KatalystContext) {
        if (phaser.depth < 0.01) return

        phaser.process(ctx.mixBuffer, ctx.blockFrames)
    }
}
