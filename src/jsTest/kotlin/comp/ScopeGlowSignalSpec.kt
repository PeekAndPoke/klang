/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.khronos.webgl.Float32Array
import org.khronos.webgl.set
import kotlin.math.PI
import kotlin.math.sin

/** The scope glow's signal: it listens to the low end, and it jumps up with a beat and dies away after it. */
class ScopeGlowSignalSpec : StringSpec({

    /** A frame of 2048 samples at 48 kHz: a sine of [hz] at [amplitude]. */
    fun sine(hz: Double, amplitude: Double): Float32Array = Float32Array(2048).also { frame ->
        for (i in 0 until 2048) {
            frame[i] = (amplitude * sin(2.0 * PI * hz * i / 48_000)).toFloat()
        }
    }

    "silence glows not at all" {
        glowEnergy(frame = Float32Array(2048), size = 2048, sampleRate = 48_000) shouldBe 0.0
    }

    "a kick's low note glows far brighter than a high note just as loud" {
        val low = glowEnergy(frame = sine(hz = 60.0, amplitude = 0.5), size = 2048, sampleRate = 48_000)
        val high = glowEnergy(frame = sine(hz = 4000.0, amplitude = 0.5), size = 2048, sampleRate = 48_000)

        // The low note passes the low-pass nearly whole and is lifted: 0.75 * min(1, ~0.46 * 1.6) + 0.25 * 0.5
        low shouldBeGreaterThan 0.6
        // The high note is the whole signal's share only: 0.25 * 0.5, plus a trace of the low-pass
        high shouldBeLessThan 0.2
    }

    "only the newest samples count: a beat that ended earlier in the frame does not glow" {
        val frame = Float32Array(2048)

        for (i in 0 until 600) {
            frame[i] = (0.8 * sin(2.0 * PI * 60.0 * i / 48_000)).toFloat()
        }

        glowEnergy(frame = frame, size = 2048, sampleRate = 48_000) shouldBeLessThan 0.05
    }

    "the follower rises with a beat within a few frames" {
        val follower = GlowFollower()

        follower.follow(target = 0.0, nowMs = 0.0)
        // 0.5 of the gap left after 10 ms, 0.25 after 20
        follower.follow(target = 1.0, nowMs = 20.0) shouldBe (0.75 plusOrMinus 1e-9)
    }

    "the follower dies away slowly after the beat" {
        val follower = GlowFollower()

        follower.follow(target = 1.0, nowMs = 0.0)
        // 0.92 of the gap kept per 10 ms: about 0.85 after 20 ms, still a glow
        follower.follow(target = 0.0, nowMs = 20.0) shouldBe (0.92 * 0.92 plusOrMinus 1e-9)
    }

    "the first frame takes its target as it is" {
        GlowFollower().follow(target = 0.7, nowMs = 1234.0) shouldBe 0.7
    }
})
