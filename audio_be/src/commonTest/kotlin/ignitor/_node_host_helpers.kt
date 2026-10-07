/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.AudioBuffer

/** TEST ONLY. Plays [data] from its own cursor, one window per call: a source whose samples the test chose. */
class ArrayIgnitor(private val data: DoubleArray) : Ignitor {
    private var cursor = 0

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        for (i in ctx.offset until ctx.windowEnd) {
            buffer[i] = data[cursor]
            cursor++
        }
    }
}

/**
 * TEST ONLY. Renders [node] over consecutive windows of [lengths] frames, one `generate` per window (the Ignitor
 * host's block contract), and returns the windows concatenated. The voice clock advances by each window.
 */
fun renderNodeWindows(node: Ignitor, lengths: List<Int>, sampleRate: Int): DoubleArray {
    val total = lengths.sum()
    val widest = lengths.max()
    val ctx = IgniteContext(
        sampleRate = sampleRate,
        voiceDurationFrames = total,
        gateEndFrame = total,
        scratchBuffers = ScratchBuffers(widest),
    )
    val out = DoubleArray(total)
    val buffer = AudioBuffer(widest)
    var at = 0

    for (len in lengths) {
        ctx.voiceElapsedFrames = at
        ctx.updateOffsetAndLength(0, len)
        node.generate(buffer, 220.0, ctx)
        buffer.copyInto(out, at, 0, len)
        at += len
    }

    return out
}

/** TEST ONLY. [input] through the node [build] makes over it, rendered as ONE window. */
fun renderThroughNode(input: DoubleArray, sampleRate: Int = 48000, build: (Ignitor) -> Ignitor): DoubleArray =
    renderNodeWindows(build(ArrayIgnitor(input)), listOf(input.size), sampleRate)
