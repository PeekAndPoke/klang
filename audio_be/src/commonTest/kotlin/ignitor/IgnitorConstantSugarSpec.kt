/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import kotlin.math.abs
import kotlin.random.Random

/**
 * The runtime arithmetic doors take a plain number wherever they take a constant (engine tidy-up step 13, D10): each
 * `Double` overload is plain sugar for the explicit `ConstantIgnitor`, so the two spellings render bit for bit the same.
 * `mul`, `div` and `detune` are not here: their `Double` forms build dedicated constant nodes of their own.
 *
 * The source swings from -1.5 to 1.5 so that every door changes it (a floor, a cap, a clamp, a select, a modulo), and a
 * delegation to the wrong door or the wrong value renders differently.
 */
class IgnitorConstantSugarSpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128
    val blocks = 4

    /** A sawtooth from -1.5 to 1.5 over 96 frames, fresh per call, so each render starts from the same sample. */
    fun saw(): Ignitor = object : Ignitor {
        var pos = 0

        override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
            for (i in ctx.offset until ctx.windowEnd) {
                buffer[i] = -1.5 + 3.0 * ((pos++ % 96) / 95.0)
            }
        }
    }

    /** A second, slower signal for the doors that take one (lerp toward a signal). */
    fun slow(): Ignitor = object : Ignitor {
        var pos = 0

        override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
            for (i in ctx.offset until ctx.windowEnd) {
                buffer[i] = 0.8 - (pos++ % 300) / 200.0
            }
        }
    }

    fun render(build: () -> Ignitor): List<Double> {
        val chain = build()
        val ctx = IgniteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = blocks * blockFrames,
            gateEndFrame = blocks * blockFrames,
            scratchBuffers = ScratchBuffers(blockFrames),
            random = Random(7),
        ).apply {
            updateOffsetAndLength(offset = 0, length = blockFrames)
            voiceElapsedFrames = 0
        }
        val buf = AudioBuffer(blockFrames)
        val out = ArrayList<Double>(blocks * blockFrames)

        repeat(blocks) {
            chain.generate(buf, 220.0, ctx)
            buf.forEach { out.add(it) }
            ctx.voiceElapsedFrames += blockFrames
        }

        // Vacuous-pass tripwire: two silent renders would match whatever the door did.
        out.maxOf { abs(it) } shouldBeGreaterThan 1e-6

        return out
    }

    fun c(v: Double) = ConstantIgnitor(v)

    "plus(Double) renders as plus(ConstantIgnitor)" {
        render { saw() + 0.25 } shouldBe render { saw() + c(0.25) }
    }

    "times(Double) renders as times(ConstantIgnitor)" {
        render { saw() * 0.25 } shouldBe render { saw() * c(0.25) }
    }

    "minus(Double) renders as minus(ConstantIgnitor)" {
        render { saw().minus(0.25) } shouldBe render { saw().minus(c(0.25)) }
    }

    "pow(Double) renders as pow(ConstantIgnitor)" {
        render { saw().pow(3.0) } shouldBe render { saw().pow(c(3.0)) }
    }

    "min(Double) renders as min(ConstantIgnitor), the mathematical minimum" {
        render { saw().min(0.25) } shouldBe render { saw().min(c(0.25)) }
    }

    "max(Double) renders as max(ConstantIgnitor), the mathematical maximum" {
        render { saw().max(0.25) } shouldBe render { saw().max(c(0.25)) }
    }

    "clamp(Double, Double) renders as clamp with two ConstantIgnitors" {
        render { saw().clamp(lo = -0.5, hi = 0.75) } shouldBe render { saw().clamp(lo = c(-0.5), hi = c(0.75)) }
    }

    "lerp(Ignitor, Double) and lerp(Double, Double) render as lerp with ConstantIgnitors" {
        render { saw().lerp(other = slow(), t = 0.3) } shouldBe render { saw().lerp(other = slow(), t = c(0.3)) }
        render { saw().lerp(other = 0.5, t = 0.3) } shouldBe render { saw().lerp(other = c(0.5), t = c(0.3)) }
    }

    "range(Double, Double) renders as range with two ConstantIgnitors" {
        render { saw().range(from = 200.0, to = 800.0) } shouldBe render { saw().range(from = c(200.0), to = c(800.0)) }
    }

    "mod(Double) renders as mod(ConstantIgnitor)" {
        render { saw().mod(0.4) } shouldBe render { saw().mod(c(0.4)) }
    }

    "select(Double, Double) renders as select with two ConstantIgnitors" {
        render { saw().select(whenTrue = 1.0, whenFalse = -1.0) } shouldBe
            render { saw().select(whenTrue = c(1.0), whenFalse = c(-1.0)) }
    }

    "withGain(Double) renders as withGain(ConstantIgnitor)" {
        render { saw().withGain(0.5) } shouldBe render { saw().withGain(c(0.5)) }
    }
})
