/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import kotlin.random.Random

/**
 * One voice's worth of a tree, rendered through `buildExciter` over a voice's windows: a mid-block onset
 * (offset 37), full blocks, the gate ending at [gateFrames] and the release running on after it. The
 * shared harness of the phase 3 step 5 specs (`FilterSlotLayerFillSpec`, `FilterPassesKnobSpec`,
 * `ClassicTailRenderSpec`), so their rows compare like with like.
 */
internal fun renderVoiceWindows(
    dsl: IgnitorDsl,
    bag: Map<String, Double>? = null,
    sampleRate: Int = 48000,
    blockFrames: Int = 128,
    blocks: Int = 60,
    gateFrames: Int = 5000,
    freqHz: Double = 220.0,
): DoubleArray {
    val ignitor = dsl.buildExciter(oscParams = bag, random = Random(7), freqHz = freqHz, sampleRate = sampleRate).ignitor
    val ctx = IgniteContext(
        sampleRate = sampleRate,
        voiceDurationFrames = gateFrames,
        gateEndFrame = gateFrames,
        releaseFrames = 0,
        scratchBuffers = ScratchBuffers(blockFrames),
        random = Random(7),
    )
    val buffer = AudioBuffer(blockFrames)
    val out = ArrayList<Double>()

    for (block in 0 until blocks) {
        val offset = if (block == 0) 37 else 0
        val length = blockFrames - offset

        ctx.updateOffsetAndLength(offset, length)
        ignitor.generate(buffer, freqHz, ctx)

        for (i in offset until offset + length) {
            out.add(buffer[i])
        }

        ctx.voiceElapsedFrames += length
    }

    return out.toDoubleArray()
}

/** The first frame whose RAW BITS differ, or -1. Raw bits: these specs claim identity, not closeness. */
internal fun firstBitMismatch(a: DoubleArray, b: DoubleArray): Int {
    if (a.size != b.size) {
        return 0
    }

    return a.indices.firstOrNull { a[it].toRawBits() != b[it].toRawBits() } ?: -1
}
