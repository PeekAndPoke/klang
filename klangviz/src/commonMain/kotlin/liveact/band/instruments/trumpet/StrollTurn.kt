/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.trumpet

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Which way the trumpet player faces on one stroll, from [from] to [to] seconds: she follows the way she [wants] to
 * face at a time (radians, off the way she faces at her place; it jumps where her way turns round or she nearly
 * stands) with a body's inertia, by the shorter way round and never faster than a body walking a bend.
 *
 * It is worked out in steps of a tenth of a second from the stroll's start, once, as far as it is asked for, and for
 * the whole of the stroll however long it is. A stroll that ends before it begins or as it begins (the plan can cut
 * one that short) has no turn: 0 all through.
 *
 * [handOver]: the turn of the stroll this one replaces (the same start, another end): up to [at] its turns are
 * taken over as they were, so the turn at [at] is the same before and after, and from there it follows [wants].
 */
class StrollTurn(
    val from: Double,
    val to: Double,
    handOver: StrollTurn? = null,
    at: Double = Double.NEGATIVE_INFINITY,
    private val wants: (Double) -> Double,
) {
    private val turns = mutableListOf<Double>()
    private var now = 0.0
    private var x = from
    private val end = to + 2 * STEP

    init {
        // the turns of the stroll this one replaces, as far as [at]: what she did then stays as it was
        if (handOver != null && handOver.from == from && at > from && to > from) {
            handOver.at(at)

            val count = min(handOver.turns.size, floor((at - from) / STEP).toInt() + 2)

            for (i in 0 until count) {
                turns.add(handOver.turns[i])
            }

            now = turns.lastOrNull() ?: 0.0
            x = from + turns.size * STEP
        }
    }

    /** Which way she faces at [t], off the way she faces at her place. */
    fun at(t: Double): Double {
        if (to <= from) {
            return 0.0
        }

        val at = max(0, floor((t - from) / STEP).toInt())

        while (turns.size < at + 2 && x < end) {
            val want = wants(x) - now

            now += max(-MOST, min(MOST, atan2(sin(want), cos(want)) * GAIN))
            turns.add(now)
            x += STEP
        }

        if (turns.size < 2) {
            return turns.lastOrNull() ?: 0.0
        }

        val i = min(turns.size - 2, at)
        val u = min(1.0, max(0.0, (t - from) / STEP - i))

        return turns[i] + (turns[i + 1] - turns[i]) * u
    }

    companion object {
        /** A step, in seconds. */
        const val STEP = 0.1

        /**
         * The most she turns in a step, in radians: 120 degrees a second, as fast as her way turns at most (see
         * [StrollRoute]). With 57 and a share of 0.2 she fell behind her planned way by up to 56 degrees in its
         * bends, and walked sideways (2026-11-19).
         */
        const val MOST = 0.21

        /** The share of what is left to turn that she turns in a step. */
        const val GAIN = 0.4
    }
}
