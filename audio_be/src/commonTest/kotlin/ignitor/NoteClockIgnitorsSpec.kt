/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.ign
import io.peekandpoke.klang.audio_bridge.mul
import io.peekandpoke.klang.audio_bridge.oversampleBy
import kotlin.random.Random

/**
 * **The note clocks, `progress()` and `elapsed()`** (KLANG-00245 (pitch pipeline into the tree) step 7f, decided
 * 2026-10-13, Q13), driven as the engine drives them: `voiceDurationFrames` is the gate length, `voiceElapsedFrames`
 * advances one window at a time from the onset and keeps counting through the release.
 *
 * The oracle is written out per frame (`frame / gate`, `frame / sampleRate`), and every row also renders the same
 * frames cut into other windows (a mid-block onset, odd window lengths): each sample comes from its frame alone, so
 * the framings agree bit for bit.
 */
class NoteClockIgnitorsSpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128

    /**
     * Renders [node] for [frames] frames from the onset, the onset [onsetOffset] frames into its first block, every
     * later window [windowFrames] long (at most a block).
     */
    fun render(
        node: Ignitor,
        gate: Int,
        frames: Int,
        gateKnown: Boolean = true,
        onsetOffset: Int = 0,
        windowFrames: Int = blockFrames,
    ): DoubleArray {
        val ctx = IgniteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = gate,
            gateEndFrame = gate,
            scratchBuffers = ScratchBuffers(blockFrames),
            random = Random(1),
            gateKnown = gateKnown,
        )
        val out = DoubleArray(frames)
        val block = AudioBuffer(blockFrames)
        var pos = 0

        while (pos < frames) {
            val offset = if (pos == 0) onsetOffset else 0
            val length = minOf(if (pos == 0) blockFrames - onsetOffset else windowFrames, frames - pos)
            ctx.updateOffsetAndLength(offset = offset, length = length)
            ctx.voiceElapsedFrames = pos
            node.generate(block, 220.0, ctx)
            block.copyInto(destination = out, destinationOffset = pos, startIndex = offset, endIndex = offset + length)
            pos += length
        }

        return out
    }

    /** The same frames cut three ways: whole blocks, a mid-block onset, odd short windows. */
    fun framings(node: Ignitor, gate: Int, frames: Int, gateKnown: Boolean = true): List<DoubleArray> = listOf(
        render(node = node, gate = gate, frames = frames, gateKnown = gateKnown),
        render(node = node, gate = gate, frames = frames, gateKnown = gateKnown, onsetOffset = 37),
        render(node = node, gate = gate, frames = frames, gateKnown = gateKnown, windowFrames = 19),
    )

    fun assertFrames(rendered: List<DoubleArray>, law: (Int) -> Double) {
        for ((framing, out) in rendered.withIndex()) {
            for (frame in out.indices) {
                withClue("framing $framing, frame $frame") {
                    out[frame] shouldBe law(frame)
                }
            }
        }
    }

    "progress: frame / gate, exactly 1.0 at the gate frame, on past 1 at the same pace through the release" {
        val gate = 1000
        val out = framings(node = ProgressIgnitor, gate = gate, frames = 3 * gate)

        assertFrames(out) { frame -> frame.toDouble() / gate.toDouble() }

        for (o in out) {
            o[0] shouldBe 0.0
            o[gate / 2] shouldBe 0.5
            o[gate] shouldBe 1.0
            o[2 * gate] shouldBe 2.0
        }
    }

    "progress: a gate of zero frames or less reads 1.0 from the first frame" {
        for (gate in listOf(0, -64)) {
            withClue("gate $gate") {
                assertFrames(framings(node = ProgressIgnitor, gate = gate, frames = 500)) { 1.0 }
            }
        }
    }

    "progress: a voice whose gate is not known reads 0 throughout" {
        assertFrames(framings(node = ProgressIgnitor, gate = 1000, frames = 3000, gateKnown = false)) { 0.0 }
    }

    "elapsed: frame / sampleRate, the gate and its flag play no part" {
        for (gateKnown in listOf(true, false)) {
            withClue("gateKnown $gateKnown") {
                val out = framings(node = ElapsedIgnitor, gate = 1000, frames = 2 * sampleRate + 77, gateKnown = gateKnown)

                assertFrames(out) { frame -> frame.toDouble() / sampleRate.toDouble() }

                for (o in out) {
                    o[sampleRate] shouldBe 1.0
                    o[2 * sampleRate] shouldBe 2.0
                }
            }
        }
    }

    "the build turns the two leaves into the two clocks" {
        val built = listOf(IgnitorDsl.Progress, IgnitorDsl.Elapsed).map { dsl ->
            render(node = dsl.buildExciter(random = Random(1), sampleRate = sampleRate).ignitor, gate = 1000, frames = 300)
        }

        assertFrames(listOf(built[0])) { frame -> frame.toDouble() / 1000.0 }
        assertFrames(listOf(built[1])) { frame -> frame.toDouble() / sampleRate.toDouble() }
    }

    "inside an oversampling region the clocks keep the voice's gate flag" {
        // The region renders its body in a context of its own at the higher rate; the progress leaf in the body must
        // still see that the voice's gate is not known. A constant 1 times the progress, oversampled by 2: silence
        // exactly when the progress reads 0, a rising level when it moves.
        val dsl = IgnitorDsl.Constant(1.0).oversampleBy(2.ign) { it.mul(IgnitorDsl.Progress) }

        fun renderRegion(gateKnown: Boolean): DoubleArray = render(
            node = dsl.buildExciter(random = Random(1), sampleRate = sampleRate).ignitor,
            gate = 1000,
            frames = 2000,
            gateKnown = gateKnown,
        )

        renderRegion(gateKnown = false).all { it == 0.0 } shouldBe true
        renderRegion(gateKnown = true).last() shouldBeGreaterThan 1.5
    }
})
