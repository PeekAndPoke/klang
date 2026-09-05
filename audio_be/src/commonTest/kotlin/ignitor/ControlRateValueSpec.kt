/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer

/**
 * Guards the [Ignitor.controlRateValueOrNull] / [Ignitor.blockStartValue] contract that replaced the
 * `ControlRateIgnitor` marker: block-constant leaves and pure pointwise combinators hand back their
 * scalar without rendering a buffer; stateful nodes report `null` and fall back to a one-sample render.
 */
class ControlRateValueSpec : StringSpec({

    val blockFrames = 64

    fun ctx(): IgniteContext = IgniteContext(
        sampleRate = 44100,
        voiceDurationFrames = blockFrames * 4,
        gateEndFrame = blockFrames * 4,
        releaseFrames = 0,
        scratchBuffers = ScratchBuffers(blockFrames),
    ).apply {
        updateOffsetAndLength(0, blockFrames)
        voiceElapsedFrames = 0
    }

    fun firstSample(sig: Ignitor, freqHz: Double): Double {
        val buf = AudioBuffer(blockFrames)
        sig.generate(buf, freqHz, ctx())
        return buf[0]
    }

    // ── leaves report their scalar ──────────────────────────────────────────────

    "ConstantIgnitor returns its value" {
        ConstantIgnitor(2.0).controlRateValueOrNull(0.0) shouldBe 2.0
    }

    "ParamIgnitor returns its default" {
        ParamIgnitor("x", 3.0).controlRateValueOrNull(0.0) shouldBe 3.0
    }

    "FreqIgnitor returns the voice frequency" {
        FreqIgnitor.controlRateValueOrNull(440.0) shouldBe 440.0
    }

    // ── pointwise combinators fold over block-constant children ──────────────────

    "times folds two constants" {
        (ConstantIgnitor(2.0) * ConstantIgnitor(3.0)).controlRateValueOrNull(0.0) shouldBe 6.0
    }

    "plus folds two constants" {
        (ConstantIgnitor(2.0) + ConstantIgnitor(3.0)).controlRateValueOrNull(0.0) shouldBe 5.0
    }

    "freq-derived expression stays control-rate" {
        // FreqIgnitor * 2 → an octave up, resolvable without a buffer
        (FreqIgnitor * ConstantIgnitor(2.0)).controlRateValueOrNull(220.0) shouldBe 440.0
    }

    "folded value matches the rendered first sample" {
        val sig = (FreqIgnitor * ConstantIgnitor(2.0)) + ConstantIgnitor(10.0)
        val cv = sig.controlRateValueOrNull(220.0)
        cv.shouldNotBeNull()
        cv shouldBe (firstSample(sig, 220.0) plusOrMinus 1e-9)
    }

    // ── stateful nodes are not control-rate ──────────────────────────────────────

    "an oscillator reports null" {
        Ignitors.sine().controlRateValueOrNull(440.0) shouldBe null
    }

    "a combinator over a stateful child reports null" {
        (Ignitors.sine() * ConstantIgnitor(2.0)).controlRateValueOrNull(440.0) shouldBe null
    }

    // ── blockStartValue falls back to a one-sample render for stateful nodes ──────

    "blockStartValue of an oscillator equals its first rendered sample" {
        val osc = Ignitors.sine()
        osc.blockStartValue(440.0, ctx()) shouldBe (firstSample(Ignitors.sine(), 440.0) plusOrMinus 1e-9)
    }

    "blockStartValue on a ZERO-LENGTH window is deterministic, never stale scratch (ledger E5)" {
        // A zero-length block renders nothing, so the scratch fallback used to return whatever a
        // previous node left at tmp[ctx.offset] — arbitrary and run-to-run nondeterministic.
        // Reachable: legato can clip a gate to 0 frames and Voice.render still runs the pipeline.
        val ctx0 = ctx().apply { updateLength(0) }
        // Dirty the pool first so a stale read would be visibly nonzero.
        ctx0.scratchBuffers.use { tmp -> tmp.fill(0.77) }
        // A stateful node (sine) has no control-rate value, so this takes the scratch fallback.
        Ignitors.sine().blockStartValue(440.0, ctx0) shouldBe 0.0
    }
})
