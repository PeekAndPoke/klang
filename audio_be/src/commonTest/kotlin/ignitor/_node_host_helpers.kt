/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.AudioBuffer
import kotlin.random.Random

/** This file's one seeded stream: every run draws the same, and successive builds still draw
 *  differently (as they did from the process-wide stream these calls used before). */
private val testRandom = Random(0x5EED)

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
        random = testRandom,
    )
    val out = DoubleArray(total)
    val buffer = AudioBuffer(widest)
    var at = 0

    for (len in lengths) {
        ctx.voiceElapsedFrames = at
        ctx.updateOffsetAndLength(offset = 0, length = len)
        node.generate(buffer, 220.0, ctx)
        buffer.copyInto(destination = out, destinationOffset = at, startIndex = 0, endIndex = len)
        at += len
    }

    return out
}

/** TEST ONLY. [input] through the node [build] makes over it, rendered as ONE window. */
fun renderThroughNode(input: DoubleArray, sampleRate: Int = 48000, build: (Ignitor) -> Ignitor): DoubleArray =
    renderNodeWindows(build(ArrayIgnitor(input)), listOf(input.size), sampleRate)
