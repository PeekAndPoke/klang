/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import kotlin.math.pow
import kotlin.random.Random

/**
 * **The accelerate node's law: semitones over the GATE, then held** (decision D2 of the pitch pipeline, with its hold,
 * maintainer 2026-10-09; pitch pipeline step 3 moved this spec from the retired strip renderer to the node).
 *
 * The node is driven as the engine drives it: `voiceDurationFrames` is the gate length, `voiceElapsedFrames` advances
 * one block at a time and keeps counting through the release tail. Up to the gate, every frame equals the law as the
 * node computed it before the hold, written out here: one `pow` seeds each block's first frame,
 * `2^(octaves * (elapsed / gate))`, then one multiply by `2^(octaves / gate)` per frame, so the frames before the gate
 * keep their bits exactly. From the gate frame on, through a release tail seven gates long, every frame is the target
 * `2^(semitones / 12)`. Before the hold the node kept rising there (24 semitones over a 1280-frame gate read 4.0 at
 * the gate, 16.0 at twice the gate).
 *
 * P2 guard (pitch-param unification, 2026-08-24): the unit is SEMITONES; without the `/ 12` the halfway point reads
 * `2^12` instead of 2.
 */
class AccelerateSemitoneLawSpec : StringSpec({

    val blockFrames = 128

    /**
     * Renders [semitones] of accelerate over a gate of [gate] frames for [frames] frames, in blocks, from the onset. The
     * onset sits [onsetOffset] frames into its first block, as the engine renders a mid-block onset: the first window
     * starts at that buffer index and is shorter, every later one starts at 0.
     */
    fun render(semitones: Double, gate: Int, frames: Int, onsetOffset: Int = 0): DoubleArray {
        val ctx = IgniteContext(
            sampleRate = 48000,
            voiceDurationFrames = gate,
            gateEndFrame = gate,
            scratchBuffers = ScratchBuffers(blockFrames),
            random = Random(1),
        )
        val node = accelerateModIgnitor(semitones = semitones)
        val out = DoubleArray(frames)
        val block = AudioBuffer(blockFrames)
        var pos = 0

        while (pos < frames) {
            val offset = if (pos == 0) onsetOffset else 0
            val length = minOf(blockFrames - offset, frames - pos)
            ctx.updateOffsetAndLength(offset = offset, length = length)
            ctx.voiceElapsedFrames = pos
            node.generate(block, 220.0, ctx)
            block.copyInto(destination = out, destinationOffset = pos, startIndex = offset, endIndex = offset + length)
            pos += length
        }

        return out
    }

    /** The glide as the node computed it before the hold, for the frames before the gate: the per-block seed, then the per-frame step. */
    fun lawBeforeTheGate(semitones: Double, gate: Int, onsetOffset: Int = 0): DoubleArray {
        val octaves = semitones / 12.0
        val step = 2.0.pow(octaves / gate.toDouble())
        val out = DoubleArray(gate)
        var blockStart = 0

        while (blockStart < gate) {
            var ratio = 2.0.pow(octaves * (blockStart.toDouble() / gate.toDouble()))
            val length = if (blockStart == 0) blockFrames - onsetOffset else blockFrames

            for (i in blockStart until minOf(blockStart + length, gate)) {
                out[i] = ratio
                ratio *= step
            }

            blockStart += length
        }

        return out
    }

    // Two gates: one on a block boundary (10 blocks), one inside a block (frame 1000 is frame 104 of the eighth). Two
    // amounts: 24 semitones is two octaves, where every product with the octaves is exact, so only 7 semitones shows
    // the rounding order of the seed (`octaves * (elapsed / gate)`, not `octaves * elapsed / gate`).
    for (semitones in listOf(24.0, 7.0)) {
        for (gate in listOf(1280, 1000)) {
            "accelerate($semitones st) over a gate of $gate frames: the law up to the gate, bit for bit, then the target held through a long tail" {
                val frames = 8 * gate
                val out = render(semitones = semitones, gate = gate, frames = frames)
                val law = lawBeforeTheGate(semitones = semitones, gate = gate)
                val target = 2.0.pow(semitones / 12.0)

                withClue("the frames before the gate keep the law's bits") {
                    (0 until gate).firstOrNull { out[it] != law[it] } shouldBe null
                }
                withClue("the semitone law: half the glide at half the gate") {
                    out[gate / 2] shouldBe (2.0.pow(semitones / 24.0) plusOrMinus 1e-12)
                }
                withClue("from the gate frame on, through seven gates of tail, the target 2^(semitones / 12)") {
                    (gate until frames).firstOrNull { out[it] != target } shouldBe null
                }
            }
        }
    }

    "a gate inside the first window of a mid-block onset: the law up to the gate, then the target" {
        // The onset 37 frames into its block and a 50-frame gate: the gate falls inside the voice's first window, which
        // starts at buffer index 37, so the split must count from the window's start, not from index 0.
        val gate = 50
        val frames = 400
        val out = render(semitones = 7.0, gate = gate, frames = frames, onsetOffset = 37)
        val law = lawBeforeTheGate(semitones = 7.0, gate = gate, onsetOffset = 37)

        withClue("the frames before the gate keep the law's bits") {
            (0 until gate).firstOrNull { out[it] != law[it] } shouldBe null
        }
        withClue("from the gate frame on, the target") {
            (gate until frames).firstOrNull { out[it] != 2.0.pow(7.0 / 12.0) } shouldBe null
        }
    }

    "a gate of zero frames has arrived at once: the target from the first frame (Q27)" {
        // `legato(0)` makes the gate 0. D2 says the glide arrives at the gate close and holds, so at gate 0 it has
        // arrived: the voice plays the target from its onset (decided by default, maintainer questions Q27).
        for (onsetOffset in listOf(0, 37)) {
            withClue("onset offset $onsetOffset") {
                val out = render(semitones = 12.0, gate = 0, frames = 600, onsetOffset = onsetOffset)

                out.toList().distinct() shouldBe listOf(2.0)
            }
        }
    }

    "a downward glide holds its target too" {
        val gate = 1000
        val out = render(semitones = -12.0, gate = gate, frames = 3 * gate)

        out[gate - 1] shouldBe (0.5 plusOrMinus 1e-3)
        (gate until 3 * gate).firstOrNull { out[it] != 0.5 } shouldBe null
    }
})
