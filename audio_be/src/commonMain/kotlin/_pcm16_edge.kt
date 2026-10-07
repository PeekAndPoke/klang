/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

// The 16-bit edge: where the engine's floating-point output (MasterStage.process) meets a consumer that
// takes 16-bit signed integers, the JVM SourceDataLine player and the WAV writer. This file is the one
// home of that conversion; the engine itself never quantises, and the browser takes the floats as they are.

/** The largest 16-bit PCM value, what an output sample of 1.0 (or above) becomes. */
const val PCM16_MAX: Int = 32767

/** The smallest 16-bit PCM value, what an output sample of -1.0 (or below, or NaN) becomes. */
const val PCM16_MIN: Int = -32768

/**
 * One output sample to a 16-bit PCM value, as an [Int] in `PCM16_MIN..PCM16_MAX`: in `(-1, 1]` it
 * scales by [PCM16_MAX] and truncates toward zero; above 1 it is [PCM16_MAX]; anything else (-1.0 and
 * below, and NaN, which fails both comparisons) is [PCM16_MIN].
 *
 * It reproduces the 16-bit values the engine wrote before its output became floating point (2026-10-07)
 * for every mix sample but one: the old rule put a mix sample of exactly -1.0 on -32767 and everything
 * below it on -32768. The float output's clip ([clipSample]) writes -1.0 for both, so no edge can tell
 * them apart any more. This one keeps the case that happens (a negative clip, which the 20:1 house
 * limiter lets through on a mix far too loud for it) bit-identical, and a mix sample of exactly -1.0
 * now lands one count lower, on [PCM16_MIN].
 *
 * Inline so the loop in [writePcm16] stays one flat body.
 */
@Suppress("NOTHING_TO_INLINE")
inline fun pcm16(sample: AudioSample): Int {
    return if (sample > -1.0 && sample <= 1.0) {
        (sample * PCM16_MAX).toInt()
    } else if (sample > 1.0) {
        PCM16_MAX
    } else {
        PCM16_MIN
    }
}

/**
 * Writes the first [frames] frames of [source] into [bytes] as interleaved stereo 16-bit little-endian
 * PCM, `[L0 low, L0 high, R0 low, R0 high, L1 low, ...]`, every sample through [pcm16]. That is the
 * layout of a WAV data chunk and of a `SourceDataLine` opened for signed 16-bit little-endian stereo.
 * [bytes] must hold `4 * frames` bytes. Allocates nothing.
 */
fun writePcm16(source: StereoBuffer, frames: Int, bytes: ByteArray) {
    val left = source.left
    val right = source.right

    for (i in 0 until frames) {
        val l = pcm16(left[i])
        val r = pcm16(right[i])
        val o = i * 4

        // toByte() keeps the low 8 bits; the arithmetic shift brings the high 8 bits down, sign included.
        bytes[o] = l.toByte()
        bytes[o + 1] = (l shr 8).toByte()
        bytes[o + 2] = r.toByte()
        bytes[o + 3] = (r shr 8).toByte()
    }
}
