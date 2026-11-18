/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.katalyst

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.ShapingFuncs
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_bridge.DistortionShapes
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

/**
 * The Katalyst `shape` stage (`KatalystShapeEffect`, KLANG-00467, cylinder amp step 1): the voice's waveshaper at the
 * bus position, the curve at unity drive, then the DC blocker, a core per channel, never off.
 *
 * **The oracle is written here**, never the stage's own core: per sample the shape's function at a drive of exactly
 * 1 (`ShapingFuncs`, the shape table itself), then a one-pole DC blocker `y = x - x1 + a * y1` with the house pole
 * written out, `a = 1 - 48 / fs` (KLANG-00433; at this spec's 44.1 kHz 0.998911..., not the voice's 0.995 and not a
 * fixed 0.999). A stage that drove, shared a core between the channels or took another pole goes red here.
 *
 * The curves themselves are not this spec's: the oracle applies `ShapingFuncs`, the shape table the stage's core runs,
 * so a wrong curve moves both sides alike. `StripLawCoresSpec` pins the curves against an oracle of its own.
 */
class KatalystShapeEffectSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    val soft = DistortionShapes.SOFT_INDEX
    val tube = DistortionShapes.indexOf("tube").toInt()
    val asym = DistortionShapes.indexOf("asym").toInt()

    /** The shape functions the oracle applies, by index: the three this spec measures. */
    fun shapeFn(index: Int): (Double) -> Double = when (index) {
        soft -> { x -> ShapingFuncs.fastTanh(x) }
        tube -> { x -> ShapingFuncs.tube(x) }
        asym -> { x -> ShapingFuncs.asym(x) }
        else -> error("no oracle shape for $index")
    }

    /** Hot, two partials, so the curve bends and an asymmetric shape makes DC. */
    fun inputL(k: Int): Double =
        1.4 * sin(2 * PI * 110.0 * k / sampleRate) + 0.5 * sin(2 * PI * 1370.0 * k / sampleRate)

    /** Another signal on the right, so a stage that crossed or shared the channels would show it. */
    fun inputR(k: Int): Double =
        0.9 * sin(2 * PI * 165.0 * k / sampleRate + 0.4) + 0.3 * sin(2 * PI * 2900.0 * k / sampleRate)

    class Out(val left: DoubleArray, val right: DoubleArray)

    /** Runs [fx] for [blocks] blocks over the input, in place on a mix buffer of [frames] per block. */
    fun run(
        fx: KatalystShapeEffect,
        blocks: Int,
        frames: Int = blockFrames,
        inL: (Int) -> Double = ::inputL,
        inR: (Int) -> Double = ::inputR,
    ): Out {
        val ctx = KatalystContext(blockFrames = frames, mixBuffer = StereoBuffer(frames))
        val outL = DoubleArray(blocks * frames)
        val outR = DoubleArray(blocks * frames)

        for (b in 0 until blocks) {
            val base = b * frames

            for (i in 0 until frames) {
                ctx.mixBuffer.left[i] = inL(base + i)
                ctx.mixBuffer.right[i] = inR(base + i)
            }

            fx.process(ctx)

            for (i in 0 until frames) {
                outL[base + i] = ctx.mixBuffer.left[i]
                outR[base + i] = ctx.mixBuffer.right[i]
            }
        }

        return Out(outL, outR)
    }

    /** The oracle of one channel: the shape at drive 1, then the DC blocker at [pole], from rest. */
    fun oracle(shape: Int, input: (Int) -> Double, frames: Int, pole: Double): DoubleArray {
        val f = shapeFn(shape)
        val out = DoubleArray(frames)
        var x1 = 0.0
        var y1 = 0.0

        for (k in 0 until frames) {
            val x = f(input(k))
            val y = x - x1 + pole * y1

            x1 = x
            y1 = y
            out[k] = y
        }

        return out
    }

    val housePole = 1.0 - 48.0 / sampleRate

    "the law: per sample the shape at unity drive, then the house DC blocker, for soft, tube and asym" {
        val blocks = 40

        for (shape in listOf(soft, tube, asym)) {
            withClue(DistortionShapes.names[shape]) {
                val out = run(KatalystShapeEffect(sampleRate = sampleRate, shapeIndex = shape), blocks = blocks)
                val wantL = oracle(shape = shape, input = ::inputL, frames = blocks * blockFrames, pole = housePole)

                for (k in out.left.indices) {
                    withClue("frame $k") { out.left[k] shouldBe wantL[k] }
                }
            }
        }

        // The input is hot enough that the curve bends: unity drive is not the identity here.
        val bent = run(KatalystShapeEffect(sampleRate = sampleRate, shapeIndex = soft), blocks = 4)
        val largest = bent.left.indices.maxOf { abs(bent.left[it] - inputL(it)) }

        largest shouldBeGreaterThan 0.1
    }

    "left and right each run their own core: each side matches its own oracle" {
        val blocks = 40

        for (shape in listOf(tube, asym)) {
            withClue(DistortionShapes.names[shape]) {
                val out = run(KatalystShapeEffect(sampleRate = sampleRate, shapeIndex = shape), blocks = blocks)
                val wantL = oracle(shape = shape, input = ::inputL, frames = blocks * blockFrames, pole = housePole)
                val wantR = oracle(shape = shape, input = ::inputR, frames = blocks * blockFrames, pole = housePole)

                for (k in 0 until blocks * blockFrames) {
                    withClue("frame $k") {
                        out.left[k] shouldBe wantL[k]
                        out.right[k] shouldBe wantR[k]
                    }
                }
            }
        }
    }

    "reset clears the state: render, reset, render the same input again gives the same samples" {
        val fx = KatalystShapeEffect(sampleRate = sampleRate, shapeIndex = tube)
        val first = run(fx, blocks = 20)

        fx.reset()

        val second = run(fx, blocks = 20)

        for (k in first.left.indices) {
            withClue("frame $k") {
                second.left[k] shouldBe first.left[k]
                second.right[k] shouldBe first.right[k]
            }
        }

        // retire is the same clean slate (it rents nothing).
        fx.retire()

        val third = run(fx, blocks = 20)

        third.left.toList() shouldBe first.left.toList()
    }

    "a lopsided burst through tube leaves a DC decay: a tail, until it has decayed" {
        val fx = KatalystShapeEffect(sampleRate = sampleRate, shapeIndex = tube)
        val burstBlocks = 20

        // A tail-less stage on a fresh core.
        fx.hasTail() shouldBe false

        run(fx, blocks = burstBlocks)

        // Silence in, right after the burst: the blocker still holds the offset it was removing.
        run(fx, blocks = 1, inL = { 0.0 }, inR = { 0.0 })

        fx.hasTail() shouldBe true

        // About 2 s of silence: the decay (a 7.64 Hz corner, about 21 ms per neper) is far under the floor.
        run(fx, blocks = 700, inL = { 0.0 }, inR = { 0.0 })

        fx.hasTail() shouldBe false
    }

    "the tail is either side's: a burst on the left alone, and on the right alone, each leaves a tail" {
        val silent: (Int) -> Double = { 0.0 }

        // Left only, the right silent throughout.
        val left = KatalystShapeEffect(sampleRate = sampleRate, shapeIndex = tube)

        run(left, blocks = 20, inR = silent)
        run(left, blocks = 1, inL = silent, inR = silent)

        left.hasTail() shouldBe true

        run(left, blocks = 700, inL = silent, inR = silent)

        left.hasTail() shouldBe false

        // The mirror: right only, the left silent throughout.
        val right = KatalystShapeEffect(sampleRate = sampleRate, shapeIndex = tube)

        run(right, blocks = 20, inL = silent)
        run(right, blocks = 1, inL = silent, inR = silent)

        right.hasTail() shouldBe true

        run(right, blocks = 700, inL = silent, inR = silent)

        right.hasTail() shouldBe false
    }

    "inside a region (firstStage = 2, four times the rate) the DC blocker's corner stays where it is in Hz" {
        val factor = 4
        val fastRate = sampleRate * factor
        val fastFrames = blockFrames * factor
        val blocks = 30
        val step = 0.5

        val slow = run(KatalystShapeEffect(sampleRate = sampleRate, shapeIndex = soft), blocks = blocks, inL = { step }, inR = { step })
        val fast = run(
            KatalystShapeEffect(sampleRate = fastRate, shapeIndex = soft, firstStage = 2),
            blocks = blocks,
            frames = fastFrames,
            inL = { step },
            inR = { step },
        )

        // The oracle at the mapped pole: the base rate's house pole to the 1/4th power, written out.
        val fastPole = (1.0 - 48.0 / sampleRate).pow(1.0 / factor)
        val want = oracle(shape = soft, input = { step }, frames = blocks * fastFrames, pole = fastPole)

        for (k in fast.left.indices) {
            withClue("frame $k") { fast.left[k] shouldBe (want[k] plusOrMinus 1e-12) }
        }

        // The step's decay in SECONDS is the base rate's: every fourth sample at 4x is the base rate's sample.
        for (i in 0 until blocks * blockFrames) {
            withClue("base frame $i") { fast.left[i * factor] shouldBe (slow.left[i] plusOrMinus 1e-9) }
        }
    }

    "silence in is silence out, exactly" {
        for (shape in listOf(soft, tube, asym)) {
            withClue(DistortionShapes.names[shape]) {
                val out = run(KatalystShapeEffect(sampleRate = sampleRate, shapeIndex = shape), blocks = 8, inL = { 0.0 }, inR = { 0.0 })

                out.left.all { it == 0.0 } shouldBe true
                out.right.all { it == 0.0 } shouldBe true
            }
        }
    }
})
