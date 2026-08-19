/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import kotlin.math.ln

/**
 * Bit-exact guards for the `controlRateValueOrNull` contract (unified-eq plan, D1a step 1):
 *
 * 1. Every pointwise combinator's scalar is `toRawBits()`-equal to the SCRATCH-path render of
 *    the same expression. For the FOLDING ops (plus/times) BOTH operands sit behind
 *    [OpaqueIgnitor] — opacifying only one leaves the other fold branch live and the oracle
 *    compares fold against fold, tautologically (round-2 finding).
 * 2. The safety guards on the scalar path actually ENGAGE at extreme values (safeOut clamps,
 *    safeDiv substitution, the log arms) — a scalar-vs-own-render comparison alone would stay
 *    green if a guard were deleted from both sides at once.
 * 3. [MemoizingIgnitor] delegates the scalar (composite constant subtrees fold through the
 *    wrapper) and stays `null` for stateful inners; the shared-consumer cache path re-renders
 *    identically when a fold has skipped its refresh.
 * 4. [Ignitor.isBlockConstant] agrees with the scalar's nullability (the structural flag gates
 *    the audio-path folds).
 * 5. The pulze `duty` path uses the scalar as a BRANCH SELECTOR (bake-once hoisted loop vs
 *    per-sample PWM rebake). Both branches must agree bit-exactly INCLUDING their rebake timing
 *    under per-block frequency changes (dt moves) and freq-tracking duties (d and dt move).
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

    /**
     * Asserts [sig]'s scalar is bit-equal to every sample of [reference]'s SCRATCH-path render.
     * The reference must opacify enough operands that the render cannot take ANY fold branch
     * (for plus/times: both operands).
     */
    fun assertScalarBitEqualsScratchRender(sig: Ignitor, reference: Ignitor, freqHz: Double = 220.0) {
        val scalar = sig.controlRateValueOrNull(freqHz, ctx())
        scalar.shouldNotBeNull()
        val buf = AudioBuffer(blockFrames)
        reference.generate(buf, freqHz, ctx())
        for (i in 0 until blockFrames) {
            buf[i].toRawBits() shouldBe scalar.toRawBits()
        }
    }

    // ── 1. per-op scalar ≡ scratch render, bit-exact ─────────────────────────────

    val a = 0.37
    val b = -2.6
    fun opq(v: Double) = OpaqueIgnitor(ConstantIgnitor(v))

    "plus scalar is bit-equal to the scratch render" {
        assertScalarBitEqualsScratchRender(
            sig = ConstantIgnitor(a) + ParamIgnitor("p", b),
            reference = opq(a) + opq(b),
        )
    }

    "times scalar is bit-equal to the scratch render" {
        assertScalarBitEqualsScratchRender(
            sig = ConstantIgnitor(a) * ParamIgnitor("p", b),
            reference = opq(a) * opq(b),
        )
    }

    "mul-by-constant scalar is bit-equal to the scratch render" {
        assertScalarBitEqualsScratchRender(
            sig = ParamIgnitor("p", b).mul(a),
            reference = opq(b).mul(a),
        )
    }

    "div scalar is bit-equal to the scratch render" {
        assertScalarBitEqualsScratchRender(
            sig = ConstantIgnitor(a).div(ParamIgnitor("p", b)),
            reference = ConstantIgnitor(a).div(opq(b)),
        )
    }

    "minus scalar is bit-equal to the scratch render" {
        assertScalarBitEqualsScratchRender(
            sig = ConstantIgnitor(a).minus(ParamIgnitor("p", b)),
            reference = ConstantIgnitor(a).minus(opq(b)),
        )
    }

    "neg scalar is bit-equal to the scratch render" {
        assertScalarBitEqualsScratchRender(
            sig = ParamIgnitor("p", b).neg(),
            reference = opq(b).neg(),
        )
    }

    "abs scalar is bit-equal to the scratch render" {
        assertScalarBitEqualsScratchRender(
            sig = ParamIgnitor("p", b).abs(),
            reference = opq(b).abs(),
        )
    }

    "pow scalar is bit-equal to the scratch render (negative base, signed-magnitude)" {
        assertScalarBitEqualsScratchRender(
            sig = ParamIgnitor("p", b).pow(ConstantIgnitor(a)),
            reference = opq(b).pow(ConstantIgnitor(a)),
        )
    }

    "min scalar is bit-equal to the scratch render" {
        assertScalarBitEqualsScratchRender(
            sig = ConstantIgnitor(a).min(ParamIgnitor("p", b)),
            reference = ConstantIgnitor(a).min(opq(b)),
        )
    }

    "max scalar is bit-equal to the scratch render" {
        assertScalarBitEqualsScratchRender(
            sig = ConstantIgnitor(a).max(ParamIgnitor("p", b)),
            reference = ConstantIgnitor(a).max(opq(b)),
        )
    }

    "clamp scalar is bit-equal to the scratch render" {
        assertScalarBitEqualsScratchRender(
            sig = ParamIgnitor("p", b).clamp(ConstantIgnitor(-1.0), ConstantIgnitor(1.0)),
            reference = opq(b).clamp(ConstantIgnitor(-1.0), ConstantIgnitor(1.0)),
        )
    }

    "exp scalar is bit-equal to the scratch render" {
        assertScalarBitEqualsScratchRender(
            sig = ParamIgnitor("p", a).exp(),
            reference = opq(a).exp(),
        )
    }

    "log scalar is bit-equal to the scratch render" {
        assertScalarBitEqualsScratchRender(
            sig = ParamIgnitor("p", b).log(),
            reference = opq(b).log(),
        )
    }

    "freq-derived composite scalar is bit-equal to the scratch render" {
        assertScalarBitEqualsScratchRender(
            sig = (FreqIgnitor * ConstantIgnitor(2.0)) + ConstantIgnitor(10.0),
            reference = (OpaqueIgnitor(FreqIgnitor) * opq(2.0)) + opq(10.0),
        )
    }

    // ── 2. guard ENGAGEMENT on the scalar path (absolute values, not just parity) ─

    "times scalar clamps at SAFE_MAX" {
        (ConstantIgnitor(1e10) * ParamIgnitor("p", 1e10))
            .controlRateValueOrNull(0.0, ctx()) shouldBe SAFE_MAX
    }

    "mul-by-constant scalar clamps at SAFE_MAX" {
        ParamIgnitor("p", 1e10).mul(1e10).controlRateValueOrNull(0.0, ctx()) shouldBe SAFE_MAX
    }

    "div scalar clamps at SAFE_MAX" {
        ConstantIgnitor(1e10).div(ParamIgnitor("p", 1e-10))
            .controlRateValueOrNull(0.0, ctx()) shouldBe SAFE_MAX
    }

    "div by NaN divisor takes the safeDiv substitution" {
        // Discriminates safeDiv from the downstream safeOut mask: WITH safeDiv, NaN -> SAFE_MIN
        // -> 1e10/1e-15 clamps to SAFE_MAX; WITHOUT it, 1e10/NaN = NaN -> safeOut scrubs to 0.0.
        // (A zero divisor cannot discriminate: +Inf also clamps to SAFE_MAX.)
        ConstantIgnitor(1e10).div(ParamIgnitor("p", Double.NaN))
            .controlRateValueOrNull(0.0, ctx()) shouldBe SAFE_MAX
    }

    "pow scalar clamps at SAFE_MAX" {
        ParamIgnitor("p", 1e10).pow(ConstantIgnitor(3.0))
            .controlRateValueOrNull(0.0, ctx()) shouldBe SAFE_MAX
    }

    "exp scalar clamps at SAFE_MAX" {
        ParamIgnitor("p", 50.0).exp().controlRateValueOrNull(0.0, ctx()) shouldBe SAFE_MAX
    }

    "log scalar positive and zero arms" {
        ParamIgnitor("p", 2.0).log().controlRateValueOrNull(0.0, ctx()) shouldBe ln(2.0)
        ParamIgnitor("p", 0.0).log().controlRateValueOrNull(0.0, ctx()) shouldBe 0.0
    }

    // ── 3. MemoizingIgnitor ───────────────────────────────────────────────────────

    "MemoizingIgnitor delegates the scalar of a composite constant subtree" {
        val wrapped = MemoizingIgnitor(FreqIgnitor * ParamIgnitor("track", 1.9))
        // Absolute value (not wrapped.crv == inner.crv, which is the delegation compared with
        // itself and cannot fail), plus the fully-opacified scratch oracle.
        wrapped.controlRateValueOrNull(220.0, ctx()) shouldBe 220.0 * 1.9
        assertScalarBitEqualsScratchRender(
            sig = wrapped,
            reference = OpaqueIgnitor(FreqIgnitor) * OpaqueIgnitor(ParamIgnitor("track", 1.9)),
        )
    }

    "MemoizingIgnitor stays null for a stateful inner" {
        MemoizingIgnitor(Ignitors.sine()).controlRateValueOrNull(440.0, ctx()).shouldBeNull()
    }

    "a shared MemoizingIgnitor skipped by a fold still serves later consumers bit-exactly" {
        // Production shape: consumers >= 2 (IgnitorBuildCache). The counting probe makes the
        // scenario falsifiable: calls == 0 proves the fold really SKIPPED the memo render;
        // consumer 2 then misses the cache and re-renders the stateless inner, which must equal
        // a scratch-path render bit-for-bit.
        val probe = RenderCountProbe(FreqIgnitor * ParamIgnitor("track", 1.9))
        val memo = MemoizingIgnitor(probe).apply { incConsumers() }
        val c = ctx()

        val folded = AudioBuffer(blockFrames)
        (Ignitors.sine() * memo).generate(folded, 220.0, c)
        probe.generateCalls shouldBe 0 // fold path: memo.generate skipped

        val viaCache = AudioBuffer(blockFrames)
        memo.generate(viaCache, 220.0, c) // consumer 2: cache miss -> render into cache -> copy
        probe.generateCalls shouldBe 1

        val fresh = AudioBuffer(blockFrames)
        (OpaqueIgnitor(FreqIgnitor) * OpaqueIgnitor(ParamIgnitor("track", 1.9)))
            .generate(fresh, 220.0, ctx())

        for (i in 0 until blockFrames) {
            viaCache[i].toRawBits() shouldBe fresh[i].toRawBits()
        }
    }

    // ── 4. isBlockConstant agrees with the scalar's nullability ──────────────────

    "isBlockConstant agrees with controlRateValueOrNull nullability for every combinator slot" {
        // Table over every combinator with the probe operand x placed in EACH child slot
        // (remaining slots constant). Built twice per entry: x constant (flag must be true,
        // scalar non-null) and x stateful (flag false, scalar null). Kills the formula-typo
        // class where one slot's term is dropped from either override — which would otherwise
        // surface only as a contract breach at runtime.
        val cases: List<Pair<String, (Ignitor) -> Ignitor>> = listOf(
            "plus a" to { x -> x + ConstantIgnitor(0.5) },
            "plus b" to { x -> ConstantIgnitor(0.5) + x },
            "times a" to { x -> x * ConstantIgnitor(0.5) },
            "times b" to { x -> ConstantIgnitor(0.5) * x },
            "mulConst" to { x -> x.mul(0.5) },
            "div a" to { x -> x.div(ConstantIgnitor(0.5)) },
            "div b" to { x -> ConstantIgnitor(0.5).div(x) },
            "minus a" to { x -> x.minus(ConstantIgnitor(0.5)) },
            "minus b" to { x -> ConstantIgnitor(0.5).minus(x) },
            "neg" to { x -> x.neg() },
            "abs" to { x -> x.abs() },
            "pow base" to { x -> x.pow(ConstantIgnitor(2.0)) },
            "pow exp" to { x -> ConstantIgnitor(2.0).pow(x) },
            "min a" to { x -> x.min(ConstantIgnitor(0.5)) },
            "min b" to { x -> ConstantIgnitor(0.5).min(x) },
            "max a" to { x -> x.max(ConstantIgnitor(0.5)) },
            "max b" to { x -> ConstantIgnitor(0.5).max(x) },
            "clamp upstream" to { x -> x.clamp(ConstantIgnitor(-1.0), ConstantIgnitor(1.0)) },
            "clamp lo" to { x -> ConstantIgnitor(0.5).clamp(x, ConstantIgnitor(1.0)) },
            "clamp hi" to { x -> ConstantIgnitor(0.5).clamp(ConstantIgnitor(-1.0), x) },
            "exp" to { x -> x.exp() },
            "log" to { x -> x.log() },
            "memoizing" to { x -> MemoizingIgnitor(x) },
        )
        val c = ctx()
        for ((name, build) in cases) {
            withClue(name) {
                val constant = build(ConstantIgnitor(0.5))
                constant.isBlockConstant.shouldBeTrue()
                constant.controlRateValueOrNull(220.0, c).shouldNotBeNull()

                val stateful = build(Ignitors.sine())
                stateful.isBlockConstant.shouldBeFalse()
                stateful.controlRateValueOrNull(220.0, c).shouldBeNull()
            }
        }
    }

    // ── 5. pulze duty branch parity (hoisted vs per-sample PWM) ───────────────────

    /**
     * Renders [blocks] blocks of the hoisted-branch and PWM-branch pulze side by side, with a
     * per-block frequency schedule (dt moves → the rebake-timing rules of BOTH branches are
     * exercised, not just the steady-state loop bodies).
     */
    fun assertPulzeBranchParity(hoistedDuty: Ignitor, pwmDuty: Ignitor, freqs: List<Double>) {
        val hoisted = Ignitors.pulze(duty = hoistedDuty)
        val pwm = Ignitors.pulze(duty = pwmDuty)
        val ctxH = ctx()
        val ctxP = ctx()
        val bufH = AudioBuffer(blockFrames)
        val bufP = AudioBuffer(blockFrames)
        for (freq in freqs) {
            hoisted.generate(bufH, freq, ctxH)
            pwm.generate(bufP, freq, ctxP)
            for (i in 0 until blockFrames) {
                bufH[i].toRawBits() shouldBe bufP[i].toRawBits()
            }
            ctxH.voiceElapsedFrames += blockFrames
            ctxP.voiceElapsedFrames += blockFrames
        }
    }

    "pulze branches agree under per-block frequency changes (dt moves, duty constant)" {
        fun duty(): Ignitor = ConstantIgnitor(0.6) * ParamIgnitor("d", 0.5)
        assertPulzeBranchParity(
            hoistedDuty = MemoizingIgnitor(duty()),
            pwmDuty = OpaqueIgnitor(duty()),
            freqs = listOf(220.0, 330.0, 220.0, 440.0),
        )
    }

    "pulze hoisted branch is live: a block-constant duty is never rendered" {
        // Branch-parity alone cannot prove the hoisted branch runs — with the gate deleted,
        // both sides take PWM and still agree. The probe pins the gate to true.
        val probe = RenderCountProbe(ConstantIgnitor(0.6) * ParamIgnitor("d", 0.5))
        val p = Ignitors.pulze(duty = probe)
        val c = ctx()
        val buf = AudioBuffer(blockFrames)
        repeat(3) {
            p.generate(buf, 220.0, c)
            c.voiceElapsedFrames += blockFrames
        }
        probe.generateCalls shouldBe 0
    }

    "pulze branches agree with a freq-tracking duty (d and dt move together)" {
        fun duty(): Ignitor = FreqIgnitor * ParamIgnitor("k", 0.001)
        assertPulzeBranchParity(
            hoistedDuty = MemoizingIgnitor(duty()),
            pwmDuty = OpaqueIgnitor(duty()),
            freqs = listOf(220.0, 330.0, 440.0, 220.0),
        )
    }
})
