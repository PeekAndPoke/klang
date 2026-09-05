/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import kotlin.math.abs
import kotlin.random.Random

/**
 * Guards ledger O1: `superpluck` must read its `voices` param via `readParam`, never by rendering
 * into scratch and indexing. The raw read returned stale cross-voice pool residue on a zero-length
 * terminal window (`generate` writes nothing there), and that value sized
 * `Array(newV) { StringState(AudioBuffer(2500)) }` — a control-rate residue meant an unbounded
 * synchronous allocation on the render thread.
 */
class SuperPluckParamReadSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    fun ctx() = IgniteContext(
        sampleRate = sampleRate, voiceDurationFrames = 4096, gateEndFrame = 4096,
        releaseFrames = 0,  scratchBuffers = ScratchBuffers(blockFrames),
    )

    fun render(ig: Ignitor, c: IgniteContext, pos: Int, len: Int): DoubleArray {
        val tmp = AudioBuffer(blockFrames)
        c.updateOffsetAndLength(0, len)
        c.voiceElapsedFrames = pos
        ig.generate(tmp, 110.0, c)
        return tmp.copyOf(len)
    }

    "a poisoned pool + zero-length window cannot perturb the voice (ledger O1)" {
        // superpluck PLUS whitenoise, sharing one voice rng: the pluck's raw voices read used to
        // turn stale pool residue into a phantom 42-string transition on a zero-length window.
        // That transition is output-invisible on the pluck itself (it reverses cleanly), but its
        // thousands of rng draws SHIFT the shared stream, so the noise that renders next comes out
        // different — which is exactly how the defect would corrupt a real voice.
        val dsl = IgnitorDsl.Plus(IgnitorDsl.SuperPluck(), IgnitorDsl.WhiteNoise())

        // Reference: two blocks, no zero-length call in between.
        val refCtx = ctx()
        val refIg = dsl.toExciter(random = Random(7))
        render(refIg, refCtx, 0, blockFrames)
        val refBlock1 = render(refIg, refCtx, blockFrames, blockFrames)

        // Same render, with a poisoned scratch pool and a ZERO-LENGTH call between the blocks —
        // what a terminal window used to hand the raw voices read. 50.0 as the residue.
        val c = ctx()
        val ig = dsl.toExciter(random = Random(7))
        render(ig, c, 0, blockFrames)
        // Poison a few slots deep: builder wrappers hold outer slots while the pluck reads, so
        // the raw read lands in a deeper slot, not slot 0.
        c.scratchBuffers.use { s0 ->
            s0.fill(50.0)
            c.scratchBuffers.use { s1 ->
                s1.fill(50.0)
                c.scratchBuffers.use { s2 -> s2.fill(50.0) }
            }
        }
        render(ig, c, blockFrames, 0)
        val block1 = render(ig, c, blockFrames, blockFrames)

        var m = 0.0
        for (i in 0 until blockFrames) m = maxOf(m, abs(block1[i] - refBlock1[i]))
        m shouldBe 0.0
    }
})
