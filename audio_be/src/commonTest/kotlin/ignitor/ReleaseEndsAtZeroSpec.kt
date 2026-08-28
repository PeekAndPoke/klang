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
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.filter.EnvelopeRenderer
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
 * The three CURVE evaluators share `releaseProgressDenom` and all three are pinned: the ignitor
 * envelope (`AdsrIgnitor`) and the strip VCA (`EnvelopeRenderer`) here, the filter/FM envelope
 * (`calculateControlRateEnvelope`) in `EnvelopeShapeTest`. A fourth, linear evaluator
 * (`IgnitorFilters.computeFilterEnvelope`) is deliberately exempt — see the note there.
 *
 * These cases set `declickSeconds = 0` so they measure the CURVE. On the strip VCA the shipped
 * default runs a de-click one-pole downstream of it, which lags and dominates the residual; see
 * the scope note in `AdsrCurveMath`.
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
        releaseCurve = curve,
        declickSeconds = IgnitorDsl.Constant(0.0), // the curve alone, no smoother residual
    )

    /** Renders exactly the frames a voice renders: [0, gate + releaseFrames). */
    fun lastRenderedEnv(relSec: Double, curve: AdsrCurve): Double {
        val gateFrames = sampleRate / 10
        val relFrames = (relSec * sampleRate).toInt()
        val total = gateFrames + relFrames
        val ctx = IgniteContext(
            sampleRate = sampleRate, voiceDurationFrames = gateFrames, gateEndFrame = gateFrames,
            releaseFrames = relFrames, voiceEndFrame = total,
            scratchBuffers = ScratchBuffers(blockFrames),
        )
        val ig = envDsl(relSec, curve).toExciter()
        val out = AudioBuffer(total)
        val tmp = AudioBuffer(blockFrames)
        var pos = 0
        while (pos < total) {
            val n = minOf(blockFrames, total - pos)
            ctx.offset = 0; ctx.length = n; ctx.voiceElapsedFrames = pos
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

    // ── The strip VCA, the third evaluator sharing the same time base ─────────

    "the strip VCA's release also ends at exactly 0.0 on the last rendered frame" {
        // The amp VCA is the one that matters most and was the only evaluator not directly pinned.
        // declickSeconds = 0 makes the smoother an exact pass-through, so this measures the CURVE
        // rather than the smoother's lag.
        val gateFrames = 100
        val relFrames = 240
        val total = gateFrames + relFrames

        for (curve in AdsrCurve.entries) {
            val buf = AudioBuffer(total)
            for (i in 0 until total) buf[i] = 1.0

            val renderer = EnvelopeRenderer(
                Voice.Envelope(
                    attackFrames = 1.0,
                    decayFrames = 1.0,
                    sustainLevel = 1.0,
                    releaseFrames = relFrames.toDouble(),
                    releaseCurve = curve,
                ),
                startFrame = 0.0,
                gateEndFrame = gateFrames.toDouble(),
                declickSeconds = 0.0,
            )
            val ctx = BlockContext(
                audioBuffer = buf,
                freqModBuffer = DoubleArray(total),
                scratchBuffers = ScratchBuffers(total),
                sampleRate = sampleRate,
                startFrame = 0.0,
                endFrame = total.toDouble(),
                gateEndFrame = gateFrames.toDouble(),
                freqHz = 100.0,
                signal = Ignitors.silence(),
                signalCtx = IgniteContext(
                    sampleRate = sampleRate, voiceDurationFrames = gateFrames,
                    gateEndFrame = gateFrames, releaseFrames = relFrames, voiceEndFrame = total,
                    scratchBuffers = ScratchBuffers(total),
                ),
                cylinders = Cylinders(blockFrames = total, sampleRate = sampleRate),
            ).apply { offset = 0; length = total; blockStart = 0.0 }

            renderer.render(ctx)

            withClue("VCA curve=$curve on the last rendered frame") {
                abs(buf[total - 1]) shouldBe 0.0
            }
        }
    }

    "the strip VCA ends at 0.0 for a FRACTIONAL releaseFrames too" {
        // Voice.Envelope.releaseFrames is a raw Double (release * sampleRate) and is fractional at
        // most sample rates, but the voice renders floor(N) frames. Without the floor() the last
        // rendered frame lands short of p = 1.0. Integral-frame cases cannot see this.
        val gateFrames = 100
        val relFrames = 240.5
        val rendered = gateFrames + 240
        val buf = AudioBuffer(rendered)
        for (i in 0 until rendered) buf[i] = 1.0

        val renderer = EnvelopeRenderer(
            Voice.Envelope(
                attackFrames = 1.0, decayFrames = 1.0, sustainLevel = 1.0,
                releaseFrames = relFrames, releaseCurve = AdsrCurve.Exponential,
            ),
            startFrame = 0.0,
            gateEndFrame = gateFrames.toDouble(),
            declickSeconds = 0.0,
        )
        val ctx = BlockContext(
            audioBuffer = buf,
            freqModBuffer = DoubleArray(rendered),
            scratchBuffers = ScratchBuffers(rendered),
            sampleRate = sampleRate,
            startFrame = 0.0,
            endFrame = gateFrames + relFrames,
            gateEndFrame = gateFrames.toDouble(),
            freqHz = 100.0,
            signal = Ignitors.silence(),
            signalCtx = IgniteContext(
                sampleRate = sampleRate, voiceDurationFrames = gateFrames,
                gateEndFrame = gateFrames, releaseFrames = 240, voiceEndFrame = rendered,
                scratchBuffers = ScratchBuffers(rendered),
            ),
            cylinders = Cylinders(blockFrames = rendered, sampleRate = sampleRate),
        ).apply { offset = 0; length = rendered; blockStart = 0.0 }

        renderer.render(ctx)
        abs(buf[rendered - 1]) shouldBe 0.0
    }

    "a release too short to ramp is silent on its single frame, not held at full" {
        // The degenerate N<=1 case: releaseProgressOffset makes p=1 immediately rather than
        // leaving the gain at the release start level for one frame and then cutting.
        lastRenderedEnv(1.0 / sampleRate, AdsrCurve.Exponential) shouldBe 0.0
    }
})
