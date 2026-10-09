/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.utils.SAFE_MAX

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import kotlin.math.abs
import kotlin.random.Random

/** This file's one seeded stream: every run draws the same, and successive builds still draw
 *  differently (as they did from the process-wide stream these calls used before). */
private val testRandom = Random(0x5EED)

/**
 * Safety tests for pitch-mod factories — ensures extreme user inputs don't produce
 * `NaN`/`Inf` that would poison oscillator phase accumulators. See
 * `audio/ref/numerical-safety.md` for the contract.
 *
 * The `2.0.pow(...)` arithmetic and `effectiveDepth / freqHz` division in
 * [vibratoModIgnitor], [accelerateModIgnitor], [pitchEnvelopeModIgnitor], and
 * [fmModIgnitor] are now wrapped in `safeOut` / `safeDiv`.
 */
class PitchModSafetyTest : StringSpec({

    val sampleRate = 44100
    val blockFrames = 256

    fun ctx(elapsedFrames: Int = 0, durationFrames: Int = sampleRate): IgniteContext = IgniteContext(
        sampleRate = sampleRate,
        voiceDurationFrames = durationFrames,
        gateEndFrame = durationFrames,
        scratchBuffers = ScratchBuffers(blockFrames),
        random = testRandom,
    ).apply {
        updateOffsetAndLength(offset = 0, length = blockFrames)
        voiceElapsedFrames = elapsedFrames
    }

    fun render(sig: Ignitor, freqHz: Double = 440.0, c: IgniteContext = ctx()): AudioBuffer {
        val buf = AudioBuffer(blockFrames)
        sig.generate(buf, freqHz, c)
        return buf
    }

    fun AudioBuffer.allFinite(): Boolean = this.all { !it.isNaN() && !it.isInfinite() }

    fun AudioBuffer.allInBounds(): Boolean = this.all { abs(it) <= SAFE_MAX }

    // ═════════════════════════════════════════════════════════════════════════════
    // Vibrato
    // ═════════════════════════════════════════════════════════════════════════════

    "vibrato with normal depth produces ratios near 1.0" {
        val sig = vibratoModIgnitor(rate = 5.0, semitones = 1.0)
        val out = render(sig)
        out.allFinite() shouldBe true
        // 1 semitone = ~5.95% pitch change; ratio in [2^(-1/12), 2^(1/12)] ≈ [0.944, 1.059]
        out.all { it in 0.93..1.07 } shouldBe true
    }

    "vibrato with extreme depthSemitones stays finite" {
        // depthSemitones = 10000 → 2^(±833) easily overflows Float.
        val sig = vibratoModIgnitor(rate = 5.0, semitones = 10000.0)
        val out = render(sig)
        out.allFinite() shouldBe true
        out.allInBounds() shouldBe true
    }

    "vibrato with zero depth outputs exactly 1.0" {
        val sig = vibratoModIgnitor(rate = 5.0, semitones = 0.0)
        val out = render(sig)
        out.all { it == 1.0 } shouldBe true
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Accelerate
    // ═════════════════════════════════════════════════════════════════════════════

    "accelerate with small amount produces graduated ratios" {
        val sig = accelerateModIgnitor(semitones = 1.0)
        val out = render(sig)
        out.allFinite() shouldBe true
        out[0] shouldBe (1.0 plusOrMinus 0.01)  // start ≈ 1
    }

    "accelerate with extreme amount stays finite at end of voice" {
        // 120000 semitones = 10000 octaves → ratio reaches 2^10000 → Inf in Double, must clamp.
        val sig = accelerateModIgnitor(semitones = 120000.0)
        // Render block from near the end of the voice, when ratio has fully accumulated.
        val out = render(sig, c = ctx(elapsedFrames = sampleRate - blockFrames))
        out.allFinite() shouldBe true
        out.allInBounds() shouldBe true
    }

    "accelerate with zero amount outputs exactly 1.0" {
        val sig = accelerateModIgnitor(semitones = 0.0)
        val out = render(sig)
        out.all { it == 1.0 } shouldBe true
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Pitch envelope
    // ═════════════════════════════════════════════════════════════════════════════

    "pitch envelope with extreme amount stays finite" {
        // amount = 1000 semitones — way past Float pow overflow at envLevel=1.
        val sig = pitchEnvelopeModIgnitor(
            attack = ParamIgnitor("att", 0.01),
            decay = ParamIgnitor("dec", 0.1),
            semitones = ParamIgnitor("amt", 1000.0),
        )
        val out = render(sig)
        out.allFinite() shouldBe true
        out.allInBounds() shouldBe true
    }

    "pitch envelope with zero amount outputs exactly 1.0" {
        val sig = pitchEnvelopeModIgnitor(
            attack = ParamIgnitor("att", 0.01),
            decay = ParamIgnitor("dec", 0.1),
            semitones = ParamIgnitor("amt", 0.0),
        )
        val out = render(sig)
        out.all { it == 1.0 } shouldBe true
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // FM
    // ═════════════════════════════════════════════════════════════════════════════

    "fm at normal frequency produces finite ratios" {
        val sig = fmModIgnitor(
            modulator = Ignitors.sine(),
            ratio = ParamIgnitor("ratio", 1.0),
            depth = ParamIgnitor("depth", 100.0),
        )
        val out = render(sig, freqHz = 440.0)
        out.allFinite() shouldBe true
    }

    "fm at sub-Hz freqHz stays finite (safeDiv on freqHz)" {
        // Heavy detune toward 0 → tiny freqHz → effectiveDepth/freqHz would explode.
        val sig = fmModIgnitor(
            modulator = Ignitors.sine(),
            ratio = ParamIgnitor("ratio", 1.0),
            depth = ParamIgnitor("depth", 1000.0),
        )
        val out = render(sig, freqHz = 1e-20)
        out.allFinite() shouldBe true
        out.allInBounds() shouldBe true
    }

    "fm at zero freqHz uses bypass path" {
        val sig = fmModIgnitor(
            modulator = Ignitors.sine(),
            ratio = ParamIgnitor("ratio", 1.0),
            depth = ParamIgnitor("depth", 100.0),
        )
        val out = render(sig, freqHz = 0.0)
        // The resolved fm freq <= 0 short-circuits to all-1.0 output.
        out.all { it == 1.0 } shouldBe true
    }

    "the bypass gates on the RESOLVED fm freq, not the argument: absolute zero bypasses a live note" {
        // Pins the freq-param anchor of the bypass (review round 1: with the default the two
        // values coincide and no row could tell them apart). The modulator is ABSOLUTE for the
        // same reason as the NaN row: a default-freq modulator driven at fm freq 0 outputs
        // silence and would mask an engaged mutant behind an all-1.0 render.
        val sig = fmModIgnitor(
            modulator = Ignitors.sine(ConstantIgnitor(300.0)),
            ratio = ParamIgnitor("ratio", 1.0),
            depth = ParamIgnitor("depth", 100.0),
            freq = ConstantIgnitor(0.0),
        )
        val out = render(sig, freqHz = 440.0)
        out.all { it == 1.0 } shouldBe true
    }

    "a NaN fm freq reads as note-less silence, never as an engaged poisoned divisor" {
        // The `!(f > 0.0)` NaN-guard form of the bypass: NaN must land in the all-1.0 arm.
        // The modulator is ABSOLUTE so an engaged mutant is loud (a default-freq modulator
        // would wrap the NaN drive to silence and mask the difference).
        val sig = fmModIgnitor(
            modulator = Ignitors.sine(ConstantIgnitor(300.0)),
            ratio = ParamIgnitor("ratio", 1.0),
            depth = ParamIgnitor("depth", 100.0),
            freq = ConstantIgnitor(Double.NaN),
        )
        val out = render(sig, freqHz = 440.0)
        out.all { it == 1.0 } shouldBe true
    }

    "the bypass gates on the RESOLVED fm freq: absolute positive engages on a note-less voice" {
        val sig = fmModIgnitor(
            modulator = Ignitors.sine(),
            ratio = ParamIgnitor("ratio", 1.0),
            depth = ParamIgnitor("depth", 100.0),
            freq = ConstantIgnitor(440.0),
        )
        val out = render(sig, freqHz = 0.0)
        // Real FM output: not the all-1.0 bypass.
        out.any { it != 1.0 } shouldBe true
        out.allFinite() shouldBe true
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Composition: extreme pitch-mod feeding an oscillator does NOT silence it
    // ═════════════════════════════════════════════════════════════════════════════

    "extreme vibrato through ModApplyingIgnitor keeps oscillator alive" {
        // Without the safety clamp, an extreme depth would set phase=Inf on first sample
        // and the oscillator would output 0/NaN forever. With the clamp, output stays bounded.
        val mod = vibratoModIgnitor(rate = 5.0, semitones = 10000.0)
        val osc = ModApplyingIgnitor(inner = Ignitors.sine(), mod = mod)
        val out = render(osc, freqHz = 440.0)
        out.allFinite() shouldBe true
        // Output isn't silent — at least one sample is non-zero (oscillator is still running).
        out.any { it != 0.0 } shouldBe true
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // A non-finite pitch amount reads as unset (docs/tasks-archive/2026-10/20261007-bugfix-ignitor-non-finite-pitch-amount.md)
    // ═════════════════════════════════════════════════════════════════════════════

    /** Renders [dsl] as a voice at 220 Hz, 16 blocks of the pinned 128 frames. */
    fun renderVoice(dsl: IgnitorDsl): DoubleArray {
        val frames = 128
        val blocks = 16
        val c = IgniteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = frames * blocks,
            gateEndFrame = frames * blocks,
            scratchBuffers = ScratchBuffers(frames),
            random = testRandom,
        )
        val ignitor = dsl.buildExciter(freqHz = 220.0, sampleRate = sampleRate, random = testRandom).ignitor
        val buf = AudioBuffer(frames)
        val out = DoubleArray(frames * blocks)

        for (b in 0 until blocks) {
            c.updateOffsetAndLength(offset = 0, length = frames)
            ignitor.generate(buf, 220.0, c)
            buf.copyInto(destination = out, destinationOffset = b * frames, startIndex = 0, endIndex = frames)
            c.voiceElapsedFrames += frames
        }

        return out
    }

    fun k(v: Double) = IgnitorDsl.Constant(v)

    val nan = k(Double.NaN)

    // The same NaN as a NON-LEAF knob. A `Constant` or `Param` leaf at a non-finite value is gated off at build
    // for accelerate, the pitch envelope and fm (pitch pipeline step 0, `audio/ref/off-values.md`), so a leaf NaN
    // never reaches the runtime's `finiteOr` these rows guard. The marker dissolves to the NaN at build but has no
    // build-time answer for the gate. Not `Times`: its `safeOut` would scrub the NaN to 0 before the node sees it.
    val nonLeafNan = IgnitorDsl.OptimizerHint(inner = nan)
    val saw = IgnitorDsl.Saw(analog = k(0.0))
    val fmCarrier = IgnitorDsl.Sine(analog = k(0.0))
    // Note-pitched, so a NaN ratio reaches its drive (an absolute modulator would ignore the ratio).
    val fmModulator = IgnitorDsl.Sine(analog = k(0.0))

    // Each row: the knob NaN, against the same node with the knob left at its default. Raw, every NaN row
    // freezes the oscillator (a DC hold) or, for the vibrato rate, drops the vibrato.
    listOf(
        Triple(
            "pitch envelope semitones",
            IgnitorDsl.PitchEnvelope(saw, semitones = nonLeafNan),
            IgnitorDsl.PitchEnvelope(saw),
        ),
        Triple(
            "fm ratio",
            IgnitorDsl.Fm(carrier = fmCarrier, modulator = fmModulator, ratio = nan, depth = k(300.0)),
            IgnitorDsl.Fm(carrier = fmCarrier, modulator = fmModulator, depth = k(300.0)),
        ),
        Triple(
            "fm depth",
            IgnitorDsl.Fm(carrier = fmCarrier, modulator = fmModulator, ratio = k(2.0), depth = nonLeafNan),
            IgnitorDsl.Fm(carrier = fmCarrier, modulator = fmModulator, ratio = k(2.0)),
        ),
        Triple(
            "vibrato rate",
            IgnitorDsl.Vibrato(saw, rate = nan, semitones = k(1.0)),
            IgnitorDsl.Vibrato(saw, semitones = k(1.0)),
        ),
        Triple(
            "vibrato semitones",
            IgnitorDsl.Vibrato(saw, rate = k(7.0), semitones = nan),
            IgnitorDsl.Vibrato(saw, rate = k(7.0)),
        ),
        Triple(
            "accelerate semitones",
            IgnitorDsl.Accelerate(saw, semitones = nonLeafNan),
            IgnitorDsl.Accelerate(saw),
        ),
    ).forEach { (knob, withNan, withDefault) ->
        "a NaN $knob reads as unset: the voice keeps sounding and equals the default-knob render" {
            val got = renderVoice(withNan)
            val expected = renderVoice(withDefault)

            got.maxOf { abs(it) } shouldBeGreaterThan 0.1
            got.toList() shouldBe expected.toList()
        }
    }
})
