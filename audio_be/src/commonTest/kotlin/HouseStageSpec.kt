/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.filters.houseDcBlockCoeff
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Guards the final master/output stage extracted from KlangAudioRenderer (D2·1): [HouseStage.process] runs on the
 * summed mix, so the wiring (DC → limiter → clip) is covered here in isolation. The clip is the real one:
 * [HouseStage.process] calls [clipOutput] and [clipSample], whose boundary table (both clamp branches, the
 * boundaries, the non-finite rows, no quantisation) lives in `OutputClipSpec`; the rows below show the clamp
 * firing through [HouseStage.process], and that no non-finite sample reaches it (phase 3 step 12, risk R0).
 */
class HouseStageSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 64

    "silent mix produces all-zero output" {
        val master = HouseStage(sampleRate = sampleRate, blockFrames = blockFrames)
        val mix = StereoBuffer(blockFrames)            // cleared on construction
        val out = StereoBuffer(blockFrames).apply { fill(0.999) }  // non-zero, must be overwritten

        master.process(mix = mix, out = out)

        out.isExactlySilent() shouldBe true
    }

    "output routes channels independently" {
        // The master limiter has lookahead, so a left-only impulse emerges
        // HOUSE_LIMITER_LOOKAHEAD_SECONDS later: past the end of a single 64-frame block. Render enough
        // blocks to carry it through, then look for it wherever it lands.
        val master = HouseStage(sampleRate = sampleRate, blockFrames = blockFrames)
        val out = StereoBuffer(blockFrames)
        val leftSeen = mutableListOf<Int>()
        val rightSeen = mutableListOf<Int>()

        repeat(8) { block ->
            val mix = StereoBuffer(blockFrames)
            // left-only impulse in the first block, well below the -1 dB limiter threshold
            if (block == 0) mix.left[0] = 0.5

            master.process(mix = mix, out = out)

            for (i in 0 until blockFrames) {
                if (out.left[i] != 0.0) leftSeen += block * blockFrames + i
                if (out.right[i] != 0.0) rightSeen += block * blockFrames + i
            }
        }

        leftSeen.shouldNotBeEmpty()          // the left impulse does come out...
        rightSeen.shouldBeEmpty()            // ...and never leaks into the right channel
    }

    "DC offset does not cost the limiter headroom: the blockers run BEFORE it" {
        // The reorder is a required part of the lookahead fix, not a tidy-up: a DC blocker is a
        // ~7 Hz high-pass that OVERSHOOTS on onsets, so downstream of the limiter its overshoot
        // lands straight on the clip and eats the margin the limiter just earned.
        //
        // Guard: the same signal with and without a DC offset must reach the output at the same
        // level. With the old order the offset survives into the limiter's detector, costing
        // headroom asymmetrically.
        // Measured on the SETTLED portion only: a DC blocker is a high-pass with a ~21 ms time
        // constant, so the first blocks legitimately still carry the offset. Including them would
        // measure the settling transient rather than the ordering.
        fun renderPeak(offset: Double): Double {
            val master = HouseStage(sampleRate = sampleRate, blockFrames = blockFrames)
            val out = StereoBuffer(blockFrames)
            var peak = 0.0
            val settleBlocks = 200

            repeat(400) {
                val mix = StereoBuffer(blockFrames)
                for (i in 0 until blockFrames) {
                    val t = (it * blockFrames + i).toDouble() / sampleRate
                    val v = 0.8 * kotlin.math.sin(2.0 * kotlin.math.PI * 220.0 * t) + offset
                    mix.left[i] = v
                    mix.right[i] = v
                }
                master.process(mix = mix, out = out)
                if (it >= settleBlocks) {
                    for (i in 0 until blockFrames) {
                        val a = kotlin.math.abs(out.left[i])
                        if (a > peak) peak = a
                    }
                }
            }
            return peak
        }

        val clean = renderPeak(0.0)
        val offset = renderPeak(0.35)

        // Within ~1%: the DC is gone before the limiter ever sees it.
        (kotlin.math.abs(clean - offset) < clean / 100) shouldBe true
    }

    "the stage reports the latency it actually adds" {
        // The diagnostics report latencyFrames in every report's output latency (the dispatcher, KLANG-00431 D3), and
        // the offline renderer trims that many frames at its head and runs that many past its end (KlangOfflineRenderer,
        // D11). If it drifts from the real delay, visuals misalign or renders lose their start or end silently.
        val master = HouseStage(sampleRate = sampleRate, blockFrames = blockFrames)
        val expected = (HouseStage.HOUSE_LIMITER_LOOKAHEAD_SECONDS * sampleRate).toInt()

        master.latencyFrames shouldBe expected

        // ...and it must match what the stage really does. Feed one impulse, find it in the output.
        val out = StereoBuffer(blockFrames)
        var foundAt = -1
        var frame = 0

        repeat(16) { block ->
            val mix = StereoBuffer(blockFrames)
            if (block == 0) mix.left[0] = 0.5
            master.process(mix = mix, out = out)
            for (i in 0 until blockFrames) {
                if (foundAt < 0 && out.left[i] != 0.0) foundAt = frame + i
            }
            frame += blockFrames
        }

        foundAt shouldBe master.latencyFrames
    }

    "a mix too loud for the limiter reaches both rails through the real clip" {
        // The limiter is 20:1, not infinite: a sine at +60 dBFS leaves it near +2 dBFS, so the clip's
        // two clamp branches fire through process(), on the settled part.
        val master = HouseStage(sampleRate = sampleRate, blockFrames = blockFrames)
        val out = StereoBuffer(blockFrames)
        var sawMax = false
        var sawMin = false

        repeat(200) { block ->
            val mix = StereoBuffer(blockFrames)

            for (i in 0 until blockFrames) {
                val t = (block * blockFrames + i).toDouble() / sampleRate
                mix.left[i] = 1000.0 * kotlin.math.sin(2.0 * kotlin.math.PI * 220.0 * t)
            }

            master.process(mix = mix, out = out)

            if (block >= 100) {
                for (i in 0 until blockFrames) {
                    sawMax = sawMax || out.left[i] == 1.0
                    sawMin = sawMin || out.left[i] == -1.0
                }
            }
        }

        sawMax shouldBe true
        sawMin shouldBe true
    }

    "a non-finite sample in the mix never reaches the clip, so it never clicks a rail" {
        // The clip maps NaN to -1.0 (a full-scale negative click) and +/-Inf to the rails.
        // None of that is heard: the DC blockers pass a non-finite sample on (and one more after
        // it), but the house limiter's delay ring stores it as 0.0, so the clip sees only finite
        // values. A quiet sine cannot reach a rail, so any rail sample here is a leaked non-finite.
        val master = HouseStage(sampleRate = sampleRate, blockFrames = blockFrames)
        val out = StereoBuffer(blockFrames)
        val poison = mapOf(3 to Double.NaN, 5 to Double.POSITIVE_INFINITY, 7 to Double.NEGATIVE_INFINITY)
        var railHits = 0
        var heard = false

        repeat(20) { block ->
            val mix = StereoBuffer(blockFrames)

            for (i in 0 until blockFrames) {
                val t = (block * blockFrames + i).toDouble() / sampleRate
                val v = 0.5 * kotlin.math.sin(2.0 * kotlin.math.PI * 220.0 * t)
                mix.left[i] = v
                mix.right[i] = v
            }

            poison[block]?.let {
                mix.left[10] = it
                mix.right[20] = it
            }

            master.process(mix = mix, out = out)

            for (s in out.interleavedCopy()) {
                if (s == -1.0 || s == 1.0) {
                    railHits++
                }

                heard = heard || s != 0.0
            }
        }

        railHits shouldBe 0
        heard shouldBe true
    }

    "poison seen: the stage counts the non-finite samples its limiter stores as 0, and nothing else (KLANG-00425 L8-04 (c))" {
        // One bad sample on one channel reads as 2: the DC blocker ahead of the limiter passes it out twice (as itself,
        // then as `x - xPrev` with the bad sample as `xPrev`) before its state is flushed. A huge FINITE block is not
        // counted. The count stays across reset() (it is not audio state) and only ever grows.
        fun run(poison: (block: Int, mix: StereoBuffer) -> Unit): HouseStage {
            val master = HouseStage(sampleRate = sampleRate, blockFrames = blockFrames)
            val out = StereoBuffer(blockFrames)

            repeat(40) { block ->
                val mix = StereoBuffer(blockFrames)

                for (i in 0 until blockFrames) {
                    val v = 0.3 * kotlin.math.sin(2.0 * kotlin.math.PI * 220.0 * (block * blockFrames + i) / sampleRate)

                    mix.left[i] = v
                    mix.right[i] = v
                }

                poison(block, mix)
                master.process(mix = mix, out = out)
            }

            return master
        }

        run { _, _ -> }.nonFiniteSamples shouldBe 0.0
        run { block, mix -> if (block == 5) mix.left.fill(1e15) }.nonFiniteSamples shouldBe 0.0
        run { block, mix -> if (block == 5) mix.left[17] = Double.NaN }.nonFiniteSamples shouldBe 2.0
        run { block, mix -> if (block == 5) mix.right[63] = Double.POSITIVE_INFINITY }.nonFiniteSamples shouldBe 2.0

        val both = run { block, mix ->
            if (block == 5 || block == 30) {
                mix.left[3] = Double.NaN
                mix.right[3] = Double.NEGATIVE_INFINITY
            }
        }

        both.nonFiniteSamples shouldBe 8.0

        both.reset()
        both.nonFiniteSamples shouldBe 8.0
    }

    "the house DC pole has one corner at every sample rate: 0.999 at 48 kHz, the same 10 Hz gain at 44.1 and 96 kHz (audit L8-07)" {
        // A fixed 0.999 put the corner at 7.0 Hz at 44.1 kHz and 15.3 Hz at 96 kHz: a 10 Hz tone came out at 0.82 and
        // 0.55 of its level. `1 - 48 / fs` keeps 7.64 Hz everywhere (about 0.79 at 10 Hz) and 48 kHz bit for bit.
        houseDcBlockCoeff(sampleRate = 48_000).toRawBits() shouldBe 0.999.toRawBits()

        fun gainAt10Hz(rate: Int): Double {
            val master = HouseStage(sampleRate = rate, blockFrames = blockFrames)
            val out = StereoBuffer(blockFrames)
            val blocks = 2 * rate / blockFrames
            var peak = 0.0

            for (block in 0 until blocks) {
                val mix = StereoBuffer(blockFrames)

                for (i in 0 until blockFrames) {
                    mix.left[i] = 0.1 * kotlin.math.sin(2.0 * kotlin.math.PI * 10.0 * (block * blockFrames + i) / rate)
                }

                master.process(mix = mix, out = out)

                if (block >= blocks / 2) {
                    for (i in 0 until blockFrames) {
                        peak = maxOf(peak, kotlin.math.abs(out.left[i]))
                    }
                }
            }

            return peak / 0.1
        }

        val at48 = gainAt10Hz(48_000)

        at48 shouldBe (0.795 plusOrMinus 0.01)
        gainAt10Hz(44_100) shouldBe (at48 plusOrMinus 0.002)
        gainAt10Hz(96_000) shouldBe (at48 plusOrMinus 0.002)
    }
    "a huge FINITE block on the mix does not linger as DC: the DC blockers drop their state above 1e3 (audit L8-01)" {
        // KLANG-00432, maintainer 2026-11-11. One block (128 frames) of a 0.3 sine at 220 Hz with 1e15 added: the
        // blockers carried it in their state and put up to seconds of full-scale DC out (the audit at 48 kHz: 26,783
        // samples on the rail, back within 0.003 of the clean run 1.13 s later). With the state dropped above
        // `HOUSE_DC_STATE_BOUND` the bad block costs about itself plus the limiter's recovery. Compared with the same
        // run without the bad block, from the bad block on.
        fun run(bad: Boolean): DoubleArray {
            val house = HouseStage(sampleRate = sampleRate, blockFrames = blockFrames)
            val mix = StereoBuffer(blockFrames)
            val out = StereoBuffer(blockFrames)
            val frames = 2 * sampleRate / blockFrames * blockFrames
            val result = DoubleArray(frames)
            var at = 0

            while (at < frames) {
                for (i in 0 until blockFrames) {
                    val n = at + i
                    val x = 0.3 * sin(2.0 * PI * 220.0 * n / sampleRate) + if (bad && n in 4800 until 4928) 1e15 else 0.0

                    mix.left[i] = x
                    mix.right[i] = x
                }

                house.process(mix = mix, out = out)
                out.left.copyInto(destination = result, destinationOffset = at, startIndex = 0, endIndex = blockFrames)
                at += blockFrames
            }

            return result
        }

        val control = run(bad = false)
        val poisoned = run(bad = true)
        var rail = 0
        var lastDeviation = -1

        for (i in 4800 until poisoned.size) {
            if (abs(poisoned[i]) >= 1.0) {
                rail++
            }

            // NaN-guard: a non-finite output counts as a deviation
            if (!(abs(poisoned[i] - control[i]) < 0.003)) {
                lastDeviation = i
            }
        }

        val recoveredSeconds = (lastDeviation + 1 - 4800).toDouble() / sampleRate

        // Measured with the bound: 2 samples on the rail, back 0.213 s after the bad block began
        withClue("samples on the rail ($rail): a few, not seconds of DC") {
            (rail < 50) shouldBe true
        }

        withClue("back within 0.003 of the clean run $recoveredSeconds s after the bad block began") {
            (recoveredSeconds < 0.4) shouldBe true
        }
    }
    "crushed: the stage counts the blocks its limiter ends more than 200 dB down or the mix ends huge, and nothing a loud mix reaches (KLANG-00432 L8-02 (c))" {
        // A huge FINITE value on the mix (1e15) has already rounded the music away in the sum; "poison seen" does not
        // count it (it is finite). The limiter's gain is read once per block: a block ending under -200 dB counts. A mix
        // a thousand times too loud (+60 dBFS) is limited by about 58 dB, nowhere near.
        fun run(extra: (block: Int) -> Double): HouseStage {
            val house = HouseStage(sampleRate = sampleRate, blockFrames = blockFrames)
            val mix = StereoBuffer(blockFrames)
            val out = StereoBuffer(blockFrames)

            repeat(400) { block ->
                for (i in 0 until blockFrames) {
                    val x = 0.3 * sin(2.0 * PI * 220.0 * (block * blockFrames + i) / sampleRate) + extra(block)

                    mix.left[i] = x
                    mix.right[i] = x
                }

                house.process(mix = mix, out = out)
            }

            return house
        }

        run { 0.0 }.crushedBlocks shouldBe 0.0
        run { 1e3 }.crushedBlocks shouldBe 0.0
        run { block -> if (block == 40) Double.NaN else 0.0 }.crushedBlocks shouldBe 0.0

        // One block of 1e15: the block itself (the mix stands huge at its end, the DC blockers' input state), and one
        // more whose end the limiter's gain is held down across (the blockers pass each of its two edges once)
        run { block -> if (block == 40) 1e15 else 0.0 }.crushedBlocks shouldBe 2.0

        // A huge value that keeps moving (1e15 flipping its sign every block, from block 100 of 400): every block from
        // its arrival to the end (the mix's side; the limiter's side alone begins three blocks later)
        val moving = run { block -> if (block >= 100) (if (block % 2 == 0) 1e15 else -1e15) else 0.0 }

        moving.crushedBlocks shouldBe 300.0

        // A huge CONSTANT: since L8-01's bound the DC blockers take it out within a sample, so the limiter sees one
        // sample of it and its gain never ends a block that low, while the sum has rounded the music under it away.
        // The blockers' input state still shows it: every block it stands (maintainer 2026-11-11). Under the level
        // (1e11, where the sum keeps 16 bits of the music) nothing counts
        run { block -> if (block >= 100) 1e15 else 0.0 }.crushedBlocks shouldBe 300.0
        run { block -> if (block >= 100) 1e11 else 0.0 }.crushedBlocks shouldBe 0.0

        // Either channel counts: the constant on one side only
        for (side in 0..1) {
            val house = HouseStage(sampleRate = sampleRate, blockFrames = blockFrames)
            val mix = StereoBuffer(blockFrames)
            val out = StereoBuffer(blockFrames)

            repeat(10) {
                mix.left.fill(if (side == 0) 1e15 else 0.0)
                mix.right.fill(if (side == 1) 1e15 else 0.0)
                house.process(mix = mix, out = out)
            }

            withClue("side $side") {
                house.crushedBlocks shouldBe 10.0
            }
        }

        // Not audio state: reset() leaves it
        moving.reset()
        moving.crushedBlocks shouldBe 300.0
    }
})
