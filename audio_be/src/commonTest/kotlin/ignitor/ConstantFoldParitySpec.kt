/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer

/**
 * Bit-parity guards for the constant-fold in [PlusIgnitor]/[TimesIgnitor] `generate()`
 * (unified-eq plan, D1a step 2): a block-constant operand skips the scratch-buffer render and
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
        // this branch at offset != 0 through NON-folding parents (clamp/div/minus render their
        // block-constant children via generate on mid-block onsets).
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
    }

    // ── nesting: a fold rendering into an ANCESTOR's scratch slot ─────────────────

    "nested fold: inner plus folds while writing the outer times' scratch buffer" {
        assertBitParity(
            folded = Ignitors.sine() * (ParamIgnitor("k", 0.5) + Ignitors.sine()),
            reference = Ignitors.sine() * (OpaqueIgnitor(ParamIgnitor("k", 0.5)) + Ignitors.sine()),
        )
    }
})
