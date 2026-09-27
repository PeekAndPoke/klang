/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.AdsrCurves
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import kotlin.math.abs

/**
 * A release of N frames must reach **exactly 0.0 on the last frame the voice renders**.
 *
 * **The bug.** `p` used to divide by N, but the voice renders `relPos = 0..N-1`, so `p` topped out
 * at `(N-1)/N` and the curve's exact endpoint landed on the first frame the voice does NOT render.
 * The envelope was therefore still audible when `Voice.render` dropped the voice, and that residual
 * is a step straight to zero. Measured before the fix:
 *
 * | release | frames | envelope on the last rendered frame |
 * |---|---|---|
 * | 0.1 ms | 4 | 5.85e-2 (−24.7 dB) |
 * | 1 ms | 48 | 3.38e-3 (−49.4 dB) |
 * | 50 ms | 2400 | 6.55e-5 (−83.7 dB) |
 *
 * `EnvelopeShapeTest`'s endpoint case did not catch it because it sampled `relPos = N`, one frame
 * past the end, where the `coerceAtMost(1.0)` masks the off-by-one. That case now samples `N-1`.
 *
 * Every envelope evaluates one law (`EnvelopeCore`, pinned against its oracles in `EnvelopeLawSpec`);
 * the amplitude host is pinned here end to end: the ignitor envelope (`AdsrIgnitor`). (The voice strip's
 * VCA was the second until the strip retired, phase 3 step 9.)
 *
 * These cases set `declickSeconds = 0` so they measure the CURVE. With the de-click on (`classic()`'s
 * envelope) a one-pole downstream of it lags and dominates the residual; see the scope note in
 * `AdsrCurveMath`.
 */
class ReleaseEndsAtZeroSpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128

    /** DC source, so the rendered output IS the envelope value. */
    fun envDsl(relSec: Double, curve: AdsrCurve) = IgnitorDsl.Adsr(
        inner = IgnitorDsl.Constant(1.0),
        attackSec = IgnitorDsl.Constant(0.001),
        decaySec = IgnitorDsl.Constant(10.0),
        sustainLevel = IgnitorDsl.Constant(1.0),   // hold at 1.0 so release starts from exactly 1.0
        releaseSec = IgnitorDsl.Constant(relSec),
        releaseCurve = AdsrCurves.knob(curve),
        declickSeconds = IgnitorDsl.Constant(0.0), // the curve alone, no smoother residual
    )

    /** Renders exactly the frames a voice renders: [0, gate + releaseFrames). */
    fun lastRenderedEnv(relSec: Double, curve: AdsrCurve): Double {
        val gateFrames = sampleRate / 10
        val relFrames = (relSec * sampleRate).toInt()
        val total = gateFrames + relFrames
        val ctx = IgniteContext(
            sampleRate = sampleRate, voiceDurationFrames = gateFrames, gateEndFrame = gateFrames,
            releaseFrames = relFrames,  
            scratchBuffers = ScratchBuffers(blockFrames),
        )
        val ig = envDsl(relSec, curve).toExciter()
        val out = AudioBuffer(total)
        val tmp = AudioBuffer(blockFrames)
        var pos = 0
        while (pos < total) {
            val n = minOf(blockFrames, total - pos)
            ctx.updateOffsetAndLength(0, n); ctx.voiceElapsedFrames = pos
            ig.generate(tmp, freqHz = 100.0, ctx = ctx)
            for (i in 0 until n) out[pos + i] = tmp[i]
            pos += n
        }
        return abs(out[total - 1])
    }

    // 0.1 ms is the case that used to sit at -25 dB; 50 ms is Der Schmetterling's guitar.
    val releases = listOf(0.0001, 0.0005, 0.001, 0.005, 0.013, 0.050, 0.200)

    releases.forEach { relSec ->
        "an exp release of ${relSec}s ends at exactly 0.0 on the last rendered frame" {
            lastRenderedEnv(relSec, AdsrCurve.Exponential) shouldBe 0.0
        }
    }

    "every curve ends at exactly 0.0, not just exp" {
        for (curve in AdsrCurve.entries) {
            withClue("curve=$curve") {
                lastRenderedEnv(0.050, curve) shouldBe 0.0
            }
        }
    }

    "a release too short to ramp is silent on its single frame, not held at full" {
        // The degenerate N<=1 case: releaseProgressOffset makes p=1 immediately rather than
        // leaving the gain at the release start level for one frame and then cutting.
        lastRenderedEnv(1.0 / sampleRate, AdsrCurve.Exponential) shouldBe 0.0
    }
})
