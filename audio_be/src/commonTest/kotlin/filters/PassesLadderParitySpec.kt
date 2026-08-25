/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.filters

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.lowpass
import io.peekandpoke.klang.audio_bridge.optimize

/**
 * C5 parity pin: the optimizer's staggered-q expansion (audio_bridge, `passesLadderRel` —
 * duplicated because the bridge cannot depend on audio_be) must produce EXACTLY the
 * engine's [butterworthQLadder] values, BIT FOR BIT. If either side's math drifts, a fused passes-N
 * filter would render a different alignment than the unfused cascade — same knob, two
 * sounds, the exact bug class this plan exists to remove.
 */
class PassesLadderParitySpec : StringSpec({

    "optimizer-expanded section qs equal the engine ladder for every (passes, q) sampled" {
        for (n in 2..5) {
            for (userQ in listOf(0.707, 1.0, 1.3)) {
                val ladder = butterworthQLadder(n, userQ)
                val eq = IgnitorDsl.Sine().lowpass(2000.0, userQ, passes = n).optimize()
                    as IgnitorDsl.Eq
                eq.sections.size shouldBe n
                for (k in 0 until n) {
                    val sectionQ = (eq.sections[k] as IgnitorDsl.EqSection.Lowpass).q
                        as IgnitorDsl.Constant
                    // RAW BITS, not a tolerance: both sides must associate the product the
                    // same way (`userQ * (√2 / 2cosθ)`). Written as `(userQ*√2) / 2cosθ` the
                    // two differ by 1 ULP on ~a third of the samples below — inaudible, but
                    // it would quietly demote the render-parity guarantee to "close enough".
                    sectionQ.value.toRawBits() shouldBe ladder[k].toRawBits()
                }
            }
        }
    }
})
