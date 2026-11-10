/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.probes

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.ignitor.ArrayIgnitor
import io.peekandpoke.klang.audio_be.ignitor.IgniteContext
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.ignitor.buildExciter
import io.peekandpoke.klang.audio_be.katalyst.KatalystChainBuilder
import io.peekandpoke.klang.audio_be.katalyst.KatalystContext
import io.peekandpoke.klang.audio_be.probes.OutputFacts.Companion.DC_WINDOW_FRAMES
import io.peekandpoke.klang.audio_be.probes.OutputFacts.Companion.SILENCE_LEVEL
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.common.infra.toKlangMap
import kotlin.math.abs
import kotlin.random.Random

// The stage probe (KLANG-00420, probe 1): one stage alone under the standard signals, its facts as data. The register
// is `audio/ref/verification.md`, "The probes". A probe is a measurement, never a gate: these functions assert nothing,
// and a spec that guards a fixed finding calls them with a threshold of its own.

/** The smallest positive normal double (the JVM's `Double.MIN_NORMAL`, 2^-1022), written out so the probe runs on Kotlin/JS too. */
const val DOUBLE_MIN_NORMAL: Double = 2.2250738585072014E-308

/**
 * Reads the facts of one output channel. [input] is the same channel of the signal that produced it (for the input
 * peak), [inputEndFrame] the first frame of the silence after the input, [poisonFrame] the poison sample's frame or -1.
 */
fun measureOutput(output: DoubleArray, input: DoubleArray, inputEndFrame: Int, poisonFrame: Int): OutputFacts {
    val frames = output.size
    var peak = 0.0
    var nonFiniteCount = 0
    var firstNonFinite = NEVER
    var lastNonFinite = NEVER
    var lastLoud = NEVER
    var lastNonZero = NEVER
    var subnormalCount = 0
    var firstSubnormal = NEVER
    var minTailAbs = Double.POSITIVE_INFINITY

    for (i in 0 until frames) {
        val x = output[i]
        val a = abs(x)

        if (x.isFinite()) {
            if (a > peak) {
                peak = a
            }
        } else {
            nonFiniteCount++
            lastNonFinite = i

            if (firstNonFinite == NEVER) {
                firstNonFinite = i
            }
        }

        // NaN-guard: `a < SILENCE_LEVEL` is false for a NaN, so a NaN counts as loud.
        if (!(a < SILENCE_LEVEL)) {
            lastLoud = i
        }

        // A NaN is not 0.0 either.
        if (x != 0.0) {
            lastNonZero = i
        }

        if (a > 0.0 && a < DOUBLE_MIN_NORMAL) {
            subnormalCount++

            if (firstSubnormal == NEVER) {
                firstSubnormal = i
            }
        }

        if (i >= inputEndFrame && a > 0.0 && a < minTailAbs) {
            minTailAbs = a
        }
    }

    var inputPeak = 0.0

    for (x in input) {
        val a = abs(x)

        if (x.isFinite() && a > inputPeak) {
            inputPeak = a
        }
    }

    val finiteAgain = when {
        nonFiniteCount == 0 -> 0
        lastNonFinite == frames - 1 -> NEVER
        else -> lastNonFinite + 1 - (if (poisonFrame >= 0) poisonFrame else firstNonFinite)
    }

    return OutputFacts(
        frames = frames,
        peak = peak,
        inputPeak = inputPeak,
        dcInput = if (inputEndFrame > 0) mean(output, from = maxOf(0, inputEndFrame - DC_WINDOW_FRAMES), to = inputEndFrame) else Double.NaN,
        dcEnd = mean(output, from = maxOf(0, frames - DC_WINDOW_FRAMES), to = frames),
        nonFiniteCount = nonFiniteCount,
        firstNonFiniteFrame = firstNonFinite,
        lastNonFiniteFrame = lastNonFinite,
        finiteAgainFrames = finiteAgain,
        tailFrames = framesAfter(last = lastLoud, frames = frames, from = inputEndFrame),
        zeroFrames = framesAfter(last = lastNonZero, frames = frames, from = inputEndFrame),
        subnormalCount = subnormalCount,
        firstSubnormalFrame = firstSubnormal,
        minTailAbs = minTailAbs,
    )
}

/** Frames from [from] until just after [last], 0 when [last] lies before [from], [NEVER] when [last] is the final frame. */
private fun framesAfter(last: Int, frames: Int, from: Int): Int = when {
    last == frames - 1 -> NEVER
    else -> maxOf(0, last + 1 - from)
}

/** The mean of [buffer] over frames [from] until [to]. */
private fun mean(buffer: DoubleArray, from: Int, to: Int): Double {
    var sum = 0.0

    for (i in from until to) {
        sum += buffer[i]
    }

    return sum / (to - from)
}

/** Runs [stage] over [signal] and reads both channels (the left only for a mono stage). */
fun measureStage(stage: ProbeStage, signal: ProbeSignal): StageFacts {
    val out = stage.render(signal)
    val left = measureOutput(output = out.left, input = signal.left, inputEndFrame = signal.inputEndFrame, poisonFrame = signal.poisonFrame)
    val right = out.right?.let {
        measureOutput(output = it, input = signal.right, inputEndFrame = signal.inputEndFrame, poisonFrame = -1)
    }

    return StageFacts(left = left, right = right)
}

/** The worse of two channels' facts, fact by fact: [NEVER] beats any frame count, a larger count beats a smaller. */
fun worstOf(a: OutputFacts, b: OutputFacts): OutputFacts {
    fun neverWins(x: Int, y: Int): Int = if (x == NEVER || y == NEVER) NEVER else maxOf(x, y)

    fun firstOf(x: Int, y: Int): Int = when {
        x == NEVER -> y
        y == NEVER -> x
        else -> minOf(x, y)
    }

    // NaN-guard: a NaN mean is worse than any number, and `abs(NaN) > x` is false.
    fun largerMean(x: Double, y: Double): Double = when {
        x != x -> x
        y != y -> y
        abs(y) > abs(x) -> y
        else -> x
    }

    return OutputFacts(
        frames = maxOf(a.frames, b.frames),
        peak = maxOf(a.peak, b.peak),
        inputPeak = maxOf(a.inputPeak, b.inputPeak),
        dcInput = largerMean(a.dcInput, b.dcInput),
        dcEnd = largerMean(a.dcEnd, b.dcEnd),
        nonFiniteCount = a.nonFiniteCount + b.nonFiniteCount,
        firstNonFiniteFrame = firstOf(a.firstNonFiniteFrame, b.firstNonFiniteFrame),
        lastNonFiniteFrame = maxOf(a.lastNonFiniteFrame, b.lastNonFiniteFrame),
        finiteAgainFrames = neverWins(a.finiteAgainFrames, b.finiteAgainFrames),
        tailFrames = neverWins(a.tailFrames, b.tailFrames),
        zeroFrames = neverWins(a.zeroFrames, b.zeroFrames),
        subnormalCount = a.subnormalCount + b.subnormalCount,
        firstSubnormalFrame = firstOf(a.firstSubnormalFrame, b.firstSubnormalFrame),
        minTailAbs = minOf(a.minTailAbs, b.minTailAbs),
    )
}

/**
 * A Katalyst chain built from [dsl] as a stage: the production builder ([KatalystChainBuilder]) with its own ring and
 * reverb shelves, configured the way the output host configures its chain (`applyParams(null)` before every block,
 * so every knob is its authored value), then `process` and `processDuck` per block, stereo in place on the mix.
 *
 * A chain with a duck gets a sidechain: the SAME signal, both channels, on a buffer of its own (the cylinder host's
 * cross-cylinder pass hands another cylinder's mix there), so the duck runs alone under its own trigger.
 */
fun katalystProbeStage(
    dsl: KatalystDsl,
    sampleRate: Int = PROBE_SAMPLE_RATE,
    blockFrames: Int = PROBE_BLOCK_FRAMES,
): ProbeStage = ProbeStage { signal ->
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

        chain.applyParams(null)
        chain.process(ctx)
        chain.processDuck(ctx)

        ctx.mixBuffer.left.copyInto(destination = left, destinationOffset = at, startIndex = 0, endIndex = blockFrames)
        ctx.mixBuffer.right.copyInto(destination = right, destinationOffset = at, startIndex = 0, endIndex = blockFrames)
        at += blockFrames
    }

    ProbeOutput(left = left, right = right)
}

/**
 * An Ignitor tree as a stage, mono. The signal enters as the voice's SAMPLE PLAYHEAD: every [IgnitorDsl.Sample] leaf
 * of [tree] is built over an [ArrayIgnitor] playing the signal's left channel, the seam the engine itself uses for a
 * sample voice (`buildExciter`'s `sampleSource`). The tree goes through the production build (`buildExciter`: the
 * gates, the slots, the optimizer) with [params] as the note's `ignitorParams`, and renders in whole blocks from
 * frame 0 with the gate closing at the input's end (at least one frame), the way `Voice` drives its tree.
 */
fun ignitorProbeStage(
    tree: IgnitorDsl,
    params: Map<String, Double> = emptyMap(),
    freqHz: Double = 220.0,
    sampleRate: Int = PROBE_SAMPLE_RATE,
    blockFrames: Int = PROBE_BLOCK_FRAMES,
): ProbeStage = ProbeStage { signal ->
    val built = tree.buildExciter(
        ignitorParams = params.toKlangMap(),
        random = Random(7),
        freqHz = freqHz,
        sampleRate = sampleRate,
        blockFrames = blockFrames,
        sampleSource = ArrayIgnitor(signal.left),
    )
    val gate = maxOf(1, signal.inputEndFrame)
    val ctx = IgniteContext(
        sampleRate = sampleRate,
        voiceDurationFrames = gate,
        gateEndFrame = gate,
        scratchBuffers = ScratchBuffers(blockFrames),
        random = Random(7),
    )
    val buffer = AudioBuffer(blockFrames)
    val left = DoubleArray(signal.frames)
    var at = 0

    while (at < signal.frames) {
        ctx.voiceElapsedFrames = at
        ctx.updateOffsetAndLength(offset = 0, length = blockFrames)
        built.ignitor.generate(buffer, freqHz, ctx)
        buffer.copyInto(destination = left, destinationOffset = at, startIndex = 0, endIndex = blockFrames)
        at += blockFrames
    }

    ProbeOutput(left = left, right = null)
}
