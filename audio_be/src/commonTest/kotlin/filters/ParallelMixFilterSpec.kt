/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.filters

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_bridge.FilterDef
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class ParallelMixFilterSpec : StringSpec({

    val sampleRate = 44100.0
    val blockFrames = 4096

    fun sine(freq: Double, length: Int): AudioBuffer =
        AudioBuffer(length) { i -> sin(2.0 * PI * freq * i / sampleRate) }

    fun rms(buf: AudioBuffer): Double {
        if (buf.isEmpty()) return 0.0
        return sqrt(buf.fold(0.0) { acc, v -> acc + v * v } / buf.size)
    }

    // Trivial wet filter: doubles the signal in place. Lets us check the blend math exactly.
    val doubler = object : AudioFilter {
        override fun process(buffer: AudioBuffer, offset: Int, length: Int) {
            for (i in offset until offset + length) buffer[i] = buffer[i] * 2.0
        }
    }

    // db=0 on the 430 mode → wet ≈ unity there, so the on-mode boost is clean to assert.
    fun woodModes() = listOf(
        FilterDef.Body.Mode(110.0, 2.0, 12.0),
        FilterDef.Body.Mode(230.0, 1.0, 10.0),
        FilterDef.Body.Mode(430.0, 0.0, 9.0),
        FilterDef.Body.Mode(820.0, -2.0, 7.0),
        FilterDef.Body.Mode(1500.0, -4.0, 5.0),
    )

    "ParallelMixFilter - amount=0 is a bit-identical dry bypass" {
        val buf = sine(440.0, blockFrames)
        val original = AudioBuffer(blockFrames) { buf[it] }

        ParallelMixFilter(doubler, amount = 0.0, floor = 0.6).process(buf, 0, buf.size)

        for (i in 0 until blockFrames) buf[i] shouldBe original[i]
    }

    "ParallelMixFilter - blend math: out = dryGain*dry + amount*wet (floor path)" {
        val buf = sine(440.0, blockFrames)
        val dry = AudioBuffer(blockFrames) { buf[it] }

        // amount=0.5, floor=0.6 → dryGain = max(0.6, 1 − 0.5·0.4) = max(0.6, 0.8) = 0.8.
        // wet = 2·dry → out = 0.8·dry + 0.5·(2·dry) = 1.8·dry.
        ParallelMixFilter(doubler, amount = 0.5, floor = 0.6).process(buf, 0, buf.size)

        for (i in 0 until blockFrames) {
            buf[i] shouldBe (dry[i] * 0.8 + (2.0 * dry[i]) * 0.5)
        }
    }

    "ParallelMixFilter - floor path reduces to a crossfade at floor=0" {
        val buf = sine(440.0, blockFrames)
        val dry = AudioBuffer(blockFrames) { buf[it] }

        // floor=0, amount=0.5 → dryGain = max(0, 0.5) = 0.5 → out = 0.5·dry + 0.5·(2·dry) = 1.5·dry.
        ParallelMixFilter(doubler, amount = 0.5, floor = 0.0).process(buf, 0, buf.size)

        for (i in 0 until blockFrames) {
            buf[i] shouldBe (dry[i] * 0.5 + (2.0 * dry[i]) * 0.5)
        }
    }

    "ParallelMixFilter wrapping BodyFilter - boosts on-mode AND keeps off-mode at the floor (never thins)" {
        val onBand = sine(430.0, blockFrames)     // on a wood mode (db=0)
        val offBand = sine(12000.0, blockFrames)  // far above every mode
        val inOn = rms(onBand)
        val inOff = rms(offBand)

        val floor = 0.6
        ParallelMixFilter(BodyFilter(woodModes(), sampleRate), amount = 1.0, floor = floor)
            .process(onBand, 0, onBand.size)
        ParallelMixFilter(BodyFilter(woodModes(), sampleRate), amount = 1.0, floor = floor)
            .process(offBand, 0, offBand.size)

        // On-mode: floor·dry + resonance → boosted above the input.
        rms(onBand) shouldBeGreaterThan (inOn * 1.2)
        // Off-mode: wet ≈ 0, so out ≈ floor·dry. The headline guarantee — it never thins below
        // the broadband floor, but it IS attenuated to it (not full dry, not silence).
        rms(offBand) shouldBeGreaterThan (inOff * 0.5)
        rms(offBand) shouldBeLessThan (inOff * 0.75)
    }
})
