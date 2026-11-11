/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.probes

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe

/**
 * The click probe's `reverb.lowpass` rows, with a threshold (KLANG-00420, the closing run of 2026-11-11): a lead that
 * was fixed leaves its row behind as an ordinary spec (`audio/ref/verification.md`, "The probes"). The rows are the
 * probe's own cases ([clickKatalystCases]) read by its own function ([measureClickStereo]), the worst of their instants
 * and channels, so probe and guard cannot drift apart.
 *
 * 1000 Hz to unset read kink 6.84 and a burst at -68.6 dB once `lowpass` was a real cutoff (KLANG-00428): the comb
 * damping's pole jumped from 0.88 to 0.2 and every comb's lag at the tone with it. The pole glides now
 * (`KatalystReverbEffect.dampingGlide`). The thresholds sit under the probe's own 50 ms glide control (kink 2.5,
 * -86 dB) and above the rows' readings after the fix.
 */
class ReverbLowpassClickSpec : StringSpec({

    "a reverb.lowpass jump, both ways, reads no click: kink under 2, the burst under -86 dB" {
        val cases = clickKatalystCases().filter { it.knob == "reverb.lowpass" }

        cases.size shouldBe 2

        for (case in cases) {
            val reading = (0 until case.instants)
                .map { measureClickStereo(render = case.render(it), config = case.config) }
                .reduce { a, b -> worstClick(a, b) }

            println("ReverbLowpassClickSpec: ${case.change}: kink ${reading.kinkRatio}, burst ${reading.burstAbsDb} dB")

            withClue("${case.change}: kink ${reading.kinkRatio}, burst ${reading.burstAbsDb} dB") {
                reading.kinkRatio shouldBeLessThan 2.0
                reading.burstAbsDb shouldBeLessThan -86.0
            }
        }
    }
})
