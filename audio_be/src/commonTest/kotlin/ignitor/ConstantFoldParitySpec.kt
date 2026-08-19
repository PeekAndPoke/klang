/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.SAFE_MAX
import io.peekandpoke.klang.audio_be.SAFE_MIN

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer

/**
 * Bit-parity guards for the constant-fold in the binary combinators' `generate()` — Plus/Times
 * (D1a) and Minus/Div/Mod/Min/Max/Pow plus the Clamp/Range/Lerp constant slots (D1b): a
 * block-constant operand skips the scratch-buffer render and
 * folds as a scalar. The reference is the SAME expression with the foldable operand behind an
 * [OpaqueIgnitor] (the pre-fold scratch path). Fold and reference must agree
 * `toRawBits()`-exactly on every sample, across multiple blocks (stateful operands advance
 * identically on both paths) AND on sub-block windows (`offset != 0`, partial `length` — the
 * mid-block note-onset case; a fold loop written against `0 until length` passes every
 * full-block case and breaks exactly there).
 */
class ConstantFoldParitySpec : StringSpec({

    val blockFrames = 128
    val blocks = 4

    fun ctx(offset: Int = 0, length: Int = blockFrames): IgniteContext = IgniteContext(
        sampleRate = 44100,
        voiceDurationFrames = blockFrames * 8,
        gateEndFrame = blockFrames * 8,
        releaseFrames = 0,
        voiceEndFrame = blockFrames * 8,
        scratchBuffers = ScratchBuffers(blockFrames),
    ).apply {
        this.offset = offset
        this.length = length
        voiceElapsedFrames = 0
    }

    /** Renders [blocks] full blocks of both signals and asserts per-sample bit equality. */
    fun assertBitParity(folded: Ignitor, reference: Ignitor, freqHz: Double = 220.0) {
        val ctxF = ctx()
        val ctxR = ctx()
        val bufF = AudioBuffer(blockFrames)
        val bufR = AudioBuffer(blockFrames)
        repeat(blocks) {
            folded.generate(bufF, freqHz, ctxF)
            reference.generate(bufR, freqHz, ctxR)
            for (i in 0 until blockFrames) {
                bufF[i].toRawBits() shouldBe bufR[i].toRawBits()
            }
            ctxF.voiceElapsedFrames += blockFrames
            ctxR.voiceElapsedFrames += blockFrames
        }
    }

    /**
     * Renders ONE sub-block window (`offset=37, length=64`) as the FIRST call on fresh signals
     * and asserts bit parity inside the window and untouched sentinels outside it. Matches the
     * production mid-block-onset ctx shape: `voiceElapsedFrames == -offset` (Voice.kt computes
     * `blockStart - startFrame` for a voice starting inside the block, never 0); the fold
     * decision reads neither field, but the spec should reproduce reality, not an idealization.
     */
    fun assertSubBlockParity(folded: Ignitor, reference: Ignitor, freqHz: Double = 220.0) {
        val offset = 37
        val length = 64
        val sentinel = 123.456
        val bufF = AudioBuffer(blockFrames).apply { fill(sentinel) }
        val bufR = AudioBuffer(blockFrames).apply { fill(sentinel) }
        folded.generate(bufF, freqHz, ctx(offset, length).apply { voiceElapsedFrames = -offset })
        reference.generate(bufR, freqHz, ctx(offset, length).apply { voiceElapsedFrames = -offset })
        for (i in 0 until blockFrames) {
            if (i in offset until offset + length) {
                bufF[i].toRawBits() shouldBe bufR[i].toRawBits()
            } else {
                bufF[i] shouldBe sentinel
                bufR[i] shouldBe sentinel
            }
        }
    }

    // ── times ─────────────────────────────────────────────────────────────────────

    "times: right-constant fold is bit-equal to the scratch path" {
        assertBitParity(
            folded = Ignitors.sine() * ParamIgnitor("g", 0.73),
            reference = Ignitors.sine() * OpaqueIgnitor(ParamIgnitor("g", 0.73)),
        )
    }

    "times: left-constant fold is bit-equal to the scratch path" {
        assertBitParity(
            folded = ParamIgnitor("g", 0.73) * Ignitors.sine(),
            reference = OpaqueIgnitor(ParamIgnitor("g", 0.73)) * Ignitors.sine(),
        )
    }

    "times: both-constant fold is bit-equal to the scratch path" {
        assertBitParity(
            folded = ConstantIgnitor(0.37) * ParamIgnitor("g", -2.6),
            reference = OpaqueIgnitor(ConstantIgnitor(0.37)) * OpaqueIgnitor(ParamIgnitor("g", -2.6)),
        )
    }

    "times: both-constant fill applies safeOut once and clamps at SAFE_MAX" {
        // Mutation anchor for the both-const fill branch: dropping its safeOut fails here
        // (the single-const anchors below cannot see this branch).
        assertBitParity(
            folded = ConstantIgnitor(1e10) * ParamIgnitor("g", 1e10),
            reference = OpaqueIgnitor(ConstantIgnitor(1e10)) * OpaqueIgnitor(ParamIgnitor("g", 1e10)),
        )
        val buf = AudioBuffer(blockFrames)
        (ConstantIgnitor(1e10) * ParamIgnitor("g", 1e10)).generate(buf, 220.0, ctx())
        for (i in 0 until blockFrames) {
            buf[i] shouldBe SAFE_MAX
        }
    }

    "times: composite operand folds through MemoizingIgnitor and stays bit-equal" {
        fun composite(): Ignitor = FreqIgnitor * ParamIgnitor("track", 1.9)
        assertBitParity(
            folded = Ignitors.sine() * MemoizingIgnitor(composite()),
            reference = Ignitors.sine() * OpaqueIgnitor(composite()),
        )
    }

    "times: safeOut clamps the RIGHT-folded product at ±SAFE_MAX exactly like the scratch path" {
        val folded = Ignitors.sine() * ParamIgnitor("g", 1e20)
        val reference = Ignitors.sine() * OpaqueIgnitor(ParamIgnitor("g", 1e20))
        assertBitParity(folded, reference)

        // The clamp must actually engage: a 220 Hz block contains both polarities driven
        // beyond ±SAFE_MAX. (Since the helper extraction, both single-const arms share ONE
        // mulConstInPlace safeOut — this case and the LEFT variant below independently anchor
        // WHICH operand renders into the buffer, not two separate clamps.)
        val buf = AudioBuffer(blockFrames)
        (Ignitors.sine() * ParamIgnitor("g", 1e20)).generate(buf, 220.0, ctx())
        (0 until blockFrames).any { buf[it] == SAFE_MAX }.shouldBeTrue()
        (0 until blockFrames).any { buf[it] == -SAFE_MAX }.shouldBeTrue()
    }

    "times: safeOut clamps the LEFT-folded product at ±SAFE_MAX exactly like the scratch path" {
        val folded = ParamIgnitor("g", 1e20) * Ignitors.sine()
        val reference = OpaqueIgnitor(ParamIgnitor("g", 1e20)) * Ignitors.sine()
        assertBitParity(folded, reference)

        // (Shares mulConstInPlace's safeOut with the RIGHT variant above — kept because it
        // anchors the a-fold's operand routing, which the b-fold case cannot see.)
        val buf = AudioBuffer(blockFrames)
        (ParamIgnitor("g", 1e20) * Ignitors.sine()).generate(buf, 220.0, ctx())
        (0 until blockFrames).any { buf[it] == SAFE_MAX }.shouldBeTrue()
        (0 until blockFrames).any { buf[it] == -SAFE_MAX }.shouldBeTrue()
    }

    "times: sub-block window folds bit-identically and leaves the rest untouched" {
        assertSubBlockParity(
            folded = Ignitors.sine() * ParamIgnitor("g", 0.73),
            reference = Ignitors.sine() * OpaqueIgnitor(ParamIgnitor("g", 0.73)),
        )
        assertSubBlockParity(
            folded = ParamIgnitor("g", 0.73) * Ignitors.sine(),
            reference = OpaqueIgnitor(ParamIgnitor("g", 0.73)) * Ignitors.sine(),
        )
        // The both-const FILL branch has its own window arithmetic (buffer.fill with explicit
        // offsets) — a fill(k, 0, length) typo passes every full-block case; production reaches
        // this branch at offset != 0 through the remaining NON-folding parent slots (as of D1b:
        // Clamp/Range upstream, Lerp a/b under a varying t, Select's children, the graph root).
        assertSubBlockParity(
            folded = ConstantIgnitor(0.37) * ParamIgnitor("g", -2.6),
            reference = OpaqueIgnitor(ConstantIgnitor(0.37)) * OpaqueIgnitor(ParamIgnitor("g", -2.6)),
        )
    }

    // ── plus ──────────────────────────────────────────────────────────────────────

    "plus: right-constant fold is bit-equal to the scratch path" {
        assertBitParity(
            folded = Ignitors.sine() + ParamIgnitor("dc", 0.31),
            reference = Ignitors.sine() + OpaqueIgnitor(ParamIgnitor("dc", 0.31)),
        )
    }

    "plus: left-constant fold is bit-equal to the scratch path" {
        assertBitParity(
            folded = ParamIgnitor("dc", 0.31) + Ignitors.sine(),
            reference = OpaqueIgnitor(ParamIgnitor("dc", 0.31)) + Ignitors.sine(),
        )
    }

    "plus: both-constant asymmetric fold is bit-equal to the scratch path" {
        assertBitParity(
            folded = ConstantIgnitor(0.31) + ParamIgnitor("dc", -0.17),
            reference = OpaqueIgnitor(ConstantIgnitor(0.31)) + OpaqueIgnitor(ParamIgnitor("dc", -0.17)),
        )
    }

    "plus: composite operand folds through MemoizingIgnitor and stays bit-equal" {
        fun composite(): Ignitor = FreqIgnitor * ParamIgnitor("track", 0.001)
        assertBitParity(
            folded = Ignitors.sine() + MemoizingIgnitor(composite()),
            reference = Ignitors.sine() + OpaqueIgnitor(composite()),
        )
    }

    "plus: stays BARE above SAFE_MAX — a spurious clamp would show here" {
        // Pins the deliberate Plus/Times asymmetry (per-op safety table): sums may exceed
        // SAFE_MAX; adding safeOut to the fill or to addConstInPlace goes red here.
        val bufFill = AudioBuffer(blockFrames)
        (ConstantIgnitor(1e15) + ParamIgnitor("dc", 1e15)).generate(bufFill, 220.0, ctx())
        for (i in 0 until blockFrames) {
            bufFill[i] shouldBe 2e15
        }

        // Fold arm: sine scaled to ±SAFE_MAX plus a SAFE_MAX offset sums to ~2e15 at the peaks.
        val bufFold = AudioBuffer(blockFrames)
        (Ignitors.sine().mul(1e15) + ParamIgnitor("dc", 1e15)).generate(bufFold, 220.0, ctx())
        (0 until blockFrames).any { bufFold[it] > SAFE_MAX }.shouldBeTrue()
    }

    "plus: a LEFT-folded -0.0 keeps signed-zero semantics of the scratch path" {
        // sine's zero samples give -0.0 + 0.0 → +0.0; both paths must produce identical bits.
        // Left-folded so this exercises the a-branch the commutativity comment argues for.
        assertBitParity(
            folded = ConstantIgnitor(-0.0) + Ignitors.sine(),
            reference = OpaqueIgnitor(ConstantIgnitor(-0.0)) + Ignitors.sine(),
        )
    }

    "plus: sub-block window folds bit-identically and leaves the rest untouched" {
        assertSubBlockParity(
            folded = Ignitors.sine() + ParamIgnitor("dc", 0.31),
            reference = Ignitors.sine() + OpaqueIgnitor(ParamIgnitor("dc", 0.31)),
        )
        assertSubBlockParity(
            folded = ParamIgnitor("dc", 0.31) + Ignitors.sine(),
            reference = OpaqueIgnitor(ParamIgnitor("dc", 0.31)) + Ignitors.sine(),
        )
        // Both-const FILL branch window arithmetic (see the times case above).
        assertSubBlockParity(
            folded = ConstantIgnitor(0.31) + ParamIgnitor("dc", -0.17),
            reference = OpaqueIgnitor(ConstantIgnitor(0.31)) + OpaqueIgnitor(ParamIgnitor("dc", -0.17)),
        )
    }

    // ── D1b ops: fold parity (right/left/both) vs fully-opaque scratch oracles ────

    "minus/div/mod/min/max/pow: fold parity in all three arms (batch)" {
        // Each triple: (folded right-const, folded left-const, folded both-const) vs the same
        // expression fully opacified. Values chosen so per-op guards see ordinary magnitudes;
        // engagement extremes have their own cases below.
        fun sine() = Ignitors.sine()
        val cases: List<Pair<Ignitor, Ignitor>> = listOf(
            sine().minus(ParamIgnitor("k", 0.4)) to sine().minus(OpaqueIgnitor(ParamIgnitor("k", 0.4))),
            ParamIgnitor("k", 0.4).minus(sine()) to OpaqueIgnitor(ParamIgnitor("k", 0.4)).minus(sine()),
            ConstantIgnitor(0.9).minus(ParamIgnitor("k", 0.4)) to
                OpaqueIgnitor(ConstantIgnitor(0.9)).minus(OpaqueIgnitor(ParamIgnitor("k", 0.4))),

            sine().div(ParamIgnitor("k", 0.4)) to sine().div(OpaqueIgnitor(ParamIgnitor("k", 0.4))),
            ParamIgnitor("k", 0.4).div(sine()) to OpaqueIgnitor(ParamIgnitor("k", 0.4)).div(sine()),
            ConstantIgnitor(0.9).div(ParamIgnitor("k", 0.4)) to
                OpaqueIgnitor(ConstantIgnitor(0.9)).div(OpaqueIgnitor(ParamIgnitor("k", 0.4))),

            sine().mod(ParamIgnitor("k", 0.4)) to sine().mod(OpaqueIgnitor(ParamIgnitor("k", 0.4))),
            ParamIgnitor("k", 0.4).mod(sine()) to OpaqueIgnitor(ParamIgnitor("k", 0.4)).mod(sine()),
            ConstantIgnitor(0.9).mod(ParamIgnitor("k", 0.4)) to
                OpaqueIgnitor(ConstantIgnitor(0.9)).mod(OpaqueIgnitor(ParamIgnitor("k", 0.4))),

            sine().min(ParamIgnitor("k", 0.4)) to sine().min(OpaqueIgnitor(ParamIgnitor("k", 0.4))),
            ParamIgnitor("k", 0.4).min(sine()) to OpaqueIgnitor(ParamIgnitor("k", 0.4)).min(sine()),
            ConstantIgnitor(0.9).min(ParamIgnitor("k", 0.4)) to
                OpaqueIgnitor(ConstantIgnitor(0.9)).min(OpaqueIgnitor(ParamIgnitor("k", 0.4))),

            sine().max(ParamIgnitor("k", 0.4)) to sine().max(OpaqueIgnitor(ParamIgnitor("k", 0.4))),
            ParamIgnitor("k", 0.4).max(sine()) to OpaqueIgnitor(ParamIgnitor("k", 0.4)).max(sine()),
            ConstantIgnitor(0.9).max(ParamIgnitor("k", 0.4)) to
                OpaqueIgnitor(ConstantIgnitor(0.9)).max(OpaqueIgnitor(ParamIgnitor("k", 0.4))),

            sine().pow(ParamIgnitor("k", 2.0)) to sine().pow(OpaqueIgnitor(ParamIgnitor("k", 2.0))),
            ParamIgnitor("k", -0.7).pow(sine().abs()) to
                OpaqueIgnitor(ParamIgnitor("k", -0.7)).pow(sine().abs()),
            ConstantIgnitor(-0.7).pow(ParamIgnitor("k", 2.0)) to
                OpaqueIgnitor(ConstantIgnitor(-0.7)).pow(OpaqueIgnitor(ParamIgnitor("k", 2.0))),
        )
        for ((folded, reference) in cases) {
            assertBitParity(folded, reference)
        }
    }

    "min/max: NaN and signed-zero ordering matches the scratch path in every arm" {
        // The comment invariant made load-bearing: `a` stays the FIRST comparison operand.
        // A hoist/reorder refactor flips results exactly when an operand is NaN or +-0.0 —
        // values no other case feeds these ops.
        val nan = Double.NaN
        val cases: List<Pair<Ignitor, Ignitor>> = listOf(
            Ignitors.sine().min(ParamIgnitor("k", nan)) to
                Ignitors.sine().min(OpaqueIgnitor(ParamIgnitor("k", nan))),
            ParamIgnitor("k", nan).min(Ignitors.sine()) to
                OpaqueIgnitor(ParamIgnitor("k", nan)).min(Ignitors.sine()),
            Ignitors.sine().max(ParamIgnitor("k", nan)) to
                Ignitors.sine().max(OpaqueIgnitor(ParamIgnitor("k", nan))),
            ParamIgnitor("k", nan).max(Ignitors.sine()) to
                OpaqueIgnitor(ParamIgnitor("k", nan)).max(Ignitors.sine()),
            ConstantIgnitor(0.0).min(ParamIgnitor("k", -0.0)) to
                OpaqueIgnitor(ConstantIgnitor(0.0)).min(OpaqueIgnitor(ParamIgnitor("k", -0.0))),
            ConstantIgnitor(-0.0).max(ParamIgnitor("k", 0.0)) to
                OpaqueIgnitor(ConstantIgnitor(-0.0)).max(OpaqueIgnitor(ParamIgnitor("k", 0.0))),
        )
        for ((folded, reference) in cases) {
            assertBitParity(folded, reference)
        }
    }

    "all D1b fold arms window correctly on sub-block renders (batch)" {
        // EVERY hand-written windowed loop (checklist item 4): each op's a-arm, b-arm and fill,
        // plus the ternary fold loops — none share the D1a helpers, so each needs its own case.
        fun sine() = Ignitors.sine()
        fun k(v: Double) = ParamIgnitor("k", v)
        fun ok(v: Double) = OpaqueIgnitor(ParamIgnitor("k", v))
        val cases: List<Pair<Ignitor, Ignitor>> = listOf(
            sine().minus(k(0.4)) to sine().minus(ok(0.4)),
            k(0.4).minus(sine()) to ok(0.4).minus(sine()),
            ConstantIgnitor(0.9).minus(k(0.4)) to OpaqueIgnitor(ConstantIgnitor(0.9)).minus(ok(0.4)),
            sine().div(k(0.4)) to sine().div(ok(0.4)),
            k(0.4).div(sine()) to ok(0.4).div(sine()),
            ConstantIgnitor(0.9).div(k(0.4)) to OpaqueIgnitor(ConstantIgnitor(0.9)).div(ok(0.4)),
            sine().mod(k(0.4)) to sine().mod(ok(0.4)),
            k(0.4).mod(sine()) to ok(0.4).mod(sine()),
            ConstantIgnitor(0.9).mod(k(0.4)) to OpaqueIgnitor(ConstantIgnitor(0.9)).mod(ok(0.4)),
            sine().min(k(0.4)) to sine().min(ok(0.4)),
            k(0.4).min(sine()) to ok(0.4).min(sine()),
            ConstantIgnitor(0.9).min(k(0.4)) to OpaqueIgnitor(ConstantIgnitor(0.9)).min(ok(0.4)),
            sine().max(k(0.4)) to sine().max(ok(0.4)),
            k(0.4).max(sine()) to ok(0.4).max(sine()),
            ConstantIgnitor(0.9).max(k(0.4)) to OpaqueIgnitor(ConstantIgnitor(0.9)).max(ok(0.4)),
            sine().pow(k(2.0)) to sine().pow(ok(2.0)),
            k(-0.7).pow(sine().abs()) to ok(-0.7).pow(sine().abs()),
            ConstantIgnitor(-0.7).pow(k(2.0)) to OpaqueIgnitor(ConstantIgnitor(-0.7)).pow(ok(2.0)),
            sine().range(ConstantIgnitor(200.0), ConstantIgnitor(4000.0)) to
                sine().range(OpaqueIgnitor(ConstantIgnitor(200.0)), OpaqueIgnitor(ConstantIgnitor(4000.0))),
            sine().lerp(sine(), ConstantIgnitor(0.3)) to
                sine().lerp(sine(), OpaqueIgnitor(ConstantIgnitor(0.3))),
        )
        for ((folded, reference) in cases) {
            assertSubBlockParity(folded, reference)
        }
    }

    "div: guards engage on the folded arms exactly like the scratch path" {
        // b-const arm: hoisted safeDiv(NaN) -> SAFE_MIN -> safeOut clamps (scratch path agrees).
        assertBitParity(
            folded = Ignitors.sine() .div(ParamIgnitor("k", Double.NaN)),
            reference = Ignitors.sine().div(OpaqueIgnitor(ParamIgnitor("k", Double.NaN))),
        )
        // a-const arm: per-sample safeDiv over a signal crossing zero -> SAFE_MAX peaks; must
        // be bit-equal AND actually clamp.
        assertBitParity(
            folded = ParamIgnitor("k", 1e10).div(Ignitors.sine()),
            reference = OpaqueIgnitor(ParamIgnitor("k", 1e10)).div(Ignitors.sine()),
        )
        val buf = AudioBuffer(blockFrames)
        (ParamIgnitor("k", 1e10).div(Ignitors.sine())).generate(buf, 220.0, ctx())
        (0 until blockFrames).any { buf[it] == SAFE_MAX }.shouldBeTrue()
    }

    "guards engage on the D1b arms at discriminating values (batch)" {
        // Checklist item 3: each guard needs a value where its removal is visible.
        // Div b-const + both-const: products beyond SAFE_MAX must clamp.
        assertBitParity(
            folded = Ignitors.sine().mul(1e10).div(ParamIgnitor("k", 1e-10)),
            reference = Ignitors.sine().mul(1e10).div(OpaqueIgnitor(ParamIgnitor("k", 1e-10))),
        )
        val divB = AudioBuffer(blockFrames)
        (Ignitors.sine().mul(1e10).div(ParamIgnitor("k", 1e-10))).generate(divB, 220.0, ctx())
        (0 until blockFrames).any { divB[it] == SAFE_MAX }.shouldBeTrue()

        val divFill = AudioBuffer(blockFrames)
        (ConstantIgnitor(1e10).div(ParamIgnitor("k", 1e-10))).generate(divFill, 220.0, ctx())
        for (i in 0 until blockFrames) {
            divFill[i] shouldBe SAFE_MAX
        }

        // Pow arms: overflow clamps.
        val powE = AudioBuffer(blockFrames)
        (Ignitors.sine().mul(1e8).pow(ParamIgnitor("k", 4.0))).generate(powE, 220.0, ctx())
        (0 until blockFrames).any { powE[it] == SAFE_MAX }.shouldBeTrue()

        val powB = AudioBuffer(blockFrames)
        (ParamIgnitor("k", 1e8).pow(Ignitors.sine().abs().mul(4.0))).generate(powB, 220.0, ctx())
        (0 until blockFrames).any { powB[it] == SAFE_MAX }.shouldBeTrue()

        // Div fill + a-arm: safeDiv discriminators (safeOut masks safeDiv at big numerators —
        // a NaN divisor separates them on the fill; a SMALL numerator separates them on the
        // a-arm, where the zero-crossing sample gives 1/SAFE_MIN < SAFE_MAX with safeDiv but
        // Inf -> SAFE_MAX without).
        val divNaNFill = AudioBuffer(blockFrames)
        (ConstantIgnitor(1e10).div(ParamIgnitor("k", Double.NaN))).generate(divNaNFill, 220.0, ctx())
        for (i in 0 until blockFrames) {
            divNaNFill[i] shouldBe SAFE_MAX
        }
        assertBitParity(
            folded = ParamIgnitor("k", 1.0).div(Ignitors.sine()),
            reference = OpaqueIgnitor(ParamIgnitor("k", 1.0)).div(Ignitors.sine()),
        )
        val divSmall = AudioBuffer(blockFrames)
        (ParamIgnitor("k", 1.0).div(Ignitors.sine())).generate(divSmall, 220.0, ctx())
        (0 until blockFrames).any { divSmall[it] == 1.0 / SAFE_MIN }.shouldBeTrue()

        // Mod b-const: zero divisor takes the safeDiv substitution (finite, parity holds).
        assertBitParity(
            folded = Ignitors.sine().mod(ParamIgnitor("k", 0.0)),
            reference = Ignitors.sine().mod(OpaqueIgnitor(ParamIgnitor("k", 0.0))),
        )
        val modB = AudioBuffer(blockFrames)
        (Ignitors.sine().mod(ParamIgnitor("k", 0.0))).generate(modB, 220.0, ctx())
        (0 until blockFrames).none { modB[it].isNaN() }.shouldBeTrue()

        // Bare contracts stay bare above SAFE_MAX (a spurious clamp shows here):
        val minusFill = AudioBuffer(blockFrames)
        (ParamIgnitor("k", 1e15).minus(ConstantIgnitor(-1e15))).generate(minusFill, 220.0, ctx())
        for (i in 0 until blockFrames) {
            minusFill[i] shouldBe 2e15
        }
        val minFill = AudioBuffer(blockFrames)
        (ConstantIgnitor(2e15).min(ParamIgnitor("k", 3e15))).generate(minFill, 220.0, ctx())
        for (i in 0 until blockFrames) {
            minFill[i] shouldBe 2e15
        }
        val maxFill = AudioBuffer(blockFrames)
        (ConstantIgnitor(2e15).max(ParamIgnitor("k", 3e15))).generate(maxFill, 220.0, ctx())
        for (i in 0 until blockFrames) {
            maxFill[i] shouldBe 3e15
        }
    }

    "clamp/range: constant bounds fold bit-identically and skip both scratch renders" {
        assertBitParity(
            folded = Ignitors.sine().clamp(ConstantIgnitor(-0.5), ConstantIgnitor(0.5)),
            reference = Ignitors.sine().clamp(OpaqueIgnitor(ConstantIgnitor(-0.5)), OpaqueIgnitor(ConstantIgnitor(0.5))),
        )
        assertBitParity(
            folded = Ignitors.sine().range(ConstantIgnitor(200.0), ConstantIgnitor(4000.0)),
            reference = Ignitors.sine().range(OpaqueIgnitor(ConstantIgnitor(200.0)), OpaqueIgnitor(ConstantIgnitor(4000.0))),
        )
        assertSubBlockParity(
            folded = Ignitors.sine().clamp(ConstantIgnitor(-0.5), ConstantIgnitor(0.5)),
            reference = Ignitors.sine().clamp(OpaqueIgnitor(ConstantIgnitor(-0.5)), OpaqueIgnitor(ConstantIgnitor(0.5))),
        )
        val loProbe = RenderCountProbe(ConstantIgnitor(-0.5))
        val hiProbe = RenderCountProbe(ConstantIgnitor(0.5))
        Ignitors.sine().clamp(loProbe, hiProbe).generate(AudioBuffer(blockFrames), 220.0, ctx())
        loProbe.generateCalls shouldBe 0
        hiProbe.generateCalls shouldBe 0

        val rLoProbe = RenderCountProbe(ConstantIgnitor(200.0))
        val rHiProbe = RenderCountProbe(ConstantIgnitor(4000.0))
        Ignitors.sine().range(rLoProbe, rHiProbe).generate(AudioBuffer(blockFrames), 220.0, ctx())
        rLoProbe.generateCalls shouldBe 0
        rHiProbe.generateCalls shouldBe 0
    }

    "lerp: constant t folds bit-identically and skips its scratch render" {
        assertBitParity(
            folded = Ignitors.sine().lerp(Ignitors.sine(), ConstantIgnitor(0.3)),
            reference = Ignitors.sine().lerp(Ignitors.sine(), OpaqueIgnitor(ConstantIgnitor(0.3))),
        )
        val tProbe = RenderCountProbe(ConstantIgnitor(0.3))
        Ignitors.sine().lerp(Ignitors.sine(), tProbe).generate(AudioBuffer(blockFrames), 220.0, ctx())
        tProbe.generateCalls shouldBe 0
    }

    // ── liveness: the fast paths must actually RUN, not merely agree ──────────────

    "fold liveness: block-constant operands are never rendered" {
        // Output-parity alone cannot prove a fold is alive — deleting a fold branch leaves
        // every parity case green (the scratch path IS the reference). The counting probes pin
        // each branch: generateCalls == 0 iff the fold short-circuited the render.
        fun probe() = RenderCountProbe(ParamIgnitor("k", 0.5))
        fun render(sig: Ignitor) = sig.generate(AudioBuffer(blockFrames), 220.0, ctx())

        val plusB = probe(); render(Ignitors.sine() + plusB); plusB.generateCalls shouldBe 0
        val plusA = probe(); render(plusA + Ignitors.sine()); plusA.generateCalls shouldBe 0
        val plusBoth = probe(); render(plusBoth + ConstantIgnitor(0.5)); plusBoth.generateCalls shouldBe 0
        val timesB = probe(); render(Ignitors.sine() * timesB); timesB.generateCalls shouldBe 0
        val timesA = probe(); render(timesA * Ignitors.sine()); timesA.generateCalls shouldBe 0
        val timesBoth = probe(); render(timesBoth * ConstantIgnitor(0.5)); timesBoth.generateCalls shouldBe 0

        val minusB = probe(); render(Ignitors.sine().minus(minusB)); minusB.generateCalls shouldBe 0
        val minusA = probe(); render(minusA.minus(Ignitors.sine())); minusA.generateCalls shouldBe 0
        val minusBoth = probe(); render(minusBoth.minus(ConstantIgnitor(0.5))); minusBoth.generateCalls shouldBe 0
        val divB = probe(); render(Ignitors.sine().div(divB)); divB.generateCalls shouldBe 0
        val divA = probe(); render(divA.div(Ignitors.sine())); divA.generateCalls shouldBe 0
        val modB = probe(); render(Ignitors.sine().mod(modB)); modB.generateCalls shouldBe 0
        val modA = probe(); render(modA.mod(Ignitors.sine())); modA.generateCalls shouldBe 0
        val minB = probe(); render(Ignitors.sine().min(minB)); minB.generateCalls shouldBe 0
        val minA = probe(); render(minA.min(Ignitors.sine())); minA.generateCalls shouldBe 0
        val maxB = probe(); render(Ignitors.sine().max(maxB)); maxB.generateCalls shouldBe 0
        val maxA = probe(); render(maxA.max(Ignitors.sine())); maxA.generateCalls shouldBe 0
        val powE = probe(); render(Ignitors.sine().pow(powE)); powE.generateCalls shouldBe 0
        val powBase = probe(); render(powBase.pow(Ignitors.sine())); powBase.generateCalls shouldBe 0

        // Both-const FILL arms (deleting one falls into a single-const arm with identical
        // output — only the probe can tell):
        val divBoth = probe(); render(divBoth.div(ConstantIgnitor(0.5))); divBoth.generateCalls shouldBe 0
        val modBoth = probe(); render(modBoth.mod(ConstantIgnitor(0.5))); modBoth.generateCalls shouldBe 0
        val minBoth = probe(); render(minBoth.min(ConstantIgnitor(0.5))); minBoth.generateCalls shouldBe 0
        val maxBoth = probe(); render(maxBoth.max(ConstantIgnitor(0.5))); maxBoth.generateCalls shouldBe 0
        val powBoth = probe(); render(powBoth.pow(ConstantIgnitor(2.0))); powBoth.generateCalls shouldBe 0
    }

    // ── nesting: a fold rendering into an ANCESTOR's scratch slot ─────────────────

    "nested fold: inner plus folds while writing the outer times' scratch buffer" {
        assertBitParity(
            folded = Ignitors.sine() * (ParamIgnitor("k", 0.5) + Ignitors.sine()),
            reference = Ignitors.sine() * (OpaqueIgnitor(ParamIgnitor("k", 0.5)) + Ignitors.sine()),
        )
    }
})
