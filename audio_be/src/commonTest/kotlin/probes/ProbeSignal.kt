/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.probes

import io.peekandpoke.klang.audio_be.AudioBackendContext
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.random.Random

/** The stage probe's sample rate (KLANG-00420, probe 1). */
const val PROBE_SAMPLE_RATE: Int = 48000

/** The block every probe renders in: the engine's pinned 128 frames, never raised to speed a run up. */
const val PROBE_BLOCK_FRAMES: Int = AudioBackendContext.RENDER_QUANTUM_FRAMES

/** Seconds of silence after every input, long enough to read a room's tail and a poisoned stage's recovery. */
const val PROBE_TAIL_SECONDS: Double = 10.0

/** The frame of the poison sample inside the poison signals' normal input (0.1 s in). */
const val PROBE_POISON_FRAME: Int = 4800

/**
 * One test signal of the stage probe, stereo: [left] and [right] are the same length, a whole number of
 * [PROBE_BLOCK_FRAMES] blocks.
 *
 * [inputEndFrame] is the first frame of the silence after the input (0 for silence itself, 1 for the unit impulse):
 * the tail is measured from there. [poisonFrame] is the frame of the one bad sample of a poison signal, -1 for the
 * others. The poison sits on [left] ONLY, so a stereo stage that lets it cross into the other channel shows it.
 */
class ProbeSignal(
    val name: String,
    val left: DoubleArray,
    val right: DoubleArray,
    val inputEndFrame: Int,
    val poisonFrame: Int = -1,
) {
    val frames: Int get() = left.size
}

/** [frames] rounded up to whole [blockFrames] blocks. */
private fun wholeBlocks(frames: Int, blockFrames: Int): Int = ((frames + blockFrames - 1) / blockFrames) * blockFrames

/**
 * The standard signals, in table order: a unit impulse, a step (DC 1.0 for 1 s), an exponential sine sweep from
 * 20 Hz to 20 kHz over 2 s at full scale, silence, full-scale white noise for 1 s (seeded, a different seed per
 * channel), then the poison cases: a 220 Hz sine at 0.5 for 0.5 s with ONE NaN, ONE +Infinity or ONE 1e30 sample on
 * the left at [PROBE_POISON_FRAME]. Every signal is followed by [tailSeconds] of silence.
 */
fun standardProbeSignals(
    sampleRate: Int = PROBE_SAMPLE_RATE,
    blockFrames: Int = PROBE_BLOCK_FRAMES,
    tailSeconds: Double = PROBE_TAIL_SECONDS,
): List<ProbeSignal> {
    val tail = (tailSeconds * sampleRate).toInt()

    fun signal(name: String, inputFrames: Int, poisonFrame: Int = -1, fill: (left: DoubleArray, right: DoubleArray) -> Unit): ProbeSignal {
        val frames = wholeBlocks(frames = inputFrames + tail, blockFrames = blockFrames)
        val left = DoubleArray(frames)
        val right = DoubleArray(frames)

        fill(left, right)

        return ProbeSignal(name = name, left = left, right = right, inputEndFrame = inputFrames, poisonFrame = poisonFrame)
    }

    val second = sampleRate
    val sweepFrames = 2 * sampleRate
    val poisonInputFrames = sampleRate / 2

    fun poisoned(name: String, bad: Double): ProbeSignal = signal(name = name, inputFrames = poisonInputFrames, poisonFrame = PROBE_POISON_FRAME) { l, r ->
        for (i in 0 until poisonInputFrames) {
            val x = 0.5 * sin(2.0 * PI * 220.0 * i / sampleRate)
            l[i] = x
            r[i] = x
        }

        l[PROBE_POISON_FRAME] = bad
    }

    return listOf(
        signal(name = "impulse", inputFrames = 1) { l, r ->
            l[0] = 1.0
            r[0] = 1.0
        },
        signal(name = "step", inputFrames = second) { l, r ->
            for (i in 0 until second) {
                l[i] = 1.0
                r[i] = 1.0
            }
        },
        signal(name = "sweep", inputFrames = sweepFrames) { l, r ->
            val f1 = 20.0
            val f2 = 20000.0
            val seconds = sweepFrames.toDouble() / sampleRate
            val rate = ln(f2 / f1)

            for (i in 0 until sweepFrames) {
                val t = i.toDouble() / sampleRate
                val x = sin(2.0 * PI * f1 * seconds / rate * (exp(t / seconds * rate) - 1.0))
                l[i] = x
                r[i] = x
            }
        },
        signal(name = "silence", inputFrames = 0) { _, _ -> },
        signal(name = "noise", inputFrames = second) { l, r ->
            val rngLeft = Random(0x5EED)
            val rngRight = Random(0x5EED + 1)

            for (i in 0 until second) {
                l[i] = rngLeft.nextDouble() * 2.0 - 1.0
                r[i] = rngRight.nextDouble() * 2.0 - 1.0
            }
        },
        poisoned(name = "nan", bad = Double.NaN),
        poisoned(name = "+inf", bad = Double.POSITIVE_INFINITY),
        poisoned(name = "1e30", bad = 1e30),
    )
}
