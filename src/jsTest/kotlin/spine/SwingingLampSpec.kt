/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.peekandpoke.ultra.streams.ops.FrameClock
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.random.Random

/**
 * The lamp over the Motor's plate, the Spine's (KLANG-00389): its sway, the start page's sequence of commands, its
 * hover, the clean light until it settles, its pace on any screen; and its light as a stream that works only while
 * someone listens.
 */
class SwingingLampSpec : StringSpec({

    /** A lamp of a fixed seed and chance, and a clock of our own: [run] advances it [ms] in frames of [frameMs]. */
    class Rig {
        val lamp = SwingingLamp(seed = 7, random = Random(1))
        var now = 0.0
        var light = lamp.advanceTo(now)

        fun run(ms: Double, frameMs: Double = 1000.0 / 60) {
            val end = now + ms

            while (now < end) {
                now += frameMs
                light = lamp.advanceTo(now)
            }
        }
    }

    "the lamp starts over the middle and is back there after half a swing" {
        val (x0, y0) = SwingingLamp.swayAt(0.0)
        val (xHalf, yHalf) = SwingingLamp.swayAt(SwingingLamp.SWAY_PERIOD_MS / 2)

        hypot(x0, y0) shouldBe (0.0 plusOrMinus 1e-9)
        hypot(xHalf, yHalf) shouldBe (0.0 plusOrMinus 1e-9)
    }

    "the lamp swings out to its sway and never further" {
        val (xOut, yOut) = SwingingLamp.swayAt(SwingingLamp.SWAY_PERIOD_MS / 4)

        hypot(xOut, yOut) shouldBe (SwingingLamp.SWAY plusOrMinus 1e-9)

        // Five minutes, a frame at a time
        var ms = 0.0

        while (ms < 300_000.0) {
            val (x, y) = SwingingLamp.swayAt(ms)

            hypot(x, y) shouldBeLessThanOrEqual SwingingLamp.SWAY + 1e-9
            ms += 16.0
        }
    }

    "the line the lamp swings along turns round" {
        /** The swing's direction at its far end, [swings] swings in, in radians. */
        fun directionAt(swings: Int): Double {
            val (x, y) = SwingingLamp.swayAt(SwingingLamp.SWAY_PERIOD_MS * (swings + 0.25))

            return atan2(y, x)
        }

        val turnPerSwing = 2 * PI * SwingingLamp.SWAY_PERIOD_MS / SwingingLamp.SWAY_TURN_MS

        abs(directionAt(1) - directionAt(0)) shouldBe (turnPerSwing plusOrMinus 1e-6)
    }

    "dark at first; warming up shows no standby before a plate is ready, then the standby, then the light at power on" {
        val rig = Rig()

        rig.run(2000.0)
        rig.light.level shouldBe 0.0

        rig.lamp.warmUp()
        rig.run(2000.0)
        rig.light.level shouldBe 0.0

        rig.lamp.plateReady()
        rig.run(3000.0)
        rig.light.level shouldBe (SwingingLamp.STANDBY_INTENSITY / SwingingLamp.DEFAULT_INTENSITY plusOrMinus 0.01)

        rig.lamp.powerOn()
        rig.run(12_000.0)
        rig.light.level shouldBe (1.0 plusOrMinus 0.01)
        rig.light.fill shouldBe (SwingingLamp.FILL_ON plusOrMinus 0.002)
    }

    "over Make Music Now the light swells and the cone opens; it closes again after" {
        val rig = Rig()

        rig.lamp.plateReady()
        rig.lamp.powerOn()
        rig.run(12_000.0)

        rig.lamp.hoverStart()
        rig.run(SwingingLamp.CONE_OPEN_MS + 8000.0)
        rig.light.level shouldBe (SwingingLamp.HOVER_INTENSITY / SwingingLamp.DEFAULT_INTENSITY plusOrMinus 0.02)
        rig.light.z shouldBe (SwingingLamp.HOVER_Z plusOrMinus 1e-6)

        rig.lamp.hoverEnd()
        rig.run(SwingingLamp.CONE_CLOSE_MS + 12_000.0)
        rig.light.level shouldBe (1.0 plusOrMinus 0.02)
        rig.light.z shouldBe (SwingingLamp.DEFAULT_Z plusOrMinus 1e-6)
    }

    "the light comes on clean and stays so until it settles: no flicker, no stutter" {
        val rig = Rig()

        rig.lamp.plateReady()
        rig.lamp.powerOn()

        repeat(600) {
            rig.run(1000.0 / 60)
            rig.light.intensity shouldBe (rig.light.level * SwingingLamp.DEFAULT_INTENSITY plusOrMinus 1e-9)
        }

        rig.lamp.settle()

        var furthest = 0.0

        repeat(60 * 20) {
            rig.run(1000.0 / 60)
            furthest = maxOf(furthest, abs(rig.light.intensity - rig.light.level * SwingingLamp.DEFAULT_INTENSITY))
        }

        // Settled, the filament shimmers and the bulb stutters now and then
        furthest shouldBeGreaterThan 0.01
    }

    "the light moves at the same pace at 60 and at 120 frames a second" {
        val at60 = Rig().apply { lamp.plateReady(); lamp.powerOn(); run(1500.0, frameMs = 1000.0 / 60) }
        val at120 = Rig().apply { lamp.plateReady(); lamp.powerOn(); run(1500.0, frameMs = 1000.0 / 120) }

        at120.light.level shouldBe (at60.light.level plusOrMinus 0.01)
        at60.light.level shouldBeGreaterThan 0.1
    }

    "the same moment twice gives the same light, the lamp not moved on" {
        val rig = Rig()

        rig.lamp.plateReady()
        rig.lamp.powerOn()
        rig.run(500.0)

        val once = rig.lamp.advanceTo(rig.now)

        rig.lamp.advanceTo(rig.now) shouldBeSameInstanceAs once
        rig.lamp.advanceTo(rig.now - 10.0) shouldBeSameInstanceAs once
    }

    "off is dark at once, as before the start page's sequence" {
        val rig = Rig()

        rig.lamp.plateReady()
        rig.lamp.powerOn()
        rig.run(5000.0)
        rig.light.level shouldBeGreaterThan 0.5

        rig.lamp.off()
        rig.run(1000.0)
        rig.light.level shouldBe 0.0
        rig.light.intensity shouldBe 0.0
    }

    "the lamp's light is worked out only while someone listens: no listener, no frame asked for" {
        class Clock : FrameClock {
            var pending: ((Double) -> Unit)? = null
            var asked = 0
            var cancelled = 0

            override fun next(onFrame: (timeMs: Double) -> Unit): () -> Unit {
                asked++
                pending = onFrame

                return {
                    cancelled++
                    pending = null
                }
            }

            fun frame(timeMs: Double) {
                val run = pending ?: return

                pending = null
                run(timeMs)
            }
        }

        val clock = Clock()
        val lamp = SwingingLamp(seed = 7, random = Random(1))
        val light = VisualState.lightOf(lamp = lamp, clock = clock)

        clock.asked shouldBe 0

        val seen = mutableListOf<SwingingLamp.Light>()
        val stop = light.subscribeToStream { seen.add(it) }

        clock.asked shouldBe 1

        lamp.plateReady()
        lamp.powerOn()
        clock.frame(1000.0)
        clock.frame(1100.0)

        seen.last().level shouldBeGreaterThan 0.0

        stop()

        clock.cancelled shouldBe 1
        clock.pending shouldBe null

        val askedBefore = clock.asked

        clock.frame(1200.0)
        clock.asked shouldBe askedBefore
    }
})
