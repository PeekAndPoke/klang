/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer

/**
 * Bit-exact guards for the `controlRateValueOrNull` contract, added with the
 * [MemoizingIgnitor.controlRateValueOrNull] propagation (unified-eq plan, D1a step 1):
 *
 * 1. Every pointwise combinator's scalar is `toRawBits()`-equal to its rendered samples —
 *    the existing `ControlRateValueSpec` checks one composite shape at 1e-9 tolerance,
 *    which is NOT evidence for the constant-fold work that consumes these scalars in the
 *    audio path.
 * 2. [MemoizingIgnitor] delegates the scalar (composite constant subtrees fold through the
 *    wrapper that `buildIgnitor` puts around every non-leaf node) and stays `null` for
 *    stateful inners.
 * 3. The pulze `duty` path uses `controlRateValueOrNull` as a BRANCH SELECTOR (bake-once
 *    hoisted loop vs per-sample PWM rebake). The propagation flips composite-constant duties
 *    onto the hoisted branch — both branches must produce bit-identical output. (Pulze only:
 *    square's duty is a hardwired bare Constant, already non-null before the change.)
 */
class ControlRateScalarParitySpec : StringSpec({

    val blockFrames = 128

    fun ctx(): IgniteContext = IgniteContext(
        sampleRate = 44100,
        voiceDurationFrames = blockFrames * 8,
        gateEndFrame = blockFrames * 8,
        releaseFrames = 0,
        voiceEndFrame = blockFrames * 8,
        scratchBuffers = ScratchBuffers(blockFrames),
    ).apply {
        offset = 0
        length = blockFrames
        voiceElapsedFrames = 0
    }

    /** Renders one block and asserts every sample is bit-equal to the reported scalar. */
    fun assertScalarBitEqualsRender(sig: Ignitor, freqHz: Double = 220.0) {
        val c = ctx()
        val scalar = sig.controlRateValueOrNull(freqHz, c)
        scalar.shouldNotBeNull()
        val buf = AudioBuffer(blockFrames)
        sig.generate(buf, freqHz, c)
        for (i in 0 until blockFrames) {
            buf[i].toRawBits() shouldBe scalar.toRawBits()
        }
    }

    // ── 1. per-op scalar ≡ rendered, bit-exact ───────────────────────────────────

    val a = 0.37
    val b = -2.6

    "plus scalar is bit-equal to render" {
        assertScalarBitEqualsRender(ConstantIgnitor(a) + ParamIgnitor("p", b))
    }

    "times scalar is bit-equal to render" {
        assertScalarBitEqualsRender(ConstantIgnitor(a) * ParamIgnitor("p", b))
    }

    "mul-by-constant scalar is bit-equal to render" {
        assertScalarBitEqualsRender(ParamIgnitor("p", b).mul(a))
    }

    "div scalar is bit-equal to render" {
        assertScalarBitEqualsRender(ConstantIgnitor(a).div(ParamIgnitor("p", b)))
    }

    "div by tiny divisor scalar is bit-equal to render (safeDiv path)" {
        assertScalarBitEqualsRender(ConstantIgnitor(a).div(ParamIgnitor("p", 1e-20)))
    }

    "minus scalar is bit-equal to render" {
        assertScalarBitEqualsRender(ConstantIgnitor(a).minus(ParamIgnitor("p", b)))
    }

    "neg scalar is bit-equal to render" {
        assertScalarBitEqualsRender(ParamIgnitor("p", b).neg())
    }

    "abs scalar is bit-equal to render" {
        assertScalarBitEqualsRender(ParamIgnitor("p", b).abs())
    }

    "pow scalar is bit-equal to render (negative base, signed-magnitude)" {
        assertScalarBitEqualsRender(ParamIgnitor("p", b).pow(ConstantIgnitor(a)))
    }

    "min scalar is bit-equal to render" {
        assertScalarBitEqualsRender(ConstantIgnitor(a).min(ParamIgnitor("p", b)))
    }

    "max scalar is bit-equal to render" {
        assertScalarBitEqualsRender(ConstantIgnitor(a).max(ParamIgnitor("p", b)))
    }

    "clamp scalar is bit-equal to render" {
        assertScalarBitEqualsRender(
            ParamIgnitor("p", b).clamp(ConstantIgnitor(-1.0), ConstantIgnitor(1.0)),
        )
    }

    "exp scalar is bit-equal to render" {
        assertScalarBitEqualsRender(ParamIgnitor("p", a).exp())
    }

    "log scalar is bit-equal to render" {
        assertScalarBitEqualsRender(ParamIgnitor("p", b).log())
    }

    "times output clamp scalar is bit-equal to render (safeOut at ±SAFE_MAX)" {
        assertScalarBitEqualsRender(ConstantIgnitor(1e10) * ParamIgnitor("p", 1e10))
    }

    "freq-derived composite scalar is bit-equal to render" {
        assertScalarBitEqualsRender((FreqIgnitor * ConstantIgnitor(2.0)) + ConstantIgnitor(10.0))
    }

    // ── 2. MemoizingIgnitor propagation ──────────────────────────────────────────

    "MemoizingIgnitor delegates the scalar of a composite constant subtree" {
        val inner = FreqIgnitor * ParamIgnitor("track", 1.9)
        val wrapped = MemoizingIgnitor(inner)
        wrapped.controlRateValueOrNull(220.0, ctx()) shouldBe inner.controlRateValueOrNull(220.0, ctx())
        assertScalarBitEqualsRender(wrapped)
    }

    "MemoizingIgnitor stays null for a stateful inner" {
        MemoizingIgnitor(Ignitors.sine()).controlRateValueOrNull(440.0, ctx()).shouldBeNull()
    }

    // ── 3. pulze duty branch parity (hoisted vs per-sample PWM), multi-block ─────

    "pulze with Memoizing-wrapped composite duty is bit-identical to the PWM branch" {
        // Same duty expression twice: once foldable (post-propagation → hoisted bake-once
        // branch), once behind an opaque wrapper whose controlRateValueOrNull stays null
        // (→ the per-sample PWM rebake branch, i.e. the pre-propagation behavior).
        fun duty(): Ignitor = ConstantIgnitor(0.6) * ParamIgnitor("d", 0.5)

        val hoisted = Ignitors.pulze(duty = MemoizingIgnitor(duty()))
        val pwm = Ignitors.pulze(duty = OpaqueIgnitor(duty()))

        val ctxH = ctx()
        val ctxP = ctx()
        val bufH = AudioBuffer(blockFrames)
        val bufP = AudioBuffer(blockFrames)
        repeat(4) {
            hoisted.generate(bufH, 220.0, ctxH)
            pwm.generate(bufP, 220.0, ctxP)
            for (i in 0 until blockFrames) {
                bufH[i].toRawBits() shouldBe bufP[i].toRawBits()
            }
            ctxH.voiceElapsedFrames += blockFrames
            ctxP.voiceElapsedFrames += blockFrames
        }
    }
})

/**
 * Forces the non-scalar path: delegates rendering but inherits the `null` default of
 * [Ignitor.controlRateValueOrNull] — the reference behavior a foldable node had before the
 * [MemoizingIgnitor] propagation (and the reference the constant-fold specs compare against).
 */
internal class OpaqueIgnitor(private val inner: Ignitor) : Ignitor {
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) =
        inner.generate(buffer, freqHz, ctx)
}
