/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.katalyst

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.probes.ProbeSignal
import io.peekandpoke.klang.audio_be.probes.ignitorProbeStage
import io.peekandpoke.klang.audio_be.probes.katalystProbeStage
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.Kat
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.kat
import io.peekandpoke.klang.common.infra.klangMapOf
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/**
 * The chain's `drive` (KLANG-00467, cylinder amp step 1): gain without a curve by the voice's drive law, stored as a
 * `times` stage. Three things are compared on the same input, sample for sample: the chain, the voice's `drive` node
 * through the production voice build (its gate included), and an oracle written here, `x * 10^(amount * 1.2)` or `x`
 * where the voice passes its input through. A number folds to a constant gain at the door; a `Kat.param` is the law as
 * a knob expression, read while the chain runs. Both forms must give the voice's samples.
 */
class KatalystDriveSpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128
    val blocks = 8

    /** A sine at 0.4, stereo, the same on both sides (the voice's stage is mono and reads the left). */
    val signal: ProbeSignal = run {
        val frames = blocks * blockFrames
        val wave = DoubleArray(frames) { 0.4 * sin(2 * PI * 220.0 * it / sampleRate) }

        ProbeSignal(name = "sine", left = wave, right = wave.copyOf(), inputEndFrame = frames)
    }

    /** The chain's left channel over [signal]. */
    fun chain(dsl: KatalystDsl): DoubleArray = katalystProbeStage(dsl = dsl, sampleRate = sampleRate, blockFrames = blockFrames)
        .render(signal).left

    /** The voice's `drive` node over [signal], through the production build (`buildExciter`: the gate, the optimizer). */
    fun voice(amount: Double): DoubleArray = ignitorProbeStage(
        tree = IgnitorDsl.Drive(inner = IgnitorDsl.Sample, amount = IgnitorDsl.Constant(amount)),
        sampleRate = sampleRate,
        blockFrames = blockFrames,
    ).render(signal).left

    /** The chain's both channels over [input]. */
    fun stereo(dsl: KatalystDsl, input: ProbeSignal) =
        katalystProbeStage(dsl = dsl, sampleRate = sampleRate, blockFrames = blockFrames).render(input)

    /** The oracle: the input times [gain]. */
    fun scaled(gain: Double): DoubleArray = DoubleArray(signal.frames) { signal.left[it] * gain }

    /** The input of [blocks] blocks: the same sine as [signal], longer. */
    fun longSignal(blocks: Int): DoubleArray = DoubleArray(blocks * blockFrames) { 0.4 * sin(2 * PI * 220.0 * it / sampleRate) }

    /**
     * The left channel of a chain `drive(Kat.param("amp.drive", default))` over [longSignal], the slot written by the
     * cylinder's owner before every block with [blocksOf] of the block's index, as a pattern's `katp` does.
     */
    fun written(default: Double, blocksOf: (Int) -> Double, blocks: Int): DoubleArray {
        val built = KatalystChainBuilder.build(
            dsl = Kat { it.drive(Kat.param("amp.drive", default)) },
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            rings = SizedBuffers.forRings(sampleRate),
            reverbs = ReverbUnits(sampleRate),
        )
        val ctx = KatalystContext(blockFrames = blockFrames, mixBuffer = StereoBuffer(blockFrames))
        val input = longSignal(blocks)
        val out = DoubleArray(input.size)

        for (b in 0 until blocks) {
            val from = b * blockFrames

            input.copyInto(destination = ctx.mixBuffer.left, destinationOffset = 0, startIndex = from, endIndex = from + blockFrames)
            input.copyInto(destination = ctx.mixBuffer.right, destinationOffset = 0, startIndex = from, endIndex = from + blockFrames)
            built.applyParams(klangMapOf("amp.drive" to blocksOf(b)))
            built.process(ctx)
            ctx.mixBuffer.left.copyInto(destination = out, destinationOffset = from, startIndex = 0, endIndex = blockFrames)
        }

        return out
    }

    fun sameSamples(label: String, got: DoubleArray, want: DoubleArray) {
        withClue(label) {
            got.size shouldBe want.size

            for (k in got.indices) {
                withClue("frame $k") { got[k] shouldBe want[k] }
            }
        }
    }

    "drive(0.3) on a chain and on a voice give the same gain, 10^(0.3 * 1.2), sample for sample" {
        val want = scaled(10.0.pow(0.3 * 1.2))

        sameSamples(label = "voice", got = voice(0.3), want = want)
        sameSamples(label = "chain", got = chain(Kat { it.drive(0.3.kat) }), want = want)
    }

    "every amount: the chain's number door and the voice agree, unity at or below 0 and when not finite" {
        listOf(0.05, 0.3, 0.5, 1.0, 2.0, 0.0, -0.0, -0.5, Double.NaN, Double.POSITIVE_INFINITY).forEach { amount ->
            val want = if (amount > 0.0 && amount.isFinite()) scaled(10.0.pow(amount * 1.2)) else scaled(1.0)

            sameSamples(label = "voice $amount", got = voice(amount), want = want)
            sameSamples(label = "chain $amount", got = chain(Kat { it.drive(amount.kat) }), want = want)
        }
    }

    "a Kat.param amount is the same law read while the chain runs: its default, and a value the pattern writes" {
        // At its default, through the output's configuration (nothing writes the slot).
        listOf(0.05, 0.3, 1.0, 0.0, -0.5, Double.NaN).forEach { amount ->
            val want = if (amount > 0.0 && amount.isFinite()) scaled(10.0.pow(amount * 1.2)) else scaled(1.0)

            sameSamples(label = "slot default $amount", got = chain(Kat { it.drive(Kat.param("amp.drive", amount)) }), want = want)
        }

        // Written by the cylinder's owner (`katp`), from the first block: the knob snaps on a fresh stage.
        sameSamples(
            label = "written 0.3",
            got = written(default = 0.0, blocksOf = { 0.3 }, blocks = blocks),
            want = scaled(10.0.pow(0.3 * 1.2)),
        )
    }

    "a slot written while the chain runs settles where the voice's law puts it: -0.5 and NaN over 0.3 unity, 0.6 its gain" {
        // 40 blocks of the first value, then 40 of the second; the knob glides over 50 ms (19 blocks at 48 kHz), so the
        // last block is settled. A non-finite value unsets the knob (the knob rule), and an unset `times` is unity.
        val total = 80
        val tail = (total - 1) * blockFrames until total * blockFrames
        val input = longSignal(total)

        listOf(-0.5 to 1.0, Double.NaN to 1.0, 0.6 to 10.0.pow(0.6 * 1.2)).forEach { (second, gain) ->
            val out = written(default = 0.3, blocksOf = { b -> if (b < 40) 0.3 else second }, blocks = total)

            withClue("0.3 then $second") {
                for (k in tail) {
                    withClue("frame $k") { out[k] shouldBe input[k] * gain }
                }
            }
        }
    }

    "the port promise: chain drive(a).shape(s) renders bit for bit what chain distort(a, s) renders" {
        val total = 24
        val hot = run {
            val frames = total * blockFrames
            val wave = DoubleArray(frames) {
                0.7 * sin(2 * PI * 110.0 * it / sampleRate) + 0.2 * sin(2 * PI * 1370.0 * it / sampleRate)
            }

            ProbeSignal(name = "hot", left = wave, right = DoubleArray(frames) { 0.8 * wave[it] }, inputEndFrame = frames)
        }

        for (shape in listOf("tube", "soft", "asym")) {
            for (amount in listOf(0.3, 1.0)) {
                withClue("$shape at $amount") {
                    val ported = stereo(Kat { it.drive(amount.kat).shape(shape) }, hot)
                    val distorted = stereo(Kat { it.distort(amount = amount.kat, shape = shape) }, hot)

                    for (k in 0 until hot.frames) {
                        withClue("frame $k") {
                            ported.left[k] shouldBe distorted.left[k]
                            ported.right!![k] shouldBe distorted.right!![k]
                        }
                    }
                }
            }
        }
    }
})
