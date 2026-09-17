/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_be.effects.Compressor

/**
 * Compressor insert effect for the bus pipeline.
 *
 * Processes the mix buffer in-place with dynamic range compression.
 * Only active when a compressor has been configured.
 */
class KatalystCompressorEffect : KatalystEffect {

    /** Compressor instance — null means no compression configured */
    var compressor: Compressor? = null

    override fun process(ctx: KatalystContext) {
        compressor?.process(ctx.mixBuffer.left, ctx.mixBuffer.right, ctx.blockFrames)
    }

    /**
     * Drops the instance, which is the whole clean slate: a new owner that wants compression gets a
     * fresh envelope follower rather than a previous owner's half-closed gain.
     */
    override fun reset() {
        compressor = null
    }

    /**
     * False: the envelope follower is state, but a compressor only ATTENUATES what it is given and
     * emits nothing from silence, so it can neither hold nor start a tail. See
     * [KatalystBodyEffect.hasTail] for the insert-vs-send rule a future stage has to apply.
     */
    override fun hasTail(): Boolean = false

    /** Rents nothing, so retiring is the clean slate. */
    override fun retire() {
        reset()
    }
}
