/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.sound

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * The sound's [Energy] while it plays, from what an analyser hears just now: the same measures a recording's
 * [Loudness] has, made as it goes.
 *
 * A recording is measured whole, so its levels are set against its own loudest passages. Live there is only what
 * was heard so far: each level is set against the loudest heard lately, which is forgotten slowly ([FORGET]
 * seconds), so a quiet song is not flat and a loud one not clipped. Until something loud was heard the levels read
 * high: there is nothing to compare with yet.
 *
 * As a meter does, a level follows a rise at once and falls slowly ([FALL] seconds). [Energy.slow] is the level
 * over about the last second and a half, [Energy.burst] how far the level stood above that lately.
 */
class LiveLoudness {

    /** One measure, set against its own loudest lately, with a meter's fall. */
    private class Meter {
        private var loudest = 0.0
        var level = 0.0
            private set

        fun feed(value: Double, dt: Double) {
            loudest = max(value, loudest * exp(-dt / FORGET))

            val shaped = if (loudest > SILENCE) min(1.3, max(0.0, value / (HEADROOM * loudest))) else 0.0

            level = max(shaped, level * exp(-dt / FALL))
        }
    }

    private val total = Meter()
    private val low = Meter()
    private val mid = Meter()
    private val high = Meter()

    private var slow = 0.0
    private var burst = 0.0
    private var last: Double? = null

    /**
     * Takes what is heard at [now] (seconds, of any clock that runs on): the sound's strength as a whole and in its
     * low, mid and high bands, each as an amplitude (not in dB), of any scale. Gives the energy as of now.
     */
    fun feed(now: Double, total: Double, low: Double, mid: Double, high: Double): Energy {
        // NaN-guard: a strength that is not a number counts as silence
        fun clean(v: Double) = if (v.isFinite() && v > 0) v else 0.0

        val before = last
        // the first time, and after a long rest (the page was hidden), a frame's length is assumed
        val dt = if (before == null || now - before <= 0 || now - before > 1.0) 1.0 / 60 else now - before

        last = now

        this.total.feed(clean(total), dt)
        this.low.feed(clean(low), dt)
        this.mid.feed(clean(mid), dt)
        this.high.feed(clean(high), dt)

        val level = this.total.level

        slow += (level - slow) * (1 - exp(-dt / SLOW))
        burst += (max(0.0, level - slow) - burst) * (1 - exp(-dt / BURST))

        return current()
    }

    /** The energy as of the last [feed]; silence before the first. */
    fun current(): Energy {
        return Energy(slow = slow, burst = burst, level = total.level, low = low.level, mid = mid.level, high = high.level)
    }

    companion object {
        /** How fast a level falls, in seconds to 1/e: as the recording's measure has it. */
        const val FALL = 0.25

        /** Over how long the slow level is taken, in seconds to 1/e (a recording's is the mean over 1.5 s). */
        const val SLOW = 0.75

        /** Over how long a burst is taken, in seconds to 1/e (a recording's is the mean over 0.12 s). */
        const val BURST = 0.06

        /** How slowly the loudest heard is forgotten, in seconds to 1/e. */
        const val FORGET = 30.0

        /** A level of 1 is this share of the loudest heard lately (a recording's is its 98th percentile). */
        const val HEADROOM = 0.85

        /** Below this nothing was heard yet. */
        const val SILENCE = 1e-9
    }
}
