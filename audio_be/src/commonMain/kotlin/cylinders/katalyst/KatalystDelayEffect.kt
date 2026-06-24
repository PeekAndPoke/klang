/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_be.effects.DelayLine

/**
 * Delay send/return effect for the bus pipeline.
 *
 * Reads from the delay send buffer and mixes the delayed signal into the mix buffer.
 * Short-circuits when delay time is negligible (< 10ms).
 */
class KatalystDelayEffect(
    val delayLine: DelayLine,
) : KatalystEffect {

    override fun process(ctx: KatalystContext) {
        if (delayLine.delayTimeSeconds < 0.01) return

        delayLine.process(ctx.delaySendBuffer, ctx.mixBuffer, ctx.blockFrames)
    }
}
