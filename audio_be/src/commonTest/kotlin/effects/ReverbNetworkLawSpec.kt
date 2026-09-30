/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.effects

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_be.StereoBuffer

/**
 * **The reverb network's output, against a Freeverb written here from the documented design** (test consolidation
 * gap, 2026-09-28). `ReverbStabilitySpec` pins the size scale, the stability bound and the drain arithmetic; until
 * this spec nothing pinned what the network renders.
 *
 * The documented design ([Reverb]'s KDoc and constants), per channel: eight parallel combs of 1116, 1188, 1277, 1356,
 * 1422, 1491, 1557, 1617 samples, then four series allpasses of 556, 441, 341, 225 samples, every length scaled by
 * `fs / 44100` and truncated, the right channel's every line 23 samples (scaled) longer. A comb reads its oldest
 * sample, lowpasses it into its store `store = out (1 - d) + store d + 1e-18` and writes `in + store · feedback`,
 * with `feedback = 0.28 size + 0.7` and `d = 0.4 damp` (damp 0.5 unset, `1 - lowpass / nyquist` set). An allpass
 * outputs `-in + buffered` and writes `in + 0.5 buffered + 1e-18`. The wet is `0.015` times the chain's output,
 * added onto what the output buffer holds. Each side's combs are fed `own + 0.5 (other - own)`: one room for both
 * ears (2026-09-30, `docs/tasks-archive/2026-09/20260930-stereo-reverb.md`).
 *
 * Bit for bit: the oracle keeps each comb's and allpass's arithmetic in the order the design states it, which is
 * the order the network runs it; past the feed the two channels share nothing, so the oracle runs them one after
 * the other.
 */
class ReverbNetworkLawSpec : StringSpec({

    val block = 128
    val frames = 4096

    /** The share of the other side each side's combs hear; written here, not read from the production constant. */
    val crossFeed = 0.5

    /** One side's comb feed: its own input, a step of [share] towards the other side's. */
    fun feed(own: DoubleArray, other: DoubleArray, share: Double) = DoubleArray(own.size) { own[it] + share * (other[it] - own[it]) }

    fun freeverb(input: DoubleArray, sampleRate: Int, size: Double, lowpass: Double?, right: Boolean): DoubleArray {
        val scale = sampleRate / 44100.0
        val spread = if (right) maxOf((23 * scale).toInt(), 1) else 0
        val combLengths = listOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617).map { maxOf((it * scale).toInt(), 1) + spread }
        val apLengths = listOf(556, 441, 341, 225).map { maxOf((it * scale).toInt(), 1) + spread }
        val feedback = size * 0.28 + 0.7
        val damp = if (lowpass != null) 1.0 - (lowpass / (sampleRate / 2.0)).coerceIn(0.0, 1.0) else 0.5
        val d = damp * 0.4

        val combs = combLengths.map { DoubleArray(it) }
        val combPos = IntArray(combs.size)
        val stores = DoubleArray(combs.size)
        val aps = apLengths.map { DoubleArray(it) }
        val apPos = IntArray(aps.size)
        val out = DoubleArray(input.size)

        for (i in input.indices) {
            val x = input[i]
            var sum = 0.0

            for (c in combs.indices) {
                val buf = combs[c]
                val read = buf[combPos[c]]

                stores[c] = (read * (1.0 - d)) + (stores[c] * d) + 1e-18
                buf[combPos[c]] = x + (stores[c] * feedback)
                sum += read
                combPos[c] = (combPos[c] + 1) % buf.size
            }

            for (a in aps.indices) {
                val buf = aps[a]
                val buffered = buf[apPos[a]]
                val allpassOut = -sum + buffered

                buf[apPos[a]] = sum + (buffered * 0.5) + 1e-18
                sum = allpassOut
                apPos[a] = (apPos[a] + 1) % buf.size
            }

            out[i] = sum * 0.015
        }

        return out
    }

    "the network renders the documented Freeverb, bit for bit, both channels, at fixed knobs" {
        // An impulse on the left, a smaller later one and a short burst on the right, so each channel's comb and
        // allpass recirculation shows (4096 frames: past two revolutions of the longest comb). The output buffer
        // holds a dry 0.25 before the wet is added in one pass: the wet is ADDITIVE. The other pass starts from 0, so
        // the wet's own bits are compared, down to the 1e-18 anti-denormal bias (under a 0.25 dry the comparison
        // resolves only ulp(0.25), about 5.6e-17, and cannot see it).
        val inL = DoubleArray(frames).also { it[0] = 1.0 }
        val inR = DoubleArray(frames).also {
            it[3] = 0.5

            for (i in 700 until 740) {
                it[i] = if (i % 2 == 0) 0.3 else -0.2
            }
        }

        // (sample rate, normalized size, lowpass): the defaults at the reference rate, a scaled rate with a set lowpass.
        for (dry in listOf(0.0, 0.25)) {
            for ((sampleRate, size, lowpass) in listOf(Triple(44100, 0.5, null), Triple(48000, 0.8, 3000.0))) {
                val reverb = Reverb(sampleRate).apply {
                    this.size = size
                    this.lowpass = lowpass
                }
                val inBuf = StereoBuffer(block)
                val outBuf = StereoBuffer(block)
                val outL = DoubleArray(frames)
                val outR = DoubleArray(frames)
                var at = 0

                while (at < frames) {
                    inL.copyInto(inBuf.left, 0, at, at + block)
                    inR.copyInto(inBuf.right, 0, at, at + block)
                    outBuf.left.fill(dry)
                    outBuf.right.fill(dry)

                    reverb.process(inBuf, outBuf, block)

                    outBuf.left.copyInto(outL, at)
                    outBuf.right.copyInto(outR, at)
                    at += block
                }

                val refL = freeverb(feed(inL, inR, crossFeed), sampleRate, size, lowpass, right = false).map { dry + it }
                val refR = freeverb(feed(inR, inL, crossFeed), sampleRate, size, lowpass, right = true).map { dry + it }

                withClue("dry $dry, $sampleRate Hz, size $size, lowpass $lowpass: the tail is there (a late sample is wet)") {
                    outL[frames - 1] shouldNotBe dry
                    outR[frames - 1] shouldNotBe dry
                }
                withClue("dry $dry, $sampleRate Hz, size $size, lowpass $lowpass, left: first mismatching frame") {
                    (0 until frames).firstOrNull { outL[it].toRawBits() != refL[it].toRawBits() } shouldBe null
                }
                withClue("dry $dry, $sampleRate Hz, size $size, lowpass $lowpass, right: first mismatching frame") {
                    (0 until frames).firstOrNull { outR[it].toRawBits() != refR[it].toRawBits() } shouldBe null
                }
            }
        }
    }

    "an input with equal sides renders exactly the two separate rooms it did before the cross-feed, bit for bit" {
        // The identity the cross-feed promises centred songs: equal inputs feed each side its own input, so the
        // network must render the oracle with NO cross-feed. (A voice at pan 0.5 is equal to one ulp only: the
        // engine's cos and sin of pi/4 differ there.) A burst with both signs, past two comb revolutions.
        val sampleRate = 48000
        val input = DoubleArray(frames).also {
            it[0] = 0.8

            for (i in 900 until 960) {
                it[i] = if (i % 3 == 0) 0.4 else -0.25
            }
        }
        val reverb = Reverb(sampleRate).apply { size = 0.6 }
        val inBuf = StereoBuffer(block)
        val outBuf = StereoBuffer(block)
        val outL = DoubleArray(frames)
        val outR = DoubleArray(frames)
        var at = 0

        while (at < frames) {
            input.copyInto(inBuf.left, 0, at, at + block)
            input.copyInto(inBuf.right, 0, at, at + block)
            outBuf.left.fill(0.0)
            outBuf.right.fill(0.0)

            reverb.process(inBuf, outBuf, block)

            outBuf.left.copyInto(outL, at)
            outBuf.right.copyInto(outR, at)
            at += block
        }

        val refL = freeverb(input, sampleRate, 0.6, null, right = false)
        val refR = freeverb(input, sampleRate, 0.6, null, right = true)

        withClue("the tail is there") {
            outL[frames - 1] shouldNotBe 0.0
        }
        withClue("left: first mismatching frame") {
            (0 until frames).firstOrNull { outL[it].toRawBits() != refL[it].toRawBits() } shouldBe null
        }
        withClue("right: first mismatching frame") {
            (0 until frames).firstOrNull { outR[it].toRawBits() != refR[it].toRawBits() } shouldBe null
        }
    }
})
