/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.peekandpoke.klang.audio_be.probes.allocatedBytesNow
import io.peekandpoke.klang.audio_be.probes.median
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import kotlin.random.Random

/**
 * What a built shimmer allocates at its note (KLANG-00429, audit finding L5-03, option (b)): its grain ring sized at the
 * build to what its grains read ([shimmerRingFrames]). At the default pitches `[0, 7, 12]` at 48 kHz that is 14,402
 * doubles, about 115 KB; before, every built shimmer allocated 96,000 (768 KB, 774,520 B per note through
 * `VoiceFactory` in the audit's measurement). Measured with the stage cost probe's byte counter ([allocatedBytesNow],
 * HotSpot's per-thread count) over the build of a saw under a shimmer at wet 0.5. The bound sits between the two.
 */
class ShimmerBuildBytesSpec : StringSpec({

    val shimmer = IgnitorDsl.Shimmer(inner = IgnitorDsl.Saw(freq = IgnitorDsl.Freq), wet = IgnitorDsl.Constant(0.5))

    "audit L5-03: a built shimmer at the default pitches allocates a ring of 14,402 frames, not 96,000" {
        val perRound = 20
        val rounds = 11
        val samples = DoubleArray(rounds)
        var sink = 0

        repeat(200) {
            sink += shimmer.buildExciter(random = Random(it), freqHz = 220.0, sampleRate = 48000).ignitor.hashCode()
        }

        for (r in 0 until rounds) {
            val b0 = allocatedBytesNow()

            repeat(perRound) {
                sink += shimmer.buildExciter(random = Random(it), freqHz = 220.0, sampleRate = 48000).ignitor.hashCode()
            }

            samples[r] = (allocatedBytesNow() - b0) / perRound
        }

        // keep the builds observable to the JIT
        if (sink == Int.MIN_VALUE) {
            println(sink)
        }

        val bytes = median(samples)

        println("ShimmerBuildBytesSpec: $bytes B per build")

        withClue("$bytes B per build of a saw under a shimmer") {
            bytes shouldBeLessThan 250_000.0
        }
    }
})
