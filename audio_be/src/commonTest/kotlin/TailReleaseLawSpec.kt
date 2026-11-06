/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import kotlin.math.abs

/**
 * [TailRelease] runs its gain as a recurrence, one multiply per sample (KLANG-00290 (engine follow ups) item 25):
 * it must stay the decided law `exp(-k / tau)` over the whole release, to rounding, and end where the law ends.
 */
class TailReleaseLawSpec : StringSpec({

    for (rate in listOf(44100, 48000, 96000)) {
        "at $rate Hz the recurrence stays within 1e-10 of exp(-k / tau), relative, down to the floor" {
            val law = ReleaseLaw(rate)
            var worst = 0.0

            for (k in 0 until law.floorFrame) {
                val exact = law.closedForm(k)
                worst = maxOf(worst, abs(law.gain(k) - exact) / exact)
            }

            withClue("worst relative error $worst over ${law.floorFrame} frames") {
                worst shouldBeLessThan 1e-10
            }
        }

        "at $rate Hz the recurrence ends within one frame of the closed form's floor, at about 4.5 s" {
            val law = ReleaseLaw(rate)

            abs(law.floorFrame - law.closedFormFloorFrame) shouldBeLessThanOrEqual 1
            abs(law.floorFrame - (4.5 * rate).toInt()) shouldBeLessThanOrEqual 1
        }
    }

    "the engine's release is the recurrence, bit for bit, and 0 from the floor on" {
        val rate = 48000
        val frames = 128
        val law = ReleaseLaw(rate)
        val release = TailRelease(sampleRate = rate, blockFrames = frames)
        val ones = StereoBuffer(frames).also { b ->
            b.left.fill(1.0)
            b.right.fill(1.0)
        }
        val blocks = law.floorFrame / frames + 2
        var silentAt = -1

        release.restart()

        for (b in 0 until blocks) {
            val out = StereoBuffer(frames)
            val silent = release.addReleased(target = out, source = ones)

            for (i in 0 until frames) {
                val k = b * frames + i

                if (out.left[i] != law.gain(k) || out.right[i] != law.gain(k)) {
                    withClue("frame $k") {
                        out.left[i] shouldBe law.gain(k)
                        out.right[i] shouldBe law.gain(k)
                    }
                }
            }

            if (silent && silentAt < 0) {
                silentAt = b
            }
        }

        silentAt shouldBe law.floorFrame / frames
        release.nextGain() shouldBe 0.0
    }

    "the next gain is 0 the moment the gain is under the floor, before a block has zeroed it" {
        // One block of exactly the frames above the floor: it ends with the gain just under the floor, not yet zeroed
        val rate = 48000
        val law = ReleaseLaw(rate)
        val release = TailRelease(sampleRate = rate, blockFrames = law.floorFrame)
        val ones = StereoBuffer(law.floorFrame).also { b ->
            b.left.fill(1.0)
            b.right.fill(1.0)
        }

        release.restart()

        withClue("positive control: nothing in the block was under the floor") {
            release.addReleased(target = StereoBuffer(law.floorFrame), source = ones) shouldBe false
        }

        release.nextGain() shouldBe 0.0
    }

    "a restart starts the release over at exactly 1, after a release ran" {
        val rate = 48000
        val frames = 128
        val law = ReleaseLaw(rate)
        val release = TailRelease(sampleRate = rate, blockFrames = frames)
        val ones = StereoBuffer(frames).also { b ->
            b.left.fill(1.0)
            b.right.fill(1.0)
        }

        release.restart()

        repeat(100) { release.addReleased(target = StereoBuffer(frames), source = ones) }

        withClue("positive control: the release has moved") {
            release.nextGain() shouldBe law.gain(100 * frames)
        }

        release.restart()

        release.nextGain() shouldBe 1.0
        val out = StereoBuffer(frames)
        release.addReleased(target = out, source = ones)

        for (i in 0 until frames) {
            out.left[i] shouldBe law.gain(i)
        }
    }
})
