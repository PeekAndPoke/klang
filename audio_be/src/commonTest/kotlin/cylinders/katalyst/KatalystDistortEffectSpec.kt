/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.DistortionCore
import io.peekandpoke.klang.audio_be.Oversampler
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.distortionShapeAt
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_bridge.DistortionShapes
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.constants.KNOB_GLIDE_SECONDS
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The Katalyst `distort` stage (`docs/tasks/katalyst-distort-stage.md`): the voice's fused distort law on each
 * channel of a bus, the compressor's switching law, a gliding amount, and the oversampler's latency held in every
 * state.
 *
 * **The oracles are BARE [DistortionCore]s driven by hand and the input's own definition**, never the stage under
 * test: settled, the stage is two cores at the drive of its amount; off, it is the input (delayed by its latency);
 * switching, it is `dry + w * (distorted - dry)` with `w` the linear fade of [KNOB_GLIDE_SECONDS]. The oracle cores
 * carry the house DC pole as the LITERAL 0.999, so a stage that slid back to the voice's pole goes red here.
 */
class KatalystDistortEffectSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128
    val fadeLen = (sampleRate * KNOB_GLIDE_SECONDS).toInt()
    val glideBlocks = round(KNOB_GLIDE_SECONDS * sampleRate / blockFrames).toInt()

    val soft = DistortionShapes.SOFT_INDEX
    val tube = DistortionShapes.indexOf("tube").toInt()
    val hard = DistortionShapes.indexOf("hard").toInt()

    /** Two different channels, so a stage that crossed or shared them would show it. */
    fun inputL(k: Int): Double =
        0.6 * sin(2 * PI * 220.0 * k / sampleRate) + 0.3 * sin(2 * PI * 1370.0 * k / sampleRate)

    fun inputR(k: Int): Double =
        0.5 * sin(2 * PI * 330.0 * k / sampleRate + 0.4) + 0.2 * sin(2 * PI * 2900.0 * k / sampleRate)

    class Out(val left: DoubleArray, val right: DoubleArray)

    /**
     * Runs [fx] for [blocks] blocks over the input. Before each block, [amountAt] gives the amount the writer would
     * hand over (null: no configure call that block), as the chain's writer does on every block with an owner.
     */
    fun run(
        fx: KatalystDistortEffect,
        blocks: Int,
        inL: (Int) -> Double = ::inputL,
        inR: (Int) -> Double = ::inputR,
        amountAt: (Int) -> Double?,
    ): Out {
        val config = DistortConfig()
        val ctx = KatalystContext(blockFrames = blockFrames, mixBuffer = StereoBuffer(blockFrames))
        val outL = DoubleArray(blocks * blockFrames)
        val outR = DoubleArray(blocks * blockFrames)

        for (b in 0 until blocks) {
            val base = b * blockFrames

            for (i in 0 until blockFrames) {
                ctx.mixBuffer.left[i] = inL(base + i)
                ctx.mixBuffer.right[i] = inR(base + i)
            }

            amountAt(b)?.let {
                config.amount = it
                fx.configure(config)
            }

            fx.process(ctx)

            for (i in 0 until blockFrames) {
                outL[base + i] = ctx.mixBuffer.left[i]
                outR[base + i] = ctx.mixBuffer.right[i]
            }
        }

        return Out(outL, outR)
    }

    /**
     * Two bare cores at [amount]'s drive and the house pole (the literal), run from block [fromBlock] on; earlier
     * samples are 0. What a stage that switched on at [fromBlock] with fresh cores distorts.
     */
    fun oracle(shape: Int, amount: Double, factor: Int, blocks: Int, fromBlock: Int = 0): Out {
        val stages = Oversampler.factorToStages(factor)
        val coreL = DistortionCore(distortionShapeAt(shape.toDouble()), stages, dcBlockCoefficient = 0.999)
        val coreR = DistortionCore(distortionShapeAt(shape.toDouble()), stages, dcBlockCoefficient = 0.999)
        val scratch = ScratchBuffers(blockFrames)
        val drive = DistortionCore.drive(amount)
        val outL = DoubleArray(blocks * blockFrames)
        val outR = DoubleArray(blocks * blockFrames)

        for (b in fromBlock until blocks) {
            val base = b * blockFrames
            val l = AudioBuffer(blockFrames) { inputL(base + it) }
            val r = AudioBuffer(blockFrames) { inputR(base + it) }

            coreL.process(buffer = l, offset = 0, length = blockFrames, drive = drive, scratchBuffers = scratch)
            coreR.process(buffer = r, offset = 0, length = blockFrames, drive = drive, scratchBuffers = scratch)

            for (i in 0 until blockFrames) {
                outL[base + i] = l[i]
                outR[base + i] = r[i]
            }
        }

        return Out(outL, outR)
    }

    "a settled stage IS the voice's fused law on each channel, at the house DC pole, bit for bit" {
        listOf(soft to 0, tube to 0, soft to 2, hard to 4).forEach { (shape, factor) ->
            val blocks = 6
            val fx = KatalystDistortEffect(sampleRate, blockFrames, shapeIndex = shape, oversampleFactor = factor)
            // A first configure on a fresh stage acts at once: full weight from the first sample.
            val out = run(fx, blocks) { 0.25 }
            val expected = oracle(shape = shape, amount = 0.25, factor = factor, blocks = blocks)

            withClue("shape $shape, oversample $factor") {
                out.left.toList() shouldBe expected.left.toList()
                out.right.toList() shouldBe expected.right.toList()
            }
        }
    }

    "the DC blocker is the house's: a quiet 40 Hz loses less than 0.3 dB, where the voice's pole takes about 2.5" {
        // The audible meaning of the pole (2026-10-09): a bus carries the sub of every voice on it. A tiny amount
        // keeps `soft` in its linear region, so the level change is the DC blocker's alone.
        val blocks = 200
        val fx = KatalystDistortEffect(sampleRate, blockFrames, shapeIndex = soft)
        val sine = { k: Int -> 0.001 * sin(2 * PI * 40.0 * k / sampleRate) }
        val out = run(fx, blocks, inL = sine, inR = sine) { 1e-6 }

        // The second half, after the pole has settled: 100 blocks are about 72 periods of 40 Hz.
        val from = blocks * blockFrames / 2
        var inPower = 0.0
        var outPower = 0.0

        for (k in from until blocks * blockFrames) {
            inPower += sine(k) * sine(k)
            outPower += out.left[k] * out.left[k]
        }

        val db = 10 * log10(outPower / inPower)

        db shouldBeGreaterThan -0.3
        db shouldBeLessThan 0.3
    }

    "an amount at or below 0, or not finite, is OFF: the mix passes untouched" {
        listOf(0.0, -0.5, Double.NaN, Double.POSITIVE_INFINITY).forEach { amount ->
            val fx = KatalystDistortEffect(sampleRate, blockFrames, shapeIndex = tube)
            val out = run(fx, 3) { amount }

            withClue("amount $amount") {
                out.left.toList() shouldBe List(3 * blockFrames) { inputL(it) }
                out.right.toList() shouldBe List(3 * blockFrames) { inputR(it) }
                fx.isOn shouldBe false
            }
        }
    }

    "oversampling delays the orbit by the rounded group delay, and OFF is that delay exactly" {
        mapOf(0 to 0, 1 to 0, 2 to 4, 3 to 4, 4 to 6, 8 to 6).forEach { (factor, latency) ->
            withClue("factor $factor") {
                KatalystDistortEffect(sampleRate, blockFrames, oversampleFactor = factor).latencyFrames shouldBe latency
            }
        }

        // The rounding stays within half a frame of the measured group delay (`OversamplerGroupDelaySpec`).
        for (stages in 1..3) {
            val factor = 1 shl stages
            val latency = KatalystDistortEffect(sampleRate, blockFrames, oversampleFactor = factor).latencyFrames

            abs(latency - Oversampler.groupDelaySamples(stages)) shouldBeLessThan 0.51
        }

        // Off, with a 4-frame latency: out[k] = in[k - 4], across the block seams.
        val fx = KatalystDistortEffect(sampleRate, blockFrames, oversampleFactor = 2)
        val out = run(fx, 3) { 0.0 }

        out.left.toList() shouldBe List(3 * blockFrames) { if (it < 4) 0.0 else inputL(it - 4) }
        out.right.toList() shouldBe List(3 * blockFrames) { if (it < 4) 0.0 else inputR(it - 4) }
    }

    "a chain sums the stage's latency with a compressor's lookahead" {
        fun chain(vararg stages: KatalystStageDsl) = KatalystChainBuilder.build(
            dsl = KatalystDsl.of(*stages),
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            rings = SizedBuffers.forRings(sampleRate),
            reverbs = ReverbUnits(sampleRate),
        )

        chain(KatalystStageDsl.Distort()).latencyFrames shouldBe 0
        chain(KatalystStageDsl.Distort(oversample = 2)).latencyFrames shouldBe 4
        chain(KatalystStageDsl.Distort(oversample = 8)).latencyFrames shouldBe 6

        val lookahead = KatalystStageDsl.Compressor(lookahead = 0.005)
        val lookaheadFrames = chain(lookahead).latencyFrames

        chain(KatalystStageDsl.Distort(oversample = 2), lookahead).latencyFrames shouldBe lookaheadFrames + 4
    }

    "ON after a life fades in from the dry over the glide: the first sample is dry, the end is the distorted mix" {
        listOf(0, 2).forEach { factor ->
            val blocks = 2 + (fadeLen / blockFrames) + 3
            val k0 = 2 * blockFrames
            val latency = KatalystDistortEffect(sampleRate, blockFrames, oversampleFactor = factor).latencyFrames
            val fx = KatalystDistortEffect(sampleRate, blockFrames, shapeIndex = tube, oversampleFactor = factor)
            // Two blocks off (the stage is no longer fresh), then on.
            val out = run(fx, blocks) { b -> if (b < 2) 0.0 else 0.3 }
            val wet = oracle(shape = tube, amount = 0.3, factor = factor, blocks = blocks, fromBlock = 2)

            fun dryL(k: Int) = if (k < latency) 0.0 else inputL(k - latency)
            fun dryR(k: Int) = if (k < latency) 0.0 else inputR(k - latency)

            withClue("oversample $factor") {
                for (k in k0 until blocks * blockFrames) {
                    val j = k - k0
                    val w = if (j >= fadeLen) 1.0 else j.toDouble() / fadeLen

                    out.left[k] shouldBe (dryL(k) + w * (wet.left[k] - dryL(k)) plusOrMinus 1e-12)
                    out.right[k] shouldBe (dryR(k) + w * (wet.right[k] - dryR(k)) plusOrMinus 1e-12)
                }

                out.left[k0] shouldBe dryL(k0)
                fx.isOn shouldBe true
            }
        }
    }

    "OFF fades out to EXACTLY the dry mix, and the stage then rests in Off; with oversampling, the delayed dry" {
        // The oversampled case is the one that proves the dry ring keeps running while the stage is engaged: the
        // fade out blends against a dry that must be the input exactly `latency` frames ago.
        listOf(0, 2).forEach { factor ->
            val offBlock = 3
            val blocks = offBlock + (fadeLen / blockFrames) + 3
            val k0 = offBlock * blockFrames
            val fx = KatalystDistortEffect(sampleRate, blockFrames, shapeIndex = soft, oversampleFactor = factor)
            val latency = fx.latencyFrames
            val out = run(fx, blocks) { b -> if (b < offBlock) 0.4 else 0.0 }
            val wet = oracle(shape = soft, amount = 0.4, factor = factor, blocks = blocks)

            fun dryL(k: Int) = if (k < latency) 0.0 else inputL(k - latency)
            fun dryR(k: Int) = if (k < latency) 0.0 else inputR(k - latency)

            withClue("oversample $factor") {
                for (k in k0 until blocks * blockFrames) {
                    val j = k - k0

                    if (j < fadeLen) {
                        val w = 1.0 - j.toDouble() / fadeLen

                        out.left[k] shouldBe (dryL(k) + w * (wet.left[k] - dryL(k)) plusOrMinus 1e-12)
                        out.right[k] shouldBe (dryR(k) + w * (wet.right[k] - dryR(k)) plusOrMinus 1e-12)
                    } else {
                        out.left[k] shouldBe dryL(k)
                        out.right[k] shouldBe dryR(k)
                    }
                }

                fx.isOn shouldBe false
            }
        }
    }

    "a fade turned around mid-way continues from the weight it stands at, both ways, never jumping" {
        // Off (no longer fresh), ON at block 2 (fading in), OFF at block 5 (fading in turned around), ON at block 8
        // (fading out turned around). Each turnaround starts a full fade from where the weight stood:
        // `w = to + (from - to) * (fadeLen - j) / fadeLen` with `from` the weight the next sample would have carried.
        val onAt = 2
        val offAt = 5
        val backAt = 8
        val blocks = backAt + (fadeLen / blockFrames) + 3
        val fx = KatalystDistortEffect(sampleRate, blockFrames, shapeIndex = tube)
        val out = run(fx, blocks) { b -> if (b < onAt) 0.0 else if (b < offAt) 0.3 else if (b < backAt) 0.0 else 0.3 }
        val wet = oracle(shape = tube, amount = 0.3, factor = 0, blocks = blocks, fromBlock = onAt)

        fun weight(from: Double, to: Double, j: Int) =
            if (j >= fadeLen) to else to + (from - to) * (fadeLen - j).toDouble() / fadeLen

        val w1 = weight(0.0, 1.0, (offAt - onAt) * blockFrames)       // where the fade in stood at the OFF
        val w2 = weight(w1, 0.0, (backAt - offAt) * blockFrames)      // where the fade out stood at the ON

        for (k in onAt * blockFrames until blocks * blockFrames) {
            val w = when {
                k < offAt * blockFrames -> weight(0.0, 1.0, k - onAt * blockFrames)
                k < backAt * blockFrames -> weight(w1, 0.0, k - offAt * blockFrames)
                else -> weight(w2, 1.0, k - backAt * blockFrames)
            }

            out.left[k] shouldBe (inputL(k) + w * (wet.left[k] - inputL(k)) plusOrMinus 1e-12)
        }

        fx.isOn shouldBe true
    }

    "a moving amount glides: the drive ramps per sample across every block of the glide, no step at a seam" {
        // A quiet Nyquist square stays in `soft`'s linear region, so the local amplitude, half the sample-to-sample
        // difference (which cancels the DC blocker's slow drift), follows the drive sample by sample. A drive that
        // stepped once per block instead would leave flat runs and one jump per seam.
        val c = 1e-4
        val nyquist = { k: Int -> if (k % 2 == 0) c else -c }
        val change = 6
        val blocks = change + glideBlocks + 4
        val fx = KatalystDistortEffect(sampleRate, blockFrames, shapeIndex = soft)
        val out = run(fx, blocks, inL = nyquist, inR = nyquist) { b -> if (b < change) 0.1 else 0.5 }

        fun amp(k: Int) = abs(out.left[k] - out.left[k - 1]) / 2

        val start = change * blockFrames
        val end = (change + glideBlocks) * blockFrames
        val steps = (start + 1 until end).map { amp(it) - amp(it - 1) }
        val mean = steps.average()

        withClue("rises on every sample of the glide") { steps.min() shouldBeGreaterThan 0.0 }
        withClue("rises evenly: no seam carries a jump") { steps.max() shouldBeLessThan 3 * mean }

        // ...and lands on the new drive: the amplitude grows by drive(0.5) / drive(0.1).
        val ratio = amp(end + 2 * blockFrames) / amp(start - 2)

        ratio shouldBe (DistortionCore.drive(0.5) / DistortionCore.drive(0.1) plusOrMinus 0.01)
    }

    "with oversampling a glide meets the settled blocks without a seam, both ways (round 1)" {
        // A host that pre-multiplied the input at the base rate left the oversampler's one-sample history in the
        // wrong domain at each change, a click at both ends of every glide (round 1 measured second differences of
        // 0.015 to 0.55). The cores now ramp the drive where they apply a settled one, inside the oversampler. On a
        // quiet 100 Hz sine the second difference of a smooth output stays near 4e-5.
        val sine = { k: Int -> 0.05 * sin(2 * PI * 100.0 * k / sampleRate) }
        val up = 6
        val down = up + glideBlocks + 6
        val blocks = down + glideBlocks + 6

        listOf(2, 4).forEach { factor ->
            val fx = KatalystDistortEffect(sampleRate, blockFrames, shapeIndex = soft, oversampleFactor = factor)
            val out = run(fx, blocks, inL = sine, inR = sine) { b -> if (b < up) 0.1 else if (b < down) 0.5 else 0.1 }
            var worst = 0.0

            for (k in blockFrames until blocks * blockFrames - 1) {
                worst = maxOf(worst, abs(out.left[k + 1] - 2 * out.left[k] + out.left[k - 1]))
            }

            withClue("oversample $factor") { worst shouldBeLessThan 1e-3 }
        }
    }

    "reset is a hard cut to Off, and the next switch is instant again" {
        val fx = KatalystDistortEffect(sampleRate, blockFrames, shapeIndex = tube)

        run(fx, 3) { 0.3 }
        fx.isOn shouldBe true

        fx.reset()
        fx.isOn shouldBe false

        // A fresh stage again: on at once, from fresh cores, so it is the oracle from its first sample.
        val out = run(fx, 2) { 0.3 }
        val expected = oracle(shape = tube, amount = 0.3, factor = 0, blocks = 2)

        out.left.toList() shouldBe expected.left.toList()
    }

    "with oversampling the stage holds a tail while the ring and the decimators may still hold audio" {
        val loud = { k: Int -> inputL(k) }
        val silent = { _: Int -> 0.0 }

        val latent = KatalystDistortEffect(sampleRate, blockFrames, oversampleFactor = 2)
        run(latent, 1, inL = loud, inR = loud) { 0.3 }
        latent.hasTail() shouldBe true

        // Once everything has drained (the DC blocker's decay included, the row below), there is no tail.
        run(latent, 200, inL = silent, inR = silent) { 0.3 }
        latent.hasTail() shouldBe false

        // OFF, the cores are reset and hold nothing, but the dry ring still holds the last frames: a tail until one
        // quiet block has pushed them out.
        val off = KatalystDistortEffect(sampleRate, blockFrames, oversampleFactor = 2)
        run(off, 1, inL = loud, inR = loud) { 0.0 }
        off.hasTail() shouldBe true
        run(off, 1, inL = silent, inR = silent) { 0.0 }
        off.hasTail() shouldBe false
    }

    "an asymmetric shape's DC decay is a tail: a chain swap must drain it, not cut it (round 1)" {
        // `tube` makes DC while signal flows; after the input stops, the DC blocker's output decays from the offset
        // it was removing. `ChainSwap` retires a leaving chain at the end of its input ramp unless it reports a
        // tail, which used to cut that decay to 0 in one sample. Without oversampling, so the ring plays no part.
        val loud = { k: Int -> 0.5 * sin(2 * PI * 110.0 * k / sampleRate) }
        val silent = { _: Int -> 0.0 }
        val fx = KatalystDistortEffect(sampleRate, blockFrames, shapeIndex = tube)

        run(fx, 8, inL = loud, inR = loud) { 0.5 }

        // The first silent block still carries the decay, well above the silence floor...
        val decay = run(fx, 1, inL = silent, inR = silent) { 0.5 }

        abs(decay.left.last()) shouldBeGreaterThan 1e-3
        fx.hasTail() shouldBe true

        // ...and once it has decayed, the tail is gone.
        run(fx, 200, inL = silent, inR = silent) { 0.5 }
        fx.hasTail() shouldBe false
    }

    "only the three state objects ever appear, through every edge of the table" {
        val fx = KatalystDistortEffect(sampleRate, blockFrames)
        val seen = mutableSetOf<Any>()
        val config = DistortConfig()
        val ctx = KatalystContext(blockFrames = blockFrames, mixBuffer = StereoBuffer(blockFrames))

        fun step(amount: Double?, blocks: Int = 1) {
            repeat(blocks) {
                amount?.let { config.amount = it; fx.configure(config) }
                fx.process(ctx)
                seen.add(fx.currentState)
            }
        }

        step(0.3)                       // fresh ON: Engaged at once
        step(0.0)                       // OFF: Fading out
        step(0.3)                       // a return: Fading, turned around
        step(0.3, glideBlocks + 2)      // lands: Engaged
        step(0.0, glideBlocks + 2)      // lands: Off
        fx.reset()
        seen.add(fx.currentState)

        // The state classes do not override equals, so the set counts identities.
        seen.size shouldBe 3
    }

    "the chain's writer reads the amount from a slot: a pattern switches the stage on and moves it" {
        val chain = KatalystChainBuilder.build(
            dsl = KatalystDsl.of(KatalystStageDsl.Distort(amount = IgnitorDsl.Param("drive", 0.0))),
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            rings = SizedBuffers.forRings(sampleRate),
            reverbs = ReverbUnits(sampleRate),
        )
        val stage = chain.pipeline.single() as KatalystDistortEffect
        val ctx = KatalystContext(blockFrames = blockFrames, mixBuffer = StereoBuffer(blockFrames))

        // The authored default is 0: off.
        chain.applyParams(null)
        chain.process(ctx)
        stage.isOn shouldBe false

        // A pattern writes the slot: on.
        chain.applyParams(mapOf("drive" to 0.3))
        chain.process(ctx)
        stage.isOn shouldBe true

        // ...and what it then renders is distorted, not the input: the default `soft` at 0.3 bends the mix.
        chain.applyParams(mapOf("drive" to 0.3))
        for (i in 0 until blockFrames) {
            ctx.mixBuffer.left[i] = inputL(i)
            ctx.mixBuffer.right[i] = inputR(i)
        }
        chain.process(ctx)

        var diff = 0.0
        for (i in 0 until blockFrames) {
            val d = ctx.mixBuffer.left[i] - inputL(i)
            diff += d * d
        }

        sqrt(diff / blockFrames) shouldBeGreaterThan 1e-3
    }
})
