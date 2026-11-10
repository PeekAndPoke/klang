/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.probes

import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.katalyst.KatalystChainBuilder
import io.peekandpoke.klang.audio_be.katalyst.KatalystContext
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.common.infra.KlangMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

// The click detector (KLANG-00420, probe 3): does a knob jump, a stage switching, or a voice edge put a discontinuity
// into the signal that the surrounding signal does not explain? A measurement, never a gate: these functions assert
// nothing about a stage; a spec that guards a fixed finding calls [measureClick] with a threshold of its own.

/**
 * The click detector's highpass: a linear-phase FIR of [ClickConfig.highpassTaps] taps (odd), a windowed-sinc lowpass at
 * [ClickConfig.highpassHz] under the symmetric 7-term Blackman-Harris window, normalised to a DC gain of exactly 1,
 * subtracted from a unit impulse at its centre. A FIR on the whole run, never an FFT brickwall on a window
 * (`audio/ref/verification.md`, "A measurement"): its stopband is printed with every table ([firResponseDb]).
 */
fun clickHighpass(config: ClickConfig = ClickConfig()): DoubleArray {
    val taps = config.highpassTaps

    require(taps % 2 == 1) { "the highpass needs an odd tap count" }

    val half = taps / 2
    val fc = config.highpassHz / config.sampleRate
    val lowpass = DoubleArray(taps) { i ->
        val m = i - half
        val sinc = if (m == 0) 2.0 * fc else sin(2.0 * PI * fc * m) / (PI * m)
        var w = 0.0
        var sign = 1.0

        for (k in BLACKMAN_HARRIS_7.indices) {
            w += sign * BLACKMAN_HARRIS_7[k] * cos(2.0 * PI * k * i / (taps - 1))
            sign = -sign
        }

        sinc * w
    }
    val gain = lowpass.sum()
    val highpass = DoubleArray(taps) { -lowpass[it] / gain }

    highpass[half] += 1.0

    return highpass
}

/** The magnitude response of the FIR [taps] at [hz], in dB. */
fun firResponseDb(taps: DoubleArray, hz: Double, sampleRate: Int = PROBE_SAMPLE_RATE): Double {
    val omega = 2.0 * PI * hz / sampleRate
    var re = 0.0
    var im = 0.0

    for (i in taps.indices) {
        re += taps[i] * cos(omega * i)
        im -= taps[i] * sin(omega * i)
    }

    return 10.0 * log10(re * re + im * im)
}

/** [taps] applied at frame [n] of [y], centred (no delay); frames outside [y] read as 0. */
private fun filteredAt(y: DoubleArray, taps: DoubleArray, n: Int): Double {
    val half = taps.size / 2
    var sum = 0.0

    for (j in taps.indices) {
        val at = n + j - half

        if (at >= 0 && at < y.size) {
            sum += taps[j] * y[at]
        }
    }

    return sum
}

/** The largest short-time RMS (over [burst] frames) of [y] through [taps], over the windows that lie inside [from] until [to]. */
private fun peakBurst(y: DoubleArray, taps: DoubleArray, from: Int, to: Int, burst: Int): Double {
    val filtered = DoubleArray(to - from) { filteredAt(y = y, taps = taps, n = from + it) }
    var peak = 0.0

    for (start in 0..filtered.size - burst) {
        var sum = 0.0

        for (i in start until start + burst) {
            sum += filtered[i] * filtered[i]
        }

        if (sum > peak) {
            peak = sum
        }
    }

    return sqrt(peak / burst)
}

/** The largest |first difference| (order 1) or |second difference| (order 2) of [y] at frames [from] until [to], and its frame. */
private fun peakDifference(y: DoubleArray, from: Int, to: Int, order: Int): Pair<Double, Int> {
    var peak = 0.0
    var at = from

    for (n in from until to) {
        val d = if (order == 1) y[n] - y[n - 1] else y[n] - 2.0 * y[n - 1] + y[n - 2]
        val a = abs(d)

        if (a > peak) {
            peak = a
            at = n
        }
    }

    return peak to at
}

/** The RMS of [y] over frames [from] until [to]. */
private fun rms(y: DoubleArray, from: Int, to: Int): Double {
    var sum = 0.0

    for (n in from until to) {
        sum += y[n] * y[n]
    }

    return sqrt(sum / (to - from))
}

/** [event] over [steady]; 1 when both are 0 (nothing anywhere), +Infinity when only [steady] is. */
private fun ratio(event: Double, steady: Double): Double = when {
    steady > 0.0 -> event / steady
    event > 0.0 -> Double.POSITIVE_INFINITY
    else -> 1.0
}

/**
 * Reads ONE channel [y] around the event at [eventFrame] (see [ClickConfig] for the windows). Needs
 * [ClickConfig.leadFrames] before the event and [ClickConfig.tailFrames] from it on.
 *
 * - jump and kink: the largest first and second difference in the event window over the largest in the before and
 *   after windows ([ClickReading.jumpRatio], [ClickReading.kinkRatio]). A level step of s at a sample reads s in both; a
 *   slope that breaks without a step reads only in the kink.
 * - burst: the highpassed signal's peak short-time RMS in the event window re the same in the steady windows, and re
 *   the signal's RMS. The high-band windows sit a half filter length earlier (the FIR is centred, so a step at E
 *   reaches back that far), and the after window starts and ends a half length inside its frames.
 * - transition: the centred one-period RMS track from the before level to the after level, 10 and 90 percent.
 */
fun measureClick(y: DoubleArray, eventFrame: Int, config: ClickConfig = ClickConfig()): ClickReading {
    val e = eventFrame
    val taps = clickHighpass(config)
    val half = taps.size / 2

    require(e - config.leadFrames >= 2) { "not enough frames before the event" }
    require(e + config.tailFrames <= y.size) { "not enough frames after the event" }
    require(config.afterFrames > 2 * half + config.burstFrames) { "the after window is shorter than the highpass" }

    val beforeFrom = e - config.beforeFrames
    val eventTo = e + config.eventFrames
    val afterTo = eventTo + config.afterFrames

    val (jumpBefore, _) = peakDifference(y = y, from = beforeFrom, to = e, order = 1)
    val (jumpAfter, _) = peakDifference(y = y, from = eventTo, to = afterTo, order = 1)
    val (jumpEvent, _) = peakDifference(y = y, from = e, to = eventTo, order = 1)
    val (kinkBefore, _) = peakDifference(y = y, from = beforeFrom, to = e, order = 2)
    val (kinkAfter, _) = peakDifference(y = y, from = eventTo, to = afterTo, order = 2)
    val (kinkEvent, kinkAt) = peakDifference(y = y, from = e, to = eventTo, order = 2)
    val jumpSteady = max(jumpBefore, jumpAfter)
    val kinkSteady = max(kinkBefore, kinkAfter)

    val burst = config.burstFrames
    val burstBefore = peakBurst(y = y, taps = taps, from = beforeFrom - half, to = e - half, burst = burst)
    val burstAfter = peakBurst(y = y, taps = taps, from = eventTo + half, to = afterTo - half, burst = burst)
    val burstEvent = peakBurst(y = y, taps = taps, from = e - half, to = eventTo + half, burst = burst)
    val burstSteady = max(burstBefore, burstAfter)

    val levelBefore = rms(y = y, from = beforeFrom, to = e)
    val levelAfter = rms(y = y, from = eventTo, to = afterTo)
    val signal = max(levelBefore, levelAfter)

    val (riseFrames, settleFrames) = transition(y = y, eventFrame = e, levelBefore = levelBefore, levelAfter = levelAfter, config = config)
    val ms = 1000.0 / config.sampleRate

    return ClickReading(
        jumpRatio = ratio(event = jumpEvent, steady = jumpSteady),
        kinkRatio = ratio(event = kinkEvent, steady = kinkSteady),
        jumpEvent = jumpEvent,
        jumpSteady = jumpSteady,
        kinkEvent = kinkEvent,
        kinkSteady = kinkSteady,
        kinkOffsetFrames = kinkAt - e,
        burstRelDb = 20.0 * log10(ratio(event = burstEvent, steady = burstSteady)),
        burstAbsDb = if (signal > 0.0) 20.0 * log10(burstEvent / signal) else Double.NaN,
        levelBefore = levelBefore,
        levelAfter = levelAfter,
        riseMs = riseFrames * ms,
        settleMs = settleFrames * ms,
    )
}

/**
 * The level track's transition: frames from 10 to 90 percent of the way from [levelBefore] to [levelAfter], and from the
 * event to the 90 percent point (the frame after the last one short of it). The track is the RMS over one period of
 * the tone, centred on each frame, scanned from one period before the event to the event window's end. NaN for both
 * when the level moves by 5 percent or less, or never reaches 10 percent.
 */
private fun transition(y: DoubleArray, eventFrame: Int, levelBefore: Double, levelAfter: Double, config: ClickConfig): Pair<Double, Double> {
    val step = levelAfter - levelBefore

    if (abs(step) <= 0.05 * max(levelBefore, levelAfter)) {
        return Double.NaN to Double.NaN
    }

    val w = config.levelFrames
    val lead = w / 2
    val scanFrom = eventFrame - w
    val scanTo = eventFrame + config.eventFrames
    // prefix[i] = sum of y^2 over frames base until base + i.
    val base = scanFrom - lead
    val prefix = DoubleArray(scanTo - scanFrom + w + 1)

    for (i in 1 until prefix.size) {
        val x = y[base + i - 1]

        prefix[i] = prefix[i - 1] + x * x
    }

    var t10 = -1
    var t90 = -1

    for (n in scanFrom until scanTo) {
        val from = n - lead - base
        val level = sqrt(max(0.0, prefix[from + w] - prefix[from]) / w)
        val frac = (level - levelBefore) / step

        if (t10 < 0 && frac >= 0.1) {
            t10 = n
        }

        if (frac < 0.9) {
            t90 = n + 1
        }
    }

    if (t10 < 0) {
        return Double.NaN to Double.NaN
    }

    return (t90 - t10).toDouble() to (t90 - eventFrame).toDouble()
}

/** The larger of two numbers where NaN means "no value": NaN only when both are. */
private fun maxOfKnown(a: Double, b: Double): Double = when {
    a != a -> b // NaN-guard
    b != b -> a // NaN-guard
    else -> max(a, b)
}

/**
 * Two readings folded into the worse one, field by field: the larger ratio, burst, raw difference, level and time; the
 * kink's offset of the reading with the larger kink ratio.
 */
fun worstClick(a: ClickReading, b: ClickReading): ClickReading = ClickReading(
    jumpRatio = max(a.jumpRatio, b.jumpRatio),
    kinkRatio = max(a.kinkRatio, b.kinkRatio),
    jumpEvent = max(a.jumpEvent, b.jumpEvent),
    jumpSteady = max(a.jumpSteady, b.jumpSteady),
    kinkEvent = max(a.kinkEvent, b.kinkEvent),
    kinkSteady = max(a.kinkSteady, b.kinkSteady),
    kinkOffsetFrames = if (b.kinkRatio > a.kinkRatio) b.kinkOffsetFrames else a.kinkOffsetFrames,
    burstRelDb = maxOfKnown(a.burstRelDb, b.burstRelDb),
    burstAbsDb = maxOfKnown(a.burstAbsDb, b.burstAbsDb),
    levelBefore = max(a.levelBefore, b.levelBefore),
    levelAfter = max(a.levelAfter, b.levelAfter),
    riseMs = maxOfKnown(a.riseMs, b.riseMs),
    settleMs = maxOfKnown(a.settleMs, b.settleMs),
)

/** Both channels of [render] read by [measureClick] and folded ([worstClick]); the left alone for a mono render. */
fun measureClickStereo(render: ClickRender, config: ClickConfig = ClickConfig()): ClickReading {
    val left = measureClick(y = render.left, eventFrame = render.eventFrame, config = config)
    val right = render.right ?: return left

    return worstClick(left, measureClick(y = right, eventFrame = render.eventFrame, config = config))
}

/**
 * The click cases' input: [frames] of a sine at [hz] and peak [level] that reaches its PEAK at [peakFrame] (a level step
 * there is the worst case for a jump), faded in over its first [fadeFrames] by a raised cosine (so the input's own
 * onset, and its echo in a delay or a room, is no click).
 */
fun clickTone(
    frames: Int,
    peakFrame: Int,
    hz: Double = CLICK_TONE_HZ,
    level: Double = CLICK_TONE_LEVEL,
    fadeFrames: Int = PROBE_SAMPLE_RATE / 10,
    sampleRate: Int = PROBE_SAMPLE_RATE,
): DoubleArray = DoubleArray(frames) { n ->
    val x = level * sin(2.0 * PI * hz * (n - peakFrame) / sampleRate + PI / 2.0)

    if (n < fadeFrames) x * 0.5 * (1.0 - cos(PI * n / fadeFrames)) else x
}

/**
 * A Katalyst chain built from [dsl] whose OWNER changes at [eventFrame]: the production builder with its own ring and
 * reverb shelves, then per block `applyParams` with the owner's slot map ([before] for every block that starts before
 * [eventFrame], [after] from there on; one map instance each, as a sounding owner hands over the same map every block),
 * `process` and `processDuck`, stereo in place on the mix, as [katalystProbeStage] drives a chain. [eventFrame] must be
 * a block boundary. A chain with a duck gets the signal itself as its sidechain.
 */
fun katalystOwnerChangeStage(
    dsl: KatalystDsl,
    before: KlangMap<Double>,
    after: KlangMap<Double>,
    eventFrame: Int,
    sampleRate: Int = PROBE_SAMPLE_RATE,
    blockFrames: Int = PROBE_BLOCK_FRAMES,
): ProbeStage = ProbeStage { signal ->
    require(eventFrame % blockFrames == 0) { "the change lands on a block boundary" }

    val chain = KatalystChainBuilder.build(
        dsl = dsl,
        sampleRate = sampleRate,
        blockFrames = blockFrames,
        rings = SizedBuffers.forRings(sampleRate),
        reverbs = ReverbUnits(sampleRate),
    )
    val ctx = KatalystContext(blockFrames = blockFrames, mixBuffer = StereoBuffer(blockFrames))
    val sidechain = if (chain.duck != null) StereoBuffer(blockFrames) else null
    val left = DoubleArray(signal.frames)
    val right = DoubleArray(signal.frames)
    var at = 0

    while (at < signal.frames) {
        signal.left.copyInto(destination = ctx.mixBuffer.left, destinationOffset = 0, startIndex = at, endIndex = at + blockFrames)
        signal.right.copyInto(destination = ctx.mixBuffer.right, destinationOffset = 0, startIndex = at, endIndex = at + blockFrames)

        if (sidechain != null) {
            signal.left.copyInto(destination = sidechain.left, destinationOffset = 0, startIndex = at, endIndex = at + blockFrames)
            signal.right.copyInto(destination = sidechain.right, destinationOffset = 0, startIndex = at, endIndex = at + blockFrames)
            ctx.sidechainBuffer = sidechain
        }

        chain.applyParams(if (at < eventFrame) before else after)
        chain.process(ctx)
        chain.processDuck(ctx)

        ctx.mixBuffer.left.copyInto(destination = left, destinationOffset = at, startIndex = 0, endIndex = blockFrames)
        ctx.mixBuffer.right.copyInto(destination = right, destinationOffset = at, startIndex = 0, endIndex = blockFrames)
        at += blockFrames
    }

    ProbeOutput(left = left, right = right)
}
