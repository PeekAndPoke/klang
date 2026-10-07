/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.IgnitorDsl.Constant
import io.peekandpoke.klang.audio_bridge.mul
import io.peekandpoke.klang.audio_bridge.plus
import kotlin.random.Random

/**
 * `IgnitorDsl.Affine` / [AffineIgnitor]: `mul · (x + pre) + add` in one pass, the node the
 * optimizer folds block-constant arithmetic into (step 1 of the arithmetic folds, 2026-09-15; no
 * rule builds it yet). Its contract is the `Plus`/`Times`/`Plus` chain it replaces, bit for bit
 * (the sign of zero included), on finite AND non-finite values, in both authored orders:
 * `safeOut(mul · (x + pre)) + add`, the Times contract scrubbing per op and the Plus contract
 * deliberately not, an absent pre-add or add being `-0.0`, the identity of the add. Every case
 * renders the node against that chain, same seeds; two pin what the equivalence cannot: that the
 * node exists at all, and that its fast path is fast.
 */
class AffineIgnitorSpec : StringSpec({

    val blockFrames = 128
    val sr = 48000
    val SENTINEL = -12345.0

    fun ctx(seed: Int): IgniteContext = IgniteContext(
        sampleRate = sr, voiceDurationFrames = sr, gateEndFrame = sr,
        scratchBuffers = ScratchBuffers(blockFrames), random = Random(seed),
    )

    /** Scratch high water of the LAST render (assertSameBits renders twice; read it after a direct render). */
    var scratchHighWater = 0

    /**
     * Renders [blocks] blocks; the first starts at [offset] (a mid-block onset), the last ends at
     * [lastLength] (a voice's short trailing window). Samples outside a window must stay untouched.
     * [freqHzPerBlock] overrides the frequency per block, so a coefficient expression read per
     * block shows as such. Scratch use is recorded in [scratchHighWater].
     */
    fun render(
        dsl: IgnitorDsl, freqHz: Double, blocks: Int = 4, offset: Int = 0, lastLength: Int = blockFrames,
        freqHzPerBlock: ((Int) -> Double)? = null,
    ): DoubleArray {
        val c = ctx(3)
        val sig = dsl.toExciter(null, random = Random(3))
        val buf = AudioBuffer(blockFrames)
        val out = DoubleArray(blocks * blockFrames)

        repeat(blocks) { b ->
            val off = if (b == 0) offset else 0
            val end = if (b == blocks - 1) lastLength else blockFrames

            buf.fill(SENTINEL)
            c.updateOffsetAndLength(offset = off, length = end - off)
            sig.generate(buf, freqHzPerBlock?.invoke(b) ?: freqHz, c)

            for (i in 0 until blockFrames) {
                if (i < off || i >= end) {
                    withClue("block $b wrote outside its window at $i") { buf[i] shouldBe SENTINEL }
                } else {
                    out[b * blockFrames + i] = buf[i]
                }
            }

            c.voiceElapsedFrames += blockFrames
        }

        scratchHighWater = c.scratchBuffers.highWater

        return out
    }

    fun assertSameBits(
        affine: IgnitorDsl, chain: IgnitorDsl, clue: String, freqHz: Double = 220.0, offset: Int = 0,
        lastLength: Int = blockFrames, freqHzPerBlock: ((Int) -> Double)? = null,
    ) {
        val a = render(affine, freqHz, offset = offset, lastLength = lastLength, freqHzPerBlock = freqHzPerBlock)
        val b = render(chain, freqHz, offset = offset, lastLength = lastLength, freqHzPerBlock = freqHzPerBlock)

        for (i in a.indices) {
            // NaN for NaN (payload bits are outside the contract), else the same bits
            if (!(a[i].isNaN() && b[i].isNaN())) {
                withClue("$clue: sample $i: ${a[i]} vs ${b[i]}") { a[i].toRawBits() shouldBe b[i].toRawBits() }
            }
        }
    }

    val saw = IgnitorDsl.Saw()
    val none = Constant(-0.0)

    "the DSL node lowers to the one-pass runtime, and its fast path touches no scratch" {
        val sig = IgnitorDsl.Affine(inner = saw, pre = none, mul = Constant(0.5), add = Constant(0.25)).toExciter(null, random = Random(1))

        withClue("lowered to AffineIgnitor, not to the chain") {
            ((sig as? MemoizingIgnitor)?.inner ?: sig).let { it is AffineIgnitor } shouldBe true
        }

        render(IgnitorDsl.Affine(inner = saw, pre = none, mul = Constant(0.5), add = Constant(0.25)), 220.0)
        withClue("block-constant coefficients render without scratch") { scratchHighWater shouldBe 0 }

        render(IgnitorDsl.Affine(inner = saw, pre = none, mul = IgnitorDsl.Sine(freq = Constant(3.0)), add = Constant(0.25)), 220.0)
        withClue("a modulated coefficient takes the scratch path, three buffers") { scratchHighWater shouldBe 3 }
    }

    "block-constant coefficients: the same bits as the chain, in both authored orders, on a mid-block onset and a short last window" {
        for ((m, a) in listOf(0.5 to 0.25, 4.0 to 10.0, -1.2 to 0.0, 1.0 to -3.0)) {
            // x.mul(m).add(a)
            val forward = IgnitorDsl.Affine(inner = saw, pre = none, mul = Constant(m), add = Constant(a))
            val forwardChain = saw.mul(Constant(m)).plus(Constant(a))
            // x.add(a).mul(m): the pre-add, exact at the zero crossings where x ≈ -a
            val reverse = IgnitorDsl.Affine(inner = saw, pre = Constant(a), mul = Constant(m), add = none)
            val reverseChain = saw.plus(Constant(a)).mul(Constant(m))

            assertSameBits(affine = forward, chain = forwardChain, clue = "mul $m add $a")
            assertSameBits(affine = forward, chain = forwardChain, clue = "mul $m add $a, onset at 37, last window 64", offset = 37, lastLength = 64)
            assertSameBits(affine = reverse, chain = reverseChain, clue = "add $a mul $m")
            assertSameBits(affine = reverse, chain = reverseChain, clue = "add $a mul $m, onset at 37, last window 64", offset = 37, lastLength = 64)
        }
    }

    "non-finite coefficients and a non-finite signal sanitise exactly like the chain" {
        val nan = Double.NaN
        val inf = Double.POSITIVE_INFINITY

        for ((m, a) in listOf(0.0 to 1.0, inf to 1.0, nan to 1.0, 2.0 to inf, 2.0 to nan, 1e300 to 1e300)) {
            assertSameBits(affine = IgnitorDsl.Affine(inner = saw, pre = none, mul = Constant(m), add = Constant(a)), chain = saw.mul(Constant(m)).plus(Constant(a)), clue = "mul $m add $a")
            assertSameBits(affine = IgnitorDsl.Affine(inner = saw, pre = Constant(a), mul = Constant(m), add = none), chain = saw.plus(Constant(a)).mul(Constant(m)), clue = "add $a mul $m")
        }

        // an infinite and a NaN SIGNAL: scrubbed by the multiply's safeOut, the add applied after;
        // a constant signal takes the fill path, so that runs on a mid-block onset too
        for (x in listOf(inf, nan, -inf, 0.75)) {
            val src = Constant(x)

            assertSameBits(affine = IgnitorDsl.Affine(inner = src, pre = none, mul = Constant(2.0), add = Constant(1.0)), chain = src.mul(Constant(2.0)).plus(Constant(1.0)), clue = "signal $x")
            assertSameBits(affine = IgnitorDsl.Affine(inner = src, pre = none, mul = Constant(2.0), add = Constant(1.0)), chain = src.mul(Constant(2.0)).plus(Constant(1.0)), clue = "signal $x, onset at 37", offset = 37)
        }
    }

    "the sign of zero survives: an absent add is the -0.0 identity, an authored add(0.0) keeps its effect" {
        // A negative gain at an exact zero gives -0.0 in the chain; a trailing + 0.0 would turn it
        // into +0.0, a trailing + (-0.0) does not. And x.add(0.0).mul(a) on a -0.0 sample IS +0.0
        // times a in the chain, so the authored zero pre-add must stay a real add.
        for (x in listOf(0.0, -0.0)) {
            val src = Constant(x)

            assertSameBits(affine = IgnitorDsl.Affine(inner = src, pre = Constant(0.0), mul = Constant(-1.2), add = none), chain = src.plus(Constant(0.0)).mul(Constant(-1.2)), clue = "signal $x, add(0.0).mul(-1.2)")
            assertSameBits(affine = IgnitorDsl.Affine(inner = src, pre = none, mul = Constant(-1.2), add = none), chain = src.mul(Constant(-1.2)), clue = "signal $x, mul(-1.2) alone")
            assertSameBits(affine = IgnitorDsl.Affine(inner = src, pre = none, mul = Constant(-1.2), add = Constant(0.0)), chain = src.mul(Constant(-1.2)).plus(Constant(0.0)), clue = "signal $x, mul(-1.2).add(0.0)")
        }

        // and on the per-sample loop, not only the fill: a saw times zero is not block-constant
        // and yields +0.0 or -0.0 per sample with the saw's sign
        val zeros = saw.mul(Constant(0.0))

        assertSameBits(affine = IgnitorDsl.Affine(inner = zeros, pre = none, mul = Constant(-1.2), add = none), chain = zeros.mul(Constant(-1.2)), clue = "signed zeros, mul(-1.2) alone")
        assertSameBits(affine = IgnitorDsl.Affine(inner = zeros, pre = Constant(0.0), mul = Constant(-1.2), add = none), chain = zeros.plus(Constant(0.0)).mul(Constant(-1.2)), clue = "signed zeros, add(0.0).mul(-1.2)")
    }

    "a block-constant coefficient EXPRESSION is read per block, at that block's voice frequency" {
        // mul = 2 · freq / 220: unity at 220 Hz, double at 440 Hz, read once per block. The
        // frequency changes between blocks (a pitch envelope), so a coefficient cached at first
        // use would render the wrong blocks.
        val mul = IgnitorDsl.Freq.mul(Constant(2.0 / 220.0))
        val affine = IgnitorDsl.Affine(inner = saw, pre = none, mul = mul, add = Constant(0.5))
        val chain = saw.mul(mul).plus(Constant(0.5))
        val perBlock = { b: Int -> if (b % 2 == 0) 220.0 else 440.0 }

        assertSameBits(affine = affine, chain = chain, clue = "220 and 440 alternating per block", freqHzPerBlock = perBlock)

        // and it really scales: the loud blocks are the 440 Hz ones
        val out = render(affine, 220.0, freqHzPerBlock = perBlock)
        val quiet = out.copyOfRange(0, blockFrames).maxOrNull()!!
        val loud = out.copyOfRange(blockFrames, 2 * blockFrames).maxOrNull()!!

        (quiet < loud) shouldBe true
    }

    "a MODULATED coefficient still renders the chain, per sample, on a mid-block onset too" {
        val lfo = IgnitorDsl.Sine(freq = Constant(3.0))
        val affine = IgnitorDsl.Affine(saw, pre = none, mul = lfo, add = Constant(0.2))
        val chain = saw.mul(lfo).plus(Constant(0.2))

        assertSameBits(affine = affine, chain = chain, clue = "modulated mul")
        assertSameBits(affine = affine, chain = chain, clue = "modulated mul, onset at 37, last window 64", offset = 37, lastLength = 64)

        val affine2 = IgnitorDsl.Affine(saw, pre = none, mul = Constant(0.5), add = lfo)
        val chain2 = saw.mul(Constant(0.5)).plus(lfo)

        assertSameBits(affine = affine2, chain = chain2, clue = "modulated add")

        // a pitched coefficient follows the voice's pitch mod like the chain's does (withMod),
        // in every slot: under a vibrato the coefficient sine must wobble with the carrier
        val pitched = IgnitorDsl.Sine()

        assertSameBits(
            affine = IgnitorDsl.Vibrato(IgnitorDsl.Affine(saw, pre = pitched, mul = Constant(0.5), add = none), semitones = Constant(2.0)),
            chain = IgnitorDsl.Vibrato(saw.plus(pitched).mul(Constant(0.5)), semitones = Constant(2.0)),
            clue = "pitched pre-add under a vibrato",
        )
        assertSameBits(
            affine = IgnitorDsl.Vibrato(IgnitorDsl.Affine(saw, pre = none, mul = pitched, add = none), semitones = Constant(2.0)),
            chain = IgnitorDsl.Vibrato(saw.mul(pitched), semitones = Constant(2.0)),
            clue = "pitched mul under a vibrato",
        )
        assertSameBits(
            affine = IgnitorDsl.Vibrato(IgnitorDsl.Affine(saw, pre = none, mul = Constant(0.5), add = pitched), semitones = Constant(2.0)),
            chain = IgnitorDsl.Vibrato(saw.mul(Constant(0.5)).plus(pitched), semitones = Constant(2.0)),
            clue = "pitched add under a vibrato",
        )
    }

    "control rate: an all-constant affine is block-constant with the chain's value, a signal one is not" {
        val constant = IgnitorDsl.Affine(inner = Constant(3.0), pre = Constant(0.5), mul = Constant(2.0), add = Constant(1.0)).toExciter(null, random = Random(1))
        val chain = Constant(3.0).plus(Constant(0.5)).mul(Constant(2.0)).plus(Constant(1.0)).toExciter(null, random = Random(1))

        constant.isBlockConstant shouldBe true
        constant.controlRateValueOrNull(220.0) shouldBe chain.controlRateValueOrNull(220.0)
        constant.controlRateValueOrNull(220.0) shouldBe 8.0

        val signal = IgnitorDsl.Affine(inner = saw, pre = none, mul = Constant(2.0), add = Constant(1.0)).toExciter(null, random = Random(1))

        signal.isBlockConstant shouldBe false
        signal.controlRateValueOrNull(220.0) shouldBe null
    }

    "the node's params are the coefficients' and the signal's, in order" {
        val dsl = IgnitorDsl.Affine(
            IgnitorDsl.Sine(analog = IgnitorDsl.Param("analog", 0.0)),
            pre = IgnitorDsl.Param("bias", 0.0),
            mul = IgnitorDsl.Param("level", 0.5),
            add = IgnitorDsl.Param("offset", 0.0),
        )
        val params = mutableListOf<IgnitorDsl.Param>().also { dsl.collectParams(it) }.map { it.name }

        params shouldBe listOf("analog", "bias", "level", "offset")
    }
})
