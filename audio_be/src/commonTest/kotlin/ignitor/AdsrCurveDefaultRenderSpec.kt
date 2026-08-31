/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import kotlin.math.abs

/**
 * ENGINE half of the ADSR curve-default pin (2026-08-24): a node with UNSET curves must
 * render bit-identically to explicit [AdsrCurve.Default] (= Exponential) on every stage —
 * on the DSL-runtime fallback (`IgnitorDslRuntime`) and BOTH `Ignitor.adsr` factory
 * overloads. The old behaviour was Square attack/release on the ignitor door while the
 * strip path was Exponential — one knob, two sounds.
 *
 * Fixture timing (48 kHz, 2048 frames, gate at 1024): attack 240 frames + decay 240 land
 * well inside the gate, sustain holds to the gate, release (480 frames) completes inside
 * the tail — every stage genuinely renders, so a wrong default on ANY stage goes red.
 */
class AdsrCurveDefaultRenderSpec : StringSpec({

    val blockFrames = 128
    val blocks = 16
    val frames = blocks * blockFrames

    fun ctx() = IgniteContext(
        sampleRate = 48000,
        voiceDurationFrames = frames,
        gateEndFrame = frames / 2,      // 1024 frames: attack (240) + decay (240) + sustain fit
        releaseFrames = 480,            // completes inside the 1024-frame tail
        scratchBuffers = ScratchBuffers(blockFrames = blockFrames),
        voiceElapsedFrames = 0,
    )

    fun render(chain: Ignitor): DoubleArray {
        val c = ctx()
        c.updateOffsetAndLength(0, blockFrames)
        val buf = AudioBuffer(blockFrames)
        val out = DoubleArray(frames)
        repeat(blocks) { blk ->
            chain.generate(buf, 220.0, c)
            buf.copyInto(out, blk * blockFrames, 0, blockFrames)
            c.voiceElapsedFrames += blockFrames
        }
        var peak = 0.0
        for (v in out) {
            if (abs(v) > peak) {
                peak = abs(v)
            }
        }
        (peak > 1e-6) shouldBe true
        return out
    }

    fun buildDsl(node: IgnitorDsl): Ignitor = node.toExciter()

    "DSL runtime: unset curves render bit-identical to explicit Exponential (and NOT to Square)" {
        fun adsrNode(a: AdsrCurve?, d: AdsrCurve?, r: AdsrCurve?) = IgnitorDsl.Adsr(
            inner = IgnitorDsl.Sine(),
            attackSec = IgnitorDsl.Constant(0.005),
            decaySec = IgnitorDsl.Constant(0.005),
            sustainLevel = IgnitorDsl.Constant(0.6),
            releaseSec = IgnitorDsl.Constant(0.01),
            attackCurve = a, decayCurve = d, releaseCurve = r,
        )
        val unset = render(buildDsl(adsrNode(null, null, null)))
        val explicit = render(buildDsl(adsrNode(AdsrCurve.Exponential, AdsrCurve.Exponential, AdsrCurve.Exponential)))
        for (i in 0 until frames) {
            unset[i].toRawBits() shouldBe explicit[i].toRawBits()
        }
        // anti-vacuous: the curve genuinely matters in this fixture
        val square = render(buildDsl(adsrNode(AdsrCurve.Square, AdsrCurve.Square, AdsrCurve.Square)))
        var differs = false
        for (i in 0 until frames) {
            if (unset[i] != square[i]) {
                differs = true
                break
            }
        }
        differs shouldBe true
    }

    "raw factory (Double overload): no curves renders bit-identical to explicit Exponential" {
        val defaulted = render(Ignitors.sine().adsr(0.005, 0.005, 0.6, 0.01))
        val explicit = render(
            Ignitors.sine().adsr(
                0.005, 0.005, 0.6, 0.01,
                attackCurve = AdsrCurve.Exponential,
                decayCurve = AdsrCurve.Exponential,
                releaseCurve = AdsrCurve.Exponential,
            )
        )
        for (i in 0 until frames) {
            defaulted[i].toRawBits() shouldBe explicit[i].toRawBits()
        }
    }

    "raw factory (Ignitor-param overload): its OWN curve defaults are Exponential too" {
        // The inner overload has separate default declarations — production always passes
        // curves explicitly, so without this row a reverted default there survives green.
        fun p(name: String, v: Double): Ignitor = ParamIgnitor(name, v)
        val defaulted = render(
            Ignitors.sine().adsr(p("a", 0.005), p("d", 0.005), p("s", 0.6), p("r", 0.01))
        )
        val explicit = render(
            Ignitors.sine().adsr(
                p("a", 0.005), p("d", 0.005), p("s", 0.6), p("r", 0.01),
                attackCurve = AdsrCurve.Exponential,
                decayCurve = AdsrCurve.Exponential,
                releaseCurve = AdsrCurve.Exponential,
            )
        )
        for (i in 0 until frames) {
            defaulted[i].toRawBits() shouldBe explicit[i].toRawBits()
        }
    }
})
