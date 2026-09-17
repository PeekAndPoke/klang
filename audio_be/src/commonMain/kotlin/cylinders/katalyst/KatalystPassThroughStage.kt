/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_bridge.KatalystStageDsl

/**
 * The stage a declared effect gets while the engine cannot build it yet: it holds the position in
 * the chain and leaves the orbit buffers **untouched, bit for bit**.
 *
 * The house fall-through is pass-through, never silence and never an invented gain: an author who
 * writes a stage the backend does not know yet keeps their sound and loses only the effect, and a
 * chain's stage COUNT and ORDER stay what the author wrote, so nothing downstream shifts.
 *
 * [KatalystStageDsl.Eq] and [KatalystStageDsl.Gain] build this today. Step 4 of the Katalyst work
 * replaces them with `KatalystEqEffect` and `KatalystGainEffect` and this class goes when the last
 * stage kind has a real implementation.
 *
 * One instance per declared stage, not a shared object: a chain's pipeline should never hold the
 * same object twice, or a reader cannot tell two declared stages apart.
 */
class KatalystPassThroughStage : KatalystEffect {

    override fun process(ctx: KatalystContext) {
        // Deliberately empty. A stage the engine cannot build yet must not change the signal, and
        // "no change" on an in-place bus buffer means doing nothing at all: even a copy through a
        // scratch buffer would be a chance to alter a sample.
    }

    /** Nothing to clear: this stage holds no state at all. Stated, not inherited, so the real
     *  `KatalystEqEffect` of step 4 cannot replace it and forget its filter memory. */
    override fun reset() {
        // Deliberately empty, see the KDoc.
    }

    /** No state at all, so nothing to ring: the one stage for which "no time-based state" really
     *  is the reason (see [KatalystBodyEffect.hasTail] for why it is not the reason elsewhere). */
    override fun hasTail(): Boolean = false

    /** Rents nothing, so retiring is the clean slate, which here is nothing. */
    override fun retire() {
        reset()
    }
}
