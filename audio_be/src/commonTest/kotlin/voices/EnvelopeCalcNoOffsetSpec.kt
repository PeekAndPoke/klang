/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.voices.strip.calculateControlRateEnvelope

/**
 * Guards the ONE deliberate divergence between the curve evaluators: `calculateControlRateEnvelope`
 * shares `releaseProgressDenom` but NOT `releaseProgressOffset`.
 *
 * The split is by destination. The denominator is a time-base correction and applies wherever a
 * curve is evaluated. The offset is amplitude-only: it exists to stop a step when a release is too
 * short to ramp, and a step matters for a gain, not for a filter cutoff or an FM depth.
 *
 * Why this needs a guard: `VoiceFactory` always builds the FM envelope with `releaseFrames = 0`
 * (`Voice.Fm`'s envelope), so a well-meaning "unify the three evaluators" refactor that adds the
 * offset here would make `p` = 1 at `relPos` = 0 and drop **FM depth to zero at gate end for every
 * FM voice in every song** — with the whole suite otherwise green.
 */
class EnvelopeCalcNoOffsetSpec : StringSpec({

    fun env(releaseFrames: Double) = Voice.Envelope(
        attackFrames = 0.0,
        decayFrames = 0.0,
        sustainLevel = 0.75,
        releaseFrames = releaseFrames,
    )

    "with releaseFrames = 0 the value at gate end is still the level, NOT zero" {
        // The exact shape VoiceFactory hands FmRenderer. Adding releaseProgressOffset here makes
        // this 0.0 and silently kills FM depth at every note-off.
        calculateControlRateEnvelope(
            env(0.0), blockStart = 100.0, startFrame = 0.0, gateEndFrame = 100.0,
        ) shouldBe (0.75 plusOrMinus 1e-12)
    }

    "with releaseFrames = 1 the value at gate end is still the level" {
        calculateControlRateEnvelope(
            env(1.0), blockStart = 100.0, startFrame = 0.0, gateEndFrame = 100.0,
        ) shouldBe (0.75 plusOrMinus 1e-12)
    }

    "the shared DENOMINATOR is still applied — the endpoint lands on the last frame" {
        // N = 101 renders relPos 0..100, so p = 1.0 at relPos 100 and the envelope is 0 there.
        calculateControlRateEnvelope(
            env(101.0), blockStart = 200.0, startFrame = 0.0, gateEndFrame = 100.0,
        ) shouldBe (0.0 plusOrMinus 1e-9)
    }
})
