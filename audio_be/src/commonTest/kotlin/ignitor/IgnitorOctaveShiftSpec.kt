/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_bridge.IgnitorDsl

/**
 * Covers [octaveUp] and [octaveDown], which had no callers and therefore no tests until
 * 2026-08-27 (spotted by the maintainer while reviewing the envelope-ownership work).
 *
 * Both are pitch nodes: they change the `freqHz` their UPSTREAM sees rather than transforming
 * samples, which is why [DetuneIgnitor] deliberately carries no `controlRateValueOrNull`. So the
 * probe here is [FreqIgnitor], whose whole job is to report the frequency handed to it.
 *
 * Not to be confused with the klangscript `octaveUp`/`octaveDown` at
 * `KlangScriptOscExtensions.kt`, which build an `IgnitorDsl.Detune` node on the DSL side.
 */
class IgnitorOctaveShiftSpec : StringSpec({

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

    fun render(sig: Ignitor, freqHz: Double): AudioBuffer {
        val buf = AudioBuffer(blockFrames)
        sig.generate(buf, freqHz, ctx())
        return buf
    }

    /** The frequency the upstream of [sig] was handed. */
    fun seenFreq(sig: Ignitor, freqHz: Double): Double = render(sig, freqHz)[0]

    // ── The ratio, read off the upstream ──────────────────────────────────────

    "octaveUp hands the upstream exactly double the frequency" {
        // Exact equality on purpose: 12 semitones is 2.0.pow(1.0), which is exact in binary
        // floating point. An off-by-one in the semitone constant would show up here.
        seenFreq(FreqIgnitor.octaveUp(), 440.0) shouldBe 880.0
        seenFreq(FreqIgnitor.octaveUp(), 41.203) shouldBe 82.406
    }

    "octaveDown hands the upstream exactly half the frequency" {
        seenFreq(FreqIgnitor.octaveDown(), 440.0) shouldBe 220.0
        seenFreq(FreqIgnitor.octaveDown(), 82.406) shouldBe 41.203
    }

    "the two are inverses, in both orders" {
        seenFreq(FreqIgnitor.octaveUp().octaveDown(), 440.0) shouldBe 440.0
        seenFreq(FreqIgnitor.octaveDown().octaveUp(), 440.0) shouldBe 440.0
    }

    "stacking shifts by whole octaves" {
        seenFreq(FreqIgnitor.octaveUp().octaveUp(), 110.0) shouldBe 440.0
        seenFreq(FreqIgnitor.octaveDown().octaveDown(), 440.0) shouldBe 110.0
    }

    // ── The audible consequence ───────────────────────────────────────────────

    "a sine one octave up renders identically to the same sine an octave higher" {
        // Pins the DIRECTION as well as the ratio: swapping the two functions makes this red
        // even though the ratio assertions above would still pass in isolation.
        val shifted = render(IgnitorDsl.Sine().toExciter().octaveUp(), 220.0)
        val direct = render(IgnitorDsl.Sine().toExciter(), 440.0)

        for (i in 0 until blockFrames) {
            shifted[i] shouldBe direct[i]
        }
    }

    "a sine one octave down renders identically to the same sine an octave lower" {
        val shifted = render(IgnitorDsl.Sine().toExciter().octaveDown(), 880.0)
        val direct = render(IgnitorDsl.Sine().toExciter(), 440.0)

        for (i in 0 until blockFrames) {
            shifted[i] shouldBe direct[i]
        }
    }
})
