/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import kotlin.random.Random

/**
 * Guards the DECIDED voice-count-transition semantics (block-framing ledger O3/O4, maintainer
 * 2026-08-28): transitions are observed at block start BY DESIGN, and at a mid-note change the
 * SURVIVING voices keep their phase, drift and gain-jitter — only NEW indices draw from the rng.
 *
 * The old code re-drew the gain-jitter of EVERY voice on any count change (an audible amplitude
 * step at a block boundary) and rebuilt every survivor's drift. With `analog = 0` the rng draws
 * are exactly countable: note-on = one phase + one jitter draw per voice; a 5 -> 6 transition must
 * cost exactly ONE voice's worth (1 phase + 1 jitter), not a re-init of all six.
 */
class SuperStackTransitionSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    /** Funnels every kotlin.random call through nextBits, so draws are exactly countable. */
    class CountingRandom(seed: Int) : Random() {
        private val inner = Random(seed)
        var draws = 0
        override fun nextBits(bitCount: Int): Int {
            draws++
            return inner.nextBits(bitCount)
        }
    }

    /** 5 voices before note-relative frame 256, 6 from there on (block-start observed by design). */
    val steppingVoices = object : Ignitor {
        override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
            for (i in ctx.offset until ctx.windowEnd) {
                val abs = ctx.voiceElapsedFrames + (i - ctx.offset)
                buffer[i] = if (abs < 256) 5.0 else 6.0
            }
        }
    }

    "a 5 -> 6 voice-count change draws for the NEW voice only, never a full re-jitter (ledger O4)" {
        val rng = CountingRandom(3)
        val ig = Ignitors.superSaw(
            freq = ConstantIgnitor(220.0),
            voices = steppingVoices,
            analog = ConstantIgnitor(0.0),   // no drift: every remaining draw is phase or jitter
            rng = rng,
            gainJitter = 0.9,
        )
        val ctx = IgniteContext(
            sampleRate = sampleRate, voiceDurationFrames = 4096, gateEndFrame = 4096,
            releaseFrames = 0,  scratchBuffers = ScratchBuffers(blockFrames),
        )
        val tmp = AudioBuffer(blockFrames)
        fun renderBlock(pos: Int) {
            ctx.updateOffsetAndLength(0, blockFrames)
            ctx.voiceElapsedFrames = pos
            ig.generate(tmp, 220.0, ctx)
        }

        renderBlock(0)
        val noteOnDraws = rng.draws
        renderBlock(128)
        val steadyDraws = rng.draws - noteOnDraws
        renderBlock(256)
        val transitionDraws = rng.draws - noteOnDraws - steadyDraws

        // nextDouble() is two nextBits() calls on this platform's Random; the ratios below hold
        // regardless, because all three numbers are counted the same way.
        (steadyDraws == 0) shouldBe true                        // no transition, no draws at analog 0
        (transitionDraws in 1..(noteOnDraws / 3)) shouldBe true // ~one voice's worth, not a re-init
    }
})
