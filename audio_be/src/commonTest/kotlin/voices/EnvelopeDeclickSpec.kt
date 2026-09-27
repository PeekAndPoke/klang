/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.ignitor.adsr
import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.constants.ENV_DECLICK_SECONDS
import kotlin.math.abs

/**
 * Regression guard for the envelope gain de-click smoother (ENV_DECLICK_SECONDS), the one `classic()`'s envelope
 * runs (the chain `adsr` with `declickSeconds`; it was the voice strip's VCA until the strip retired in phase 3
 * step 9).
 *
 * The ADSR shape curves are value-continuous but not slope-continuous: at a
 * segment join (attack→decay peak, gate-off, instant cutoff) the gain changes
 * abruptly, radiating a click. On a low note the slow carrier can't mask it, so
 * it reads as a "plop". A short one-pole low-pass on the gain rounds those joins.
 *
 * Manual 2nd-difference analysis (corner/floor metric) showed the smoother cuts
 * the 40Hz attack→decay corner ~25x at 0.5ms (ratio 525→21). This guard locks in
 * the directly observable consequence through a real voice: the
 * per-sample gain slew is bounded — no single-sample jumps. Renders a DC carrier
 * (constant=1.0) so the output buffer equals the envelope gain.
 */
class EnvelopeDeclickSpec : StringSpec({

    fun maxSlew(buffer: AudioBuffer, len: Int): Double {
        var m = 0.0
        for (i in 1 until len) {
            val d = abs(buffer[i] - buffer[i - 1])
            if (d > m) m = d
        }
        return m
    }

    "de-click bounds the gain slew at a hard cutoff (releaseFrames=0)" {
        // Without de-click a releaseFrames=0 gate-off drops 1.0 -> 0.0 in ONE sample
        // (slew 1.0 = a click). The smoother caps per-sample slew far below that.
        // Window (412 frames post-gate) is long enough that the fade converges for any
        // reasonable declick time constant (~up to 3ms), so this stays tau-agnostic.
        val voice = VoiceTestHelpers.createSynthVoice(
            startFrame = 0.0,
            endFrame = 512.0,
            gateEndFrame = 100.0,
            blockFrames = 512,
            signal = TestIgnitors.constant.adsr(
                attackSec = 0.0, decaySec = 0.0, sustainLevel = 1.0, releaseSec = 0.0,
                attackCurve = AdsrCurve.Linear, decayCurve = AdsrCurve.Linear, releaseCurve = AdsrCurve.Linear,
                declickSeconds = ENV_DECLICK_SECONDS,
            ),
        )

        val ctx = VoiceTestHelpers.createContext(blockStart = 0.0, blockFrames = 512)
        voice.render(ctx)

        // Sustain at full level before the gate, then a de-clicked fade (not a step).
        ctx.voiceBuffer[0] shouldBe (1.0 plusOrMinus 0.02)
        (maxSlew(ctx.voiceBuffer, 512) < 0.1) shouldBe true
        // And it fully fades out within the (long-enough) window — the smoother converges.
        (ctx.voiceBuffer[511] < 0.05) shouldBe true
    }

    "de-click also bounds the slew across an exp attack→decay→release voice" {
        // The default-ish curves with the steepest joins; still no single-sample jump.
        val voice = VoiceTestHelpers.createSynthVoice(
            startFrame = 0.0,
            endFrame = 600.0,
            gateEndFrame = 400.0,
            blockFrames = 600,
            signal = TestIgnitors.constant.adsr(
                attackSec = 20.0 / 44100, decaySec = 200.0 / 44100, sustainLevel = 0.5, releaseSec = 200.0 / 44100,
                attackCurve = AdsrCurve.Exponential, decayCurve = AdsrCurve.Exponential, releaseCurve = AdsrCurve.Exponential,
                declickSeconds = ENV_DECLICK_SECONDS,
            ),
        )

        val ctx = VoiceTestHelpers.createContext(blockStart = 0.0, blockFrames = 600)
        voice.render(ctx)

        (maxSlew(ctx.voiceBuffer, 600) < 0.1) shouldBe true
    }
})
