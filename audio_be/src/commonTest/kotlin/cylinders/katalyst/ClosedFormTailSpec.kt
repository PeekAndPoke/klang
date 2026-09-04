/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.effects.DelayLine
import io.peekandpoke.klang.audio_be.effects.Reverb
import io.peekandpoke.klang.audio_be.master.MasterChain
import io.peekandpoke.klang.audio_bridge.MasterDsl
import io.peekandpoke.klang.audio_bridge.MasterStageDsl

/**
 * The Active-state tail question answered in closed form (`TailCountdown`) instead of by scanning
 * the ring or the combs, for both orbit effects and the master chain. The scans (`DelayLine.hasTail`,
 * `Reverb.hasTail`) stay as the ORACLE here: the closed form must never say "no tail" while the
 * scan still finds audible energy in what the tap can reach — and must eventually say it.
 */
class ClosedFormTailSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    fun ctx() = KatalystContext(
        blockFrames = blockFrames,
        mixBuffer = StereoBuffer(blockFrames),
        delaySendBuffer = StereoBuffer(blockFrames),
        reverbSendBuffer = StereoBuffer(blockFrames),
    )

    fun delayEffect(time: Double, feedback: Double) =
        KatalystDelayEffect(delayLine = DelayLine(maxDelaySeconds = 2.0, sampleRate = sampleRate), blockFrames = blockFrames)
            .apply { configure(timeSeconds = time, feedback = feedback, cap = 1.0) }

    fun KatalystDelayEffect.feed(ctx: KatalystContext, level: Double) {
        ctx.delaySendBuffer.left.fill(level)
        ctx.delaySendBuffer.right.fill(level)
        ctx.mixBuffer.clear()
        process(ctx)
    }

    // ── Delay ────────────────────────────────────────────────────────────────────────────────────

    "a running delay: tail while fed, tail through the echoes after the input stops, then provably none" {
        val fx = delayEffect(time = 0.05, feedback = 0.5)
        val ctx = ctx()
        fx.hasTail() shouldBe false // configured, nothing fed: idle

        repeat(20) { fx.feed(ctx, 0.5) }
        fx.hasTail() shouldBe true

        // Silence in: the closed form must stay true as long as the scan finds a tail...
        var blocksWithTail = 0
        var scanSaidTail = true
        while (fx.hasTail()) {
            fx.feed(ctx, 0.0)
            blocksWithTail++
            (blocksWithTail < 2000) shouldBe true
            if (fx.hasTail()) {
                scanSaidTail = fx.delayLine!!.tapWindowPeakAbs() > 0.0
            }
        }
        // ...and it said false only once the tap window is below the threshold.
        (fx.delayLine!!.tapWindowPeakAbs() <= 0.00001) shouldBe true
        // Positive control: the echoes were held — at least a few delay periods (0.05 s ≈ 17 blocks).
        blocksWithTail shouldBeGreaterThan 17
        scanSaidTail shouldBe true
    }

    "a note mid-decay restarts the proof — the orbit is not cut under a new note" {
        val fx = delayEffect(time = 0.05, feedback = 0.3)
        val ctx = ctx()
        repeat(10) { fx.feed(ctx, 0.5) }
        repeat(5) { fx.feed(ctx, 0.0) }
        fx.hasTail() shouldBe true
        fx.feed(ctx, 0.5)
        var blocks = 0
        while (fx.hasTail()) {
            fx.feed(ctx, 0.0)
            blocks++
            (blocks < 2000) shouldBe true
        }
        blocks shouldBeGreaterThan 17 // a full decay again, not the remainder of the first
    }

    "raising the feedback mid-decay re-measures — the proof is not the old, faster one" {
        val fast = delayEffect(time = 0.05, feedback = 0.1)
        val ctx = ctx()
        repeat(10) { fast.feed(ctx, 0.5) }
        fast.feed(ctx, 0.0) // proof started at fb 0.1
        fast.configure(timeSeconds = 0.05, feedback = 0.9, cap = 1.0) // the owner turns it up
        var blocks = 0
        while (fast.hasTail()) {
            fast.feed(ctx, 0.0)
            blocks++
            (blocks < 5000) shouldBe true
        }
        // At fb 0.9 the ceiling needs ~100 periods to fall 100 dB: far more than fb 0.1's ~5.
        blocks shouldBeGreaterThan 200
    }

    "self-oscillation (feedback >= 1) keeps the tail until the owner turns it off" {
        val fx = delayEffect(time = 0.05, feedback = 1.0)
        val ctx = ctx()
        repeat(10) { fx.feed(ctx, 0.5) }
        repeat(3000) { fx.feed(ctx, 0.0) } // 8.7 s of silence
        fx.hasTail() shouldBe true
        fx.configure(timeSeconds = 0.0, feedback = 0.0, cap = 1.0) // off → Draining, infinite → stays
        fx.hasTail() shouldBe true
    }

    "the closed form never scans the ring: a ring far bigger than the delay costs the delay, not the ring" {
        // No timing assertion (not a benchmark); the structural fact: the only read at the
        // silence onset is the tap window, whose size is the delay time, not the ring class.
        val huge = DelayLine(StereoBuffer(4 * sampleRate), sampleRate, delayTimeSeconds = 0.05, feedback = 0.5)
        val fx = KatalystDelayEffect(delayLine = huge, blockFrames = blockFrames)
            .apply { configure(timeSeconds = 0.05, feedback = 0.5, cap = 1.0) }
        val ctx = ctx()
        repeat(10) { fx.feed(ctx, 0.5) }
        fx.feed(ctx, 0.0)
        // The proof length is the drain math on the tap window peak — the same number a tiny ring gives.
        val small = delayEffect(time = 0.05, feedback = 0.5)
        val ctx2 = ctx()
        repeat(10) { small.feed(ctx2, 0.5) }
        small.feed(ctx2, 0.0)
        var a = 0
        while (fx.hasTail()) { fx.feed(ctx, 0.0); a++; (a < 5000) shouldBe true }
        var b = 0
        while (small.hasTail()) { small.feed(ctx2, 0.0); b++; (b < 5000) shouldBe true }
        a shouldBe b
    }

    // ── Reverb ───────────────────────────────────────────────────────────────────────────────────

    fun reverbEffect(roomSize: Double) =
        KatalystReverbEffect(reverb = Reverb(sampleRate), blockFrames = blockFrames)
            .apply { configure(roomSize = roomSize, roomFade = null, roomLp = null, roomDim = null, iResponse = null) }

    fun KatalystReverbEffect.feed(ctx: KatalystContext, level: Double) {
        ctx.reverbSendBuffer.left.fill(level)
        ctx.reverbSendBuffer.right.fill(level)
        ctx.mixBuffer.clear()
        process(ctx)
    }

    "a running reverb: tail while fed, through the decay after the input stops, then provably none" {
        val fx = reverbEffect(roomSize = 0.5)
        val ctx = ctx()
        fx.hasTail() shouldBe false
        repeat(20) { fx.feed(ctx, 0.5) }
        fx.hasTail() shouldBe true

        var blocks = 0
        while (fx.hasTail()) {
            fx.feed(ctx, 0.0)
            blocks++
            (blocks < 20000) shouldBe true
        }
        withClue("the combs must be below the threshold when the closed form says silent") {
            fx.reverb!!.hasTail() shouldBe false
        }
        blocks shouldBeGreaterThan 100 // a real decay was held (~0.7 s minimum tail ≈ 240 blocks)
    }

    // ── Master chain ─────────────────────────────────────────────────────────────────────────────

    "a master chain: no tail before input, tail while fed and through the echoes, then provably none" {
        val chain = MasterChain.build(
            MasterDsl.of(MasterStageDsl.Delay(wet = 0.5, timeSeconds = 0.05, feedback = 0.5), MasterStageDsl.Reverb(wet = 0.4, roomSize = 5.0)),
            sampleRate, blockFrames,
        )
        chain.hasActiveTail() shouldBe false
        val bus = StereoBuffer(blockFrames)
        repeat(20) {
            bus.left.fill(0.5); bus.right.fill(0.5)
            chain.process(bus, blockFrames)
        }
        chain.hasActiveTail() shouldBe true

        var blocks = 0
        while (chain.hasActiveTail()) {
            bus.clear()
            chain.process(bus, blockFrames)
            blocks++
            (blocks < 20000) shouldBe true
        }
        chain.delays[0].hasTail() shouldBe false
        chain.reverbs[0].hasTail() shouldBe false
        blocks shouldBeGreaterThan 100
    }
})
