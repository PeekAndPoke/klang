/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.ultra.maths.Smoothing
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * A light bulb about to break, for the start page's lamp (the maintainer, 2026-10-27: "a bit more flicker ... but not
 * constantly, like a lightbulb that is about to break, stuttering, but then again quite stable for some time"): calm
 * stretches, then a short stutter in which the light drops in quick dips, now and then deeper, to half at the most
 * ("It gets too dark when it is stuttering, half only").
 *
 * [next] gives where the light heads, 1.0 while it is calm; the first stretch is always calm, so the light comes up
 * cleanly. The way there is the lamp's: it smooths these goals with a [Smoothing], a law of its own for each stutter,
 * which [onStutter] hands over as the stutter starts (the maintainer: "You could even randomize the smoothing type").
 */
class BulbStutter(
    private val random: Random = Random.Default,
    private val onStutter: (Smoothing) -> Unit = {},
) {

    companion object {
        /** A calm stretch, in ms: "stable for some time", but "a bit less stable in between" (the maintainer). */
        val CALM_MS = 2000.0..7000.0

        /** A stutter, in ms. */
        val STUTTER_MS = 250.0..1400.0

        /** One step of a stutter, in ms. */
        val STEP_MS = 30.0..120.0

        /** The chance that a step of a stutter is a dip, not the light back on. */
        const val DIP_CHANCE = 0.5

        /** How far a dip leaves the light. */
        val DIP_LEVEL = 0.6..0.85

        /** The chance that a dip is a deep one. */
        const val DEEP_CHANCE = 0.15

        /** How far a deep dip leaves the light: half, at the most. */
        val DEEP_LEVEL = 0.5..0.55
    }

    private var stuttering = false
    private var phaseLeftMs = draw(CALM_MS)
    private var stepLeftMs = 0.0
    private var level = 1.0

    /** Where the light heads, 0.5 to 1, after [deltaMs] more. */
    fun next(deltaMs: Double): Double {
        phaseLeftMs -= deltaMs

        if (phaseLeftMs <= 0.0) {
            stuttering = !stuttering
            phaseLeftMs = draw(if (stuttering) STUTTER_MS else CALM_MS)
            stepLeftMs = 0.0
            level = 1.0

            if (stuttering) {
                onStutter(smoothing())
            }
        }

        if (stuttering) {
            stepLeftMs -= deltaMs

            if (stepLeftMs <= 0.0) {
                stepLeftMs = draw(STEP_MS)
                level = when {
                    random.nextDouble() >= DIP_CHANCE -> 1.0
                    random.nextDouble() < DEEP_CHANCE -> draw(DEEP_LEVEL)
                    else -> draw(DIP_LEVEL)
                }
            }
        }

        return level
    }

    /**
     * A stutter's way between its goals, one of four: soft; a quick drop and a slower glow back, as a filament
     * cools and heats; a spring that flashes a little past full on its way back; a steady fade.
     */
    private fun smoothing(): Smoothing = when (random.nextInt(4)) {
        0 -> Smoothing.Exponential(keepPer10Ms = draw(0.4..0.7))

        1 -> Smoothing.AttackRelease(up = draw(0.75..0.9), down = draw(0.15..0.35))

        2 -> {
            val stiffness = draw(600.0..1500.0)

            // Below the critical damping (2 * sqrt(stiffness)): a small overshoot, a flash
            Smoothing.Spring(stiffness = stiffness, damping = 2.0 * sqrt(stiffness) * draw(0.45..0.75))
        }

        else -> Smoothing.Linear(unitsPerSecond = draw(3.0..8.0))
    }

    private fun draw(range: ClosedFloatingPointRange<Double>): Double =
        range.start + random.nextDouble() * (range.endInclusive - range.start)
}

/**
 * A [Smoothing] whose [law] can change on the way: each step goes by the law of the moment, and the motion (where the
 * value is, how fast it moves) stays with the stream that smooths, so a change of law never starts it over.
 */
class SwappableSmoothing(var law: Smoothing) : Smoothing {

    override fun step(motion: Smoothing.Motion, goal: Double, elapsedMs: Double): Smoothing.Motion =
        law.step(motion = motion, goal = goal, elapsedMs = elapsedMs)

    override fun isSettled(motion: Smoothing.Motion, goal: Double, near: Double): Boolean =
        law.isSettled(motion = motion, goal = goal, near = near)
}
