/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
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
        voiceEndFrame = blockFrames * 4,
        scratchBuffers = ScratchBuffers(blockFrames),
    ).apply {
        offset = 0
        length = blockFrames
        voiceElapsedFrames = 0
    }

    fun firstSample(sig: Ignitor, freqHz: Double): Double {
        val buf = AudioBuffer(blockFrames)
        sig.generate(buf, freqHz, ctx())
        return buf[0]
    }

    // ── leaves report their scalar ──────────────────────────────────────────────

    "ConstantIgnitor returns its value" {
        ConstantIgnitor(2.0).controlRateValueOrNull(0.0, ctx()) shouldBe 2.0
    }

    "ParamIgnitor returns its default" {
        ParamIgnitor("x", 3.0).controlRateValueOrNull(0.0, ctx()) shouldBe 3.0
    }

    "FreqIgnitor returns the voice frequency" {
        FreqIgnitor.controlRateValueOrNull(440.0, ctx()) shouldBe 440.0
    }

    // ── pointwise combinators fold over block-constant children ──────────────────

    "times folds two constants" {
        (ConstantIgnitor(2.0) * ConstantIgnitor(3.0)).controlRateValueOrNull(0.0, ctx()) shouldBe 6.0
    }

    "plus folds two constants" {
        (ConstantIgnitor(2.0) + ConstantIgnitor(3.0)).controlRateValueOrNull(0.0, ctx()) shouldBe 5.0
    }

    "freq-derived expression stays control-rate" {
        // FreqIgnitor * 2 → an octave up, resolvable without a buffer
        (FreqIgnitor * ConstantIgnitor(2.0)).controlRateValueOrNull(220.0, ctx()) shouldBe 440.0
    }

    "folded value matches the rendered first sample" {
        val sig = (FreqIgnitor * ConstantIgnitor(2.0)) + ConstantIgnitor(10.0)
        val cv = sig.controlRateValueOrNull(220.0, ctx())
        cv.shouldNotBeNull()
        cv shouldBe (firstSample(sig, 220.0) plusOrMinus 1e-9)
    }

    // ── stateful nodes are not control-rate ──────────────────────────────────────

    "an oscillator reports null" {
        Ignitors.sine().controlRateValueOrNull(440.0, ctx()) shouldBe null
    }

    "a combinator over a stateful child reports null" {
        (Ignitors.sine() * ConstantIgnitor(2.0)).controlRateValueOrNull(440.0, ctx()) shouldBe null
    }

    // ── blockStartValue falls back to a one-sample render for stateful nodes ──────

    "blockStartValue of an oscillator equals its first rendered sample" {
        val osc = Ignitors.sine()
        osc.blockStartValue(440.0, ctx()) shouldBe (firstSample(Ignitors.sine(), 440.0) plusOrMinus 1e-9)
    }
})
