/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe

/**
 * Guards the final master/output stage extracted from KlangAudioRenderer (D2·1): [MasterStage.process] runs on the
 * summed mix, so the wiring (DC → limiter → clip) is covered here in isolation. The clip is the real one:
 * [MasterStage.process] calls [clipOutput] and [clipSample], whose boundary table (both clamp branches, the
 * boundaries, the non-finite rows, no quantisation) lives in `OutputClipSpec`; the rows below show the clamp
 * firing through [MasterStage.process], and that no non-finite sample reaches it (phase 3 step 12, risk R0).
 */
class MasterStageSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 64

    "silent mix produces all-zero output" {
        val master = MasterStage(sampleRate = sampleRate, blockFrames = blockFrames)
        val mix = StereoBuffer(blockFrames)            // cleared on construction
        val out = StereoBuffer(blockFrames).apply { fill(0.999) }  // non-zero, must be overwritten

        master.process(mix, out)

        out.isExactlySilent() shouldBe true
    }

    "output routes channels independently" {
        // The master limiter has lookahead, so a left-only impulse emerges
        // HOUSE_LIMITER_LOOKAHEAD_SECONDS later — past the end of a single 64-frame block. Render enough
        // blocks to carry it through, then look for it wherever it lands.
        val master = MasterStage(sampleRate = sampleRate, blockFrames = blockFrames)
        val out = StereoBuffer(blockFrames)
        val leftSeen = mutableListOf<Int>()
        val rightSeen = mutableListOf<Int>()

        repeat(8) { block ->
            val mix = StereoBuffer(blockFrames)
            // left-only impulse in the first block, well below the -1 dB limiter threshold
            if (block == 0) mix.left[0] = 0.5

            master.process(mix, out)

            for (i in 0 until blockFrames) {
                if (out.left[i] != 0.0) leftSeen += block * blockFrames + i
                if (out.right[i] != 0.0) rightSeen += block * blockFrames + i
            }
        }

        leftSeen.shouldNotBeEmpty()          // the left impulse does come out...
        rightSeen.shouldBeEmpty()            // ...and never leaks into the right channel
    }

    "DC offset does not cost the limiter headroom — the blockers run BEFORE it" {
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
            val master = MasterStage(sampleRate = sampleRate, blockFrames = blockFrames)
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
                master.process(mix, out)
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

        // Within ~1% — the DC is gone before the limiter ever sees it.
        (kotlin.math.abs(clean - offset) < clean / 100) shouldBe true
    }

    "the stage reports the latency it actually adds" {
        // Phase 5: the FE latency budget and the offline frame count both read this. If it drifts
        // from the real delay, visuals misalign and offline renders truncate — silently, in both
        // cases, because nothing throws.
        val master = MasterStage(sampleRate = sampleRate, blockFrames = blockFrames)
        val expected = (MasterStage.HOUSE_LIMITER_LOOKAHEAD_SECONDS * sampleRate).toInt()

        master.latencyFrames shouldBe expected
        master.latencyMs shouldBe (expected * 1000.0 / sampleRate)

        // ...and it must match what the stage really does. Feed one impulse, find it in the output.
        val out = StereoBuffer(blockFrames)
        var foundAt = -1
        var frame = 0

        repeat(16) { block ->
            val mix = StereoBuffer(blockFrames)
            if (block == 0) mix.left[0] = 0.5
            master.process(mix, out)
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
        val master = MasterStage(sampleRate = sampleRate, blockFrames = blockFrames)
        val out = StereoBuffer(blockFrames)
        var sawMax = false
        var sawMin = false

        repeat(200) { block ->
            val mix = StereoBuffer(blockFrames)

            for (i in 0 until blockFrames) {
                val t = (block * blockFrames + i).toDouble() / sampleRate
                mix.left[i] = 1000.0 * kotlin.math.sin(2.0 * kotlin.math.PI * 220.0 * t)
            }

            master.process(mix, out)

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
        val master = MasterStage(sampleRate = sampleRate, blockFrames = blockFrames)
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

            master.process(mix, out)

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
})
