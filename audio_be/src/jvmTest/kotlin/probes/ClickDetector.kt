/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.probes

import kotlin.math.roundToInt

/** The steady tone every click case plays (Hz): low, so its own sample-to-sample motion is small. */
const val CLICK_TONE_HZ: Double = 110.0

/** The tone's peak level. */
const val CLICK_TONE_LEVEL: Double = 0.5

/**
 * The windows and filters of [measureClick] (KLANG-00420, probe 3), all around the event frame E:
 * - BEFORE: [beforeSeconds] ending at E; EVENT: [eventSeconds] from E; AFTER: [afterSeconds] right after the event window.
 *   Before and after are the "surrounding signal" every reading is relative to.
 * - the high band: a linear-phase windowed-sinc highpass at [highpassHz] with [highpassTaps] taps (7-term Blackman-Harris
 *   window), applied to the whole output; its short-time RMS over [burstFrames] frames;
 * - the level track: a centred RMS over one period of [toneHz] (the tone's ripple averages out there), for the transition.
 */
data class ClickConfig(
    val sampleRate: Int = PROBE_SAMPLE_RATE,
    val toneHz: Double = CLICK_TONE_HZ,
    val beforeSeconds: Double = 0.25,
    val eventSeconds: Double = 1.2,
    val afterSeconds: Double = 0.3,
    val highpassHz: Double = 5000.0,
    val highpassTaps: Int = 511,
    val burstFrames: Int = 32,
) {
    val beforeFrames: Int get() = (beforeSeconds * sampleRate).toInt()
    val eventFrames: Int get() = (eventSeconds * sampleRate).toInt()
    val afterFrames: Int get() = (afterSeconds * sampleRate).toInt()

    /** Frames of the level track's window: one period of the tone. */
    val levelFrames: Int get() = (sampleRate / toneHz).roundToInt()

    /** Frames needed before the event: the before window plus the highpass's half length. */
    val leadFrames: Int get() = beforeFrames + highpassTaps

    /** Frames needed from the event on: the event and after windows. */
    val tailFrames: Int get() = eventFrames + afterFrames
}

/**
 * What [measureClick] read around one event on ONE channel. Ratios are event over steady (1 = nothing the surrounding
 * signal does not already do); "steady" is the larger of the before and after windows' value, so a stage that makes
 * the signal brighter or louder on purpose is judged against its own new state too.
 */
data class ClickReading(
    /** Largest |y[n] - y[n-1]| in the event window over the steady one: a JUMP. */
    val jumpRatio: Double,
    /** Largest |y[n] - 2 y[n-1] + y[n-2]| in the event window over the steady one: a jump OR a kink (a slope that breaks). */
    val kinkRatio: Double,
    /** The event window's largest first difference, raw. */
    val jumpEvent: Double,
    /** The steady windows' largest first difference, raw. */
    val jumpSteady: Double,
    /** The event window's largest second difference, raw. */
    val kinkEvent: Double,
    /** The steady windows' largest second difference, raw. */
    val kinkSteady: Double,
    /** Where the largest second difference sits, frames after the event. */
    val kinkOffsetFrames: Int,
    /** The high band's peak short-time RMS in the event window re the steady windows', dB. */
    val burstRelDb: Double,
    /** The same peak re the signal's RMS (the louder of before and after), dB: how loud the burst is. */
    val burstAbsDb: Double,
    /** RMS over the before window. */
    val levelBefore: Double,
    /** RMS over the after window. */
    val levelAfter: Double,
    /** The level track's 10 to 90 percent time from the before level to the after level, ms; NaN when the level does not move (under 5 percent). */
    val riseMs: Double,
    /** From the event to the 90 percent point (after which the track stays past it), ms; NaN as [riseMs]. */
    val settleMs: Double,
)

/**
 * One output of a click case: the [left] channel, the [right] one (null for mono), and the [eventFrame] the change
 * landed on.
 */
class ClickRender(
    val left: DoubleArray,
    val right: DoubleArray?,
    val eventFrame: Int,
)

/**
 * One row of the click table before it is measured: in [family], [stage]'s [knob] changing as [change]. [render]
 * produces the output for instant `i` of [instants] (the same change at a different phase of the tone), measured with
 * [config].
 */
class ClickCase(
    val family: String,
    val stage: String,
    val knob: String,
    val change: String,
    val instants: Int,
    val config: ClickConfig,
    val render: (instant: Int) -> ClickRender,
)

/** One measured row: [case], the worst [reading] over its instants and channels. */
class ClickRow(
    val case: ClickCase,
    val reading: ClickReading,
)
