/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine

import io.peekandpoke.klang.common.math.PerlinNoise2D
import io.peekandpoke.klang.spine.SwingingLamp.Companion.easeOutExpo
import io.peekandpoke.ultra.maths.Smoothing
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * The lamp over the Motor's plate: a bulb on a cord, swinging slowly, its light easing toward what it is asked for,
 * its filament shimmering and, once it has [settle]d, stuttering now and then like a bulb about to break
 * ([BulbStutter]). Not drawn here: [advanceTo] works out its [Light] for a moment, and whoever shows the plate or
 * reflects the lamp draws from that (the maintainer, 2026-10-29: "The model of the lamp must be calculated in the
 * VisualState then. Or a dedicated SwingingLamp that is only hosted by VisualState"). [VisualState] hosts one.
 *
 * Its commands are the start page's sequence: black, [warmUp] (a dim standby once a plate is [plateReady]), [powerOn],
 * [settle]; [hoverStart] and [hoverEnd] over "Make Music Now"; [off] for a sequence that starts in the dark again. Moved
 * here from `MotorBackground` unchanged, but for the easings: they took a share per frame and now take it per 1/60 s,
 * so the light moves at the same pace on a 120 Hz screen.
 */
class SwingingLamp(seed: Int, random: Random = Random.Default) {

    companion object {
        /**
         * The lamp sways as if it hung on a cord, left and right of the centre, very slightly and very slowly (the
         * maintainer, 2026-10-28; 2026-10-29 "a bit wider", then "a bit faster and wider": 0.12, 0.18 with 16 s): this
         * far at the most, in the scene's units (the plate is 2 high, the lamp 4.5 in front of it), and one swing there
         * and back in [SWAY_PERIOD_MS].
         */
        const val SWAY = 0.22
        const val SWAY_PERIOD_MS = 13_000.0

        /**
         * The line the lamp sways along turns slowly round, as a Foucault pendulum's (the maintainer, 2026-10-28: "make
         * the light pendulum direction rotate slowly"): once round in this time.
         */
        const val SWAY_TURN_MS = 90_000.0

        /**
         * The light at rest (the maintainer, 2026-10-27: 1.62 was "a bit too bright"; 1.35 then); raised as the lamp
         * hung further away for a wider cone (2026-10-29: 1.55 at 3.5, 2.0 at 4.5), so the middle stays as bright: the
         * light falls off with distance.
         */
        const val DEFAULT_INTENSITY = 2.0

        /**
         * Over "Make Music Now" (the maintainer, 2026-10-26 and 27: twice the first swell was too bright, then 4.5 "a
         * bit too bright" too).
         */
        const val HOVER_INTENSITY = 3.6

        /** The light while the Motor warms up ([warmUp]): the plate and the title show, dim (the maintainer, 2026-10-26). */
        const val STANDBY_INTENSITY = DEFAULT_INTENSITY * 0.18

        /** The fill light, once powered on. */
        const val FILL_ON = 0.12

        /**
         * How far the lamp hangs in front of the plate: further away, its circle of light is wider (the maintainer,
         * 2026-10-29: "make the normal lightcone a bit wider", 3.0 to 3.5; then "the light normal radius can be half a
         * tile wider", 4.5: the circle is the plate's sheen, its radius in step with the height, about two tiles at
         * 3.5). Over "Make Music Now" the cone opens wider,
         * "like opening your eyes" (the maintainer, 2026-10-26: wider still).
         */
        const val DEFAULT_Z = 4.5
        const val HOVER_Z = 5.2

        /**
         * The eye opening over "Make Music Now", and closing again (the maintainer: "longer please", then rather than
         * twice as long, "just stronger ease out": [easeOutExpo]).
         */
        const val CONE_OPEN_MS = 5000.0
        const val CONE_CLOSE_MS = 2000.0

        /** How far the light moves toward its target in 1/60 s: calm, but quick while it warms up to the standby. */
        const val INTENSITY_EASE = 0.01
        const val WARM_UP_EASE = 0.05

        /** The fill light's and the shimmer's way toward theirs, in 1/60 s. */
        private const val FILL_EASE = 0.02
        private const val SHIMMER_EASE = 0.02

        /** The time the easings were made for: a frame at 60 a second. */
        private const val FRAME_MS = 1000.0 / 60

        /** The longest step one advance takes: a pause, a hidden tab, does not jump the lamp. */
        const val MAX_STEP_MS = 100.0

        /**
         * The lamp's place [elapsedMs] into its sway, in the scene's units: a pendulum swinging [SWAY] out to either side
         * every [SWAY_PERIOD_MS], the line it swings along turning once in [SWAY_TURN_MS].
         */
        fun swayAt(elapsedMs: Double): Pair<Double, Double> {
            val swing = SWAY * sin(2.0 * PI * elapsedMs / SWAY_PERIOD_MS)
            val direction = 2.0 * PI * elapsedMs / SWAY_TURN_MS

            return (swing * cos(direction)) to (swing * sin(direction))
        }

        /** The share of the way an easing of [perFrame] a frame goes in [elapsedMs]. */
        private fun share(perFrame: Double, elapsedMs: Double): Double = 1.0 - (1.0 - perFrame).pow(elapsedMs / FRAME_MS)

        /**
         * Ease out, exponential: most of the way at first, then a long, very soft arrival (the maintainer: "much
         * stronger ease out" than a quintic).
         */
        private fun easeOutExpo(t: Double): Double = if (t >= 1.0) 1.0 else 1.0 - 2.0.pow(-10.0 * t)
    }

    /**
     * The lamp's light at a moment: where it hangs, in the scene's units ([x], [y] in the sway, [z] in front of the
     * plate), how bright it shines ([intensity], with the flicker and the bulb), how bright it is asked to be against
     * its rest ([level], steady: 1 at rest, 0 off, more while hovered), and the fill light ([fill]).
     */
    data class Light(
        val x: Double,
        val y: Double,
        val z: Double,
        val intensity: Double,
        val level: Double,
        val fill: Double,
    ) {
        /** Where it is in its sway, from -1 to 1 each way, up positive: for things that reflect it. */
        val swayX: Double get() = x / SWAY
        val swayY: Double get() = y / SWAY

        companion object {
            /** Off, at rest over the middle. */
            val dark = Light(x = 0.0, y = 0.0, z = DEFAULT_Z, intensity = 0.0, level = 0.0, fill = 0.0)
        }
    }

    /** The filament's shimmer, from the user's seed: the same noise the plate's grain is made of. */
    private val noise = PerlinNoise2D(random = Random(seed))

    /** A plate is built: the standby light may show. */
    private var ready = false

    /** Whether the Motor warms up: the standby light is on from then. */
    private var warming = false
    private var poweredOn = false

    private var targetIntensity = 0.0
    private var currentEase = INTENSITY_EASE

    /** The eased intensity, without the flicker on top: the flicker never pollutes the easing. */
    private var currentIntensity = 0.0
    private var targetFill = 0.0
    private var fill = 0.0

    /** How long the lamp has been swaying, for its pendulum, and the filament's time. */
    private var swayElapsedMs = 0.0
    private var flickerTime = 0.0

    /** The cone's move to [targetZ]: from [coneFrom], over [coneMs], easing out hard. */
    private var z = DEFAULT_Z
    private var targetZ = DEFAULT_Z
    private var coneFrom = DEFAULT_Z
    private var coneElapsedMs = 0.0
    private var coneMs = 1.0

    /**
     * The bulb about to break: its goals are followed by a law of the stutter's own (the maintainer, 2026-10-28: "build
     * a stream with .smooth() for how bright the light is and throw values in it"; a stutter swaps the law, the motion
     * goes on where it is). Clean from the start: no stutter, no shimmer, until [settle].
     */
    private var bulbLaw: Smoothing = Smoothing.Exponential(keepPer10Ms = 0.5)
    private var bulbMotion = Smoothing.Motion(position = 1.0)
    private val bulb = BulbStutter(random = random, onStutter = { bulbLaw = it }).apply { steady = true }

    /** How much of the filament's shimmer shows: none while the bulb is clean, easing in once it [settle]s. */
    private var shimmer = 0.0

    /** The moment the light was last worked out for, and that light. */
    private var lastMs: Double? = null
    private var light = Light.dark

    /** A plate is built: from now the standby light shows while the Motor warms up. */
    fun plateReady() {
        ready = true

        if (warming && !poweredOn) {
            targetIntensity = STANDBY_INTENSITY
        }
    }

    /** The Motor warms up: the standby light fades in from black, quickly, once a plate is [plateReady]. */
    fun warmUp() {
        warming = true
        currentEase = WARM_UP_EASE

        if (ready && !poweredOn) {
            targetIntensity = STANDBY_INTENSITY
        }
    }

    /** The light comes on, calmly, and the fill light with it. */
    fun powerOn() {
        warming = true
        poweredOn = true
        currentEase = INTENSITY_EASE
        targetIntensity = DEFAULT_INTENSITY
        targetFill = FILL_ON
    }

    /**
     * The light comes on clean and stays so, no stutter, no shimmer, until this (the maintainer, 2026-10-28: "The light
     * must come clean on and stay clean until 5 seconds after the dynamo test"): then the shimmer eases in and the bulb
     * stutters now and then, calmly ([BulbStutter.SETTLED]).
     */
    fun settle() {
        bulb.temper = BulbStutter.SETTLED
        bulb.steady = false
    }

    /** Boosts the light and widens the cone: an element's mouse-enter. */
    fun hoverStart() {
        targetIntensity = HOVER_INTENSITY
        moveCone(to = HOVER_Z, ms = CONE_OPEN_MS)
    }

    /** Takes the hover back: the element's mouse-leave. */
    fun hoverEnd() {
        targetIntensity = when {
            poweredOn -> DEFAULT_INTENSITY
            warming -> STANDBY_INTENSITY
            else -> 0.0
        }
        moveCone(to = DEFAULT_Z, ms = CONE_CLOSE_MS)
    }

    /**
     * Dark at once, as before the start page's sequence: no light, no warm-up, the bulb clean again. The sway goes on
     * where it was.
     */
    fun off() {
        warming = false
        poweredOn = false
        targetIntensity = 0.0
        currentIntensity = 0.0
        currentEase = INTENSITY_EASE
        targetFill = 0.0
        fill = 0.0
        bulb.steady = true
        shimmer = 0.0
        light = light.copy(intensity = 0.0, level = 0.0, fill = 0.0)
    }

    private fun moveCone(to: Double, ms: Double) {
        coneFrom = z
        targetZ = to
        coneElapsedMs = 0.0
        coneMs = ms
    }

    /**
     * The light at [timeMs], any clock's milliseconds: the lamp moves on by the time since the last call, at most
     * [MAX_STEP_MS]. A second call for the same moment, or an earlier one, gives the same light again: several readers
     * may ask in one frame.
     */
    fun advanceTo(timeMs: Double): Light {
        val last = lastMs

        if (last != null && !(timeMs > last)) {
            return light
        }

        lastMs = timeMs

        // NaN-guard: a first call, or a clock that jumped, moves by nothing or by a frame's worth at the most
        val dt = if (last == null) 0.0 else min(MAX_STEP_MS, max(0.0, timeMs - last))

        swayElapsedMs += dt
        val (x, y) = swayAt(swayElapsedMs)

        coneElapsedMs += dt
        z = coneFrom + (targetZ - coneFrom) * easeOutExpo(min(1.0, coneElapsedMs / coneMs))

        currentIntensity += (targetIntensity - currentIntensity) * share(currentEase, dt)
        fill += (targetFill - fill) * share(FILL_EASE, dt)

        // A slow drift and a faster shimmer, both smooth noise, a few per cent: a living filament, not a faulty one.
        // None while the bulb is clean; once it settles, the shimmer eases in over a couple of seconds
        flickerTime += dt
        shimmer += ((if (bulb.steady) 0.0 else 1.0) - shimmer) * share(SHIMMER_EASE, dt)

        val flicker = 1.0 + shimmer * (
                // Three times slower than at first (the maintainer, 2026-10-26: "it flickers too often"); a bit livelier
                // between the stutters (2026-10-27: "a bit less stable in between")
                noise.noise(flickerTime * 0.0012, 7.7) * 0.07 + noise.noise(flickerTime * 0.007, 3.3) * 0.035
                )

        bulbMotion = bulbLaw.step(motion = bulbMotion, goal = bulb.next(dt), elapsedMs = dt)

        light = Light(
            x = x,
            y = y,
            z = z,
            intensity = currentIntensity * flicker * bulbMotion.position,
            level = currentIntensity / DEFAULT_INTENSITY,
            fill = fill,
        )

        return light
    }
}
