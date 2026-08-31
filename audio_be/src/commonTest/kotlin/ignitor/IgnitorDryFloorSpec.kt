/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.random.Random

/**
 * C4.2 guard: the ignitor-door `dryFloor` follows the shared wet/dry law on both routed
 * effects — phaser `dry = max(dryFloor, cos²(w·π/2))` (correlated, p = 2), shimmer
 * `dry = max(dryFloor, cos(w·π/2))` (decorrelated, p = 1). Same method as
 * `PhaserFloorLawSpec`: identical inputs give identical wet streams (both effects tap their
 * feedback BEFORE the output mix — the phaser from its allpass state, the shimmer from the
 * raw grain sum — so the wet path never sees dryC/wetC at ANY feedback), and output
 * differences therefore isolate the dry coefficient exactly.
 */
class IgnitorDryFloorSpec : StringSpec({

    val blockFrames = 128

    // 24k frames at 48 kHz: the shimmer's grain engine has a LONG warm-up — the first grain
    // reads 150 ms BEHIND the write head into a still-zero ring, so real wet only appears
    // after ~8.5k frames (the pitch-7 grain catching up with written data). A short fixture
    // renders dry-only shimmer and turns every shimmer row vacuous (round-2 review finding).
    val blocks = 188
    val frames = blocks * blockFrames
    val wet = 0.9

    fun ctx() = IgniteContext(
        sampleRate = 48000,
        voiceDurationFrames = frames * 2,
        gateEndFrame = frames * 2,
        releaseFrames = 0,
        scratchBuffers = ScratchBuffers(blockFrames = blockFrames),
        voiceElapsedFrames = 0,
    )

    val noise = DoubleArray(frames).also {
        val rng = Random(4711)
        for (i in it.indices) {
            it[i] = rng.nextDouble() * 2.0 - 1.0
        }
    }

    fun noiseSource(): Ignitor = object : Ignitor {
        var pos = 0
        override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
            for (i in ctx.offset until ctx.windowEnd) {
                buffer[i] = noise[pos++]
            }
        }
    }

    fun render(chain: Ignitor): DoubleArray {
        val c = ctx()
        c.updateOffsetAndLength(0, blockFrames)
        val buf = AudioBuffer(blockFrames)
        val out = DoubleArray(frames)
        repeat(blocks) { blk ->
            chain.generate(buf, 220.0, c)
            buf.copyInto(out, blk * blockFrames, 0, blockFrames)
            c.voiceElapsedFrames += blockFrames
        }
        // anti-vacuous tripwire
        var peak = 0.0
        for (v in out) {
            if (abs(v) > peak) {
                peak = abs(v)
            }
        }
        (peak > 1e-6) shouldBe true
        return out
    }

    "ignitor phaser: dryFloor scales the dry by max(floor, cos²(w·π/2)) — p = 2" {
        val cos2 = cos(wet * PI / 2.0).let { it * it }
        fun chain(floor: Double) = noiseSource().phaser(
            rate = ConstantIgnitor(1.0),
            wet = ConstantIgnitor(wet),
            center = ConstantIgnitor(1000.0),
            sweep = ConstantIgnitor(500.0),
            dryFloor = ConstantIgnitor(floor),
        )
        val a = render(chain(0.5)) // dryC = max(0.5, cos2) = 0.5
        val b = render(chain(0.0)) // dryC = cos2
        for (i in 0 until frames) {
            (a[i] - b[i]) shouldBe ((0.5 - cos2) * noise[i] plusOrMinus 1e-12)
        }
    }

    "ignitor shimmer: dryFloor scales the dry by max(floor, cos(w·π/2)) — p = 1, NOT squared" {
        val cos1 = cos(wet * PI / 2.0)
        fun chain(floor: Double) = noiseSource().shimmer(
            wet = ConstantIgnitor(wet),
            feedback = ConstantIgnitor(0.0),
            tone = ConstantIgnitor(4000.0),
            dryFloor = ConstantIgnitor(floor),
        )
        val a = render(chain(0.5)) // dryC = max(0.5, cos1) = 0.5
        val b = render(chain(0.0)) // dryC = cos1
        for (i in 0 until frames) {
            (a[i] - b[i]) shouldBe ((0.5 - cos1) * noise[i] plusOrMinus 1e-12)
        }
    }

    "ignitor shimmer: default dryFloor is 0.0 — a true crossfade (bit-identical to explicit 0)" {
        fun chain(explicit: Boolean): Ignitor {
            return if (explicit) {
                noiseSource().shimmer(
                    wet = ConstantIgnitor(wet),
                    feedback = ConstantIgnitor(0.3),
                    tone = ConstantIgnitor(4000.0),
                    dryFloor = ConstantIgnitor(0.0),
                )
            } else {
                noiseSource().shimmer(
                    wet = ConstantIgnitor(wet),
                    feedback = ConstantIgnitor(0.3),
                    tone = ConstantIgnitor(4000.0),
                )
            }
        }
        val explicit = render(chain(explicit = true))
        val defaulted = render(chain(explicit = false))
        for (i in 0 until frames) {
            defaulted[i].toRawBits() shouldBe explicit[i].toRawBits()
        }
    }

    "sanity: BOTH wet paths are audible in these fixtures (out differs from dry·dryC alone)" {
        // Guards the guards: a dead wet path (e.g. the grain loop deleted) would leave
        // out == dryC·noise and every difference row above would still pass.
        fun wetAudible(chain: Ignitor, dryC: Double) {
            val out = render(chain)
            var maxDiff = 0.0
            for (i in frames / 2 until frames) {
                val d = abs(out[i] - dryC * noise[i])
                if (d > maxDiff) {
                    maxDiff = d
                }
            }
            (maxDiff > 1e-3) shouldBe true
        }
        wetAudible(
            noiseSource().phaser(
                rate = ConstantIgnitor(1.0),
                wet = ConstantIgnitor(wet),
                center = ConstantIgnitor(1000.0),
                sweep = ConstantIgnitor(500.0),
            ),
            dryC = cos(wet * PI / 2.0).let { it * it },
        )
        wetAudible(
            noiseSource().shimmer(
                wet = ConstantIgnitor(wet),
                feedback = ConstantIgnitor(0.3),
                tone = ConstantIgnitor(4000.0),
            ),
            dryC = cos(wet * PI / 2.0),
        )
    }

    "ignitor phaser: default dryFloor is 0.0 — a true crossfade (bit-identical to explicit 0)" {
        val explicit = render(
            noiseSource().phaser(
                rate = ConstantIgnitor(1.0),
                wet = ConstantIgnitor(wet),
                center = ConstantIgnitor(1000.0),
                sweep = ConstantIgnitor(500.0),
                dryFloor = ConstantIgnitor(0.0),
            )
        )
        val defaulted = render(
            noiseSource().phaser(
                rate = ConstantIgnitor(1.0),
                wet = ConstantIgnitor(wet),
                center = ConstantIgnitor(1000.0),
                sweep = ConstantIgnitor(500.0),
            )
        )
        for (i in 0 until frames) {
            defaulted[i].toRawBits() shouldBe explicit[i].toRawBits()
        }
    }
})
