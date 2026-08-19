/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_be.effects.Reverb

/**
 * Reverb send/return effect for the bus pipeline.
 *
 * Reads from the reverb send buffer and mixes the wet reverb signal into the mix buffer.
 * Short-circuits when there is no tail to render — no explicit `roomFade` and a negligible
 * `roomSize` (< 0.01). An explicit `roomFade` always renders, including 0.0 (the shortest tail).
 */
class KatalystReverbEffect(
    val reverb: Reverb,
) : KatalystEffect {

    override fun process(ctx: KatalystContext) {
        // The DSP decays from `roomFade ?: roomSize`, so the gate must ask the same question.
        // Testing roomSize alone made `room(0.6).roomfade(0.1)` — no `roomsize` — silent, because
        // roomSize defaults to 0.0 and the override was never reached. Note an explicit roomFade is
        // intent to reverberate at ANY value: 0.0 is the engine's SHORTEST tail (~0.7 s), not "off".
        if (reverb.roomFade == null && reverb.roomSize < 0.01) return

        reverb.process(ctx.reverbSendBuffer, ctx.mixBuffer, ctx.blockFrames)
    }
}
