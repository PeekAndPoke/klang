/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_be.effects.Reverb

/**
 * Reverb send/return effect for the bus pipeline.
 *
 * Reads from the reverb send buffer and mixes the wet reverb signal into the mix buffer.
 * Short-circuits when room size is negligible (< 0.01).
 */
class KatalystReverbEffect(
    val reverb: Reverb,
) : KatalystEffect {

    override fun process(ctx: KatalystContext) {
        // The DSP decays from `roomFade ?: roomSize`, so the audibility gate has to ask the same
        // question. Testing roomSize alone made `room(0.6).roomfade(0.1)` — no `roomsize` — silent,
        // because roomSize defaults to 0.0 and the override was never reached.
        if ((reverb.roomFade ?: reverb.roomSize) < 0.01) return

        reverb.process(ctx.reverbSendBuffer, ctx.mixBuffer, ctx.blockFrames)
    }
}
