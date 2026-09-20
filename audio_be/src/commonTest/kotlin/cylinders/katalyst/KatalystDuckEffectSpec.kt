/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.effects.Ducking
import io.peekandpoke.klang.audio_be.voices.Voice
import kotlin.math.abs

/**
 * The contract of [KatalystDuckEffect] (Katalyst 5c-9, `docs/plans/knob-glide.md` and
 * `docs/tasks/katalyst-dsl.md` step 5c).
 *
 * Every oracle is written out from the decided law: the switch rides a WEIGHT from where it stands
 * to its target over `KNOB_GLIDE_SECONDS` rounded to whole blocks (17 at 44.1 kHz and 128 frames),
 * `gain = 1 + w * (g - 1)`, and the gain reduction `g` comes from a BARE [Ducking] driven in
 * parallel, never from the stage under test.
 */
class KatalystDuckEffectSpec : StringSpec({

    val sampleRate = 44100
    val frames = 128
    val glideBlocks = 17

    fun stage(): KatalystDuckEffect = KatalystDuckEffect(sampleRate = sampleRate, blockFrames = frames)

    fun ctx(): KatalystContext = KatalystContext(blockFrames = frames, mixBuffer = StereoBuffer(frames))

    fun settings(orbit: Int, depth: Double, attack: Double = 0.1): Voice.Ducking =
        Voice.Ducking(cylinderId = orbit, attackSeconds = attack, depth = depth)

    /** A steady mix to duck, and a steady loud trigger: the worst case, a reduction in force. */
    fun mix(blocks: Int): DoubleArray = DoubleArray(blocks * frames) { 0.5 }

    fun trigger(blocks: Int, level: Double = 0.9): DoubleArray = DoubleArray(blocks * frames) { level }

    /**
     * Drives the stage the way the host does, in the host's order: the writer's `configure`, then
     * the orbit's block (`KatalystChain.process`), then the duck pass.
     */
    fun run(
        src: DoubleArray,
        side: DoubleArray,
        resetAt: Int = -1,
        fx: KatalystDuckEffect = stage(),
        settingsAt: (Int) -> Voice.Ducking?,
    ): DoubleArray {
        val context = ctx()
        val sideBuf = StereoBuffer(frames)
        val out = DoubleArray(src.size)
        val blocks = src.size / frames

        for (k in 0 until blocks) {
            if (k == resetAt) {
                fx.reset()
            }

            fx.configure(settingsAt(k))
            fx.orbitBlockRan()

            for (i in 0 until frames) {
                context.mixBuffer.left[i] = src[k * frames + i]
                context.mixBuffer.right[i] = src[k * frames + i]
                sideBuf.left[i] = side[k * frames + i]
                sideBuf.right[i] = side[k * frames + i]
            }

            context.sidechainBuffer = sideBuf
            fx.process(context)
            context.sidechainBuffer = null

            for (i in 0 until frames) {
                out[k * frames + i] = context.mixBuffer.left[i]
            }
        }

        return out
    }

    /**
     * The gain reduction a BARE [Ducking] produces for [side], sample by sample: the oracle's `g`.
     * Feeding a signal of 1.0 makes the output the gain itself.
     */
    fun reduction(side: DoubleArray, depth: Double, attack: Double = 0.1, from: Int = 0): DoubleArray {
        val d = Ducking(sampleRate = sampleRate, attackSeconds = attack, depth = depth)
        val out = DoubleArray(side.size) { 1.0 }
        val l = AudioBuffer(1)
        val r = AudioBuffer(1)
        val sl = AudioBuffer(1)
        val sr = AudioBuffer(1)

        for (i in from until side.size) {
            l[0] = 1.0
            r[0] = 1.0
            sl[0] = side[i]
            sr[0] = side[i]
            d.processStereo(l, r, sl, sr, 1)
            out[i] = l[0]
        }

        return out
    }

    /**
     * The decided weight law: the value at the END of each of [blocks] blocks of one glide,
     * interpolated from the start (never accumulated) and landing on [to] exactly.
     *
     * The reciprocal, not a division: 1/17 is not a binary fraction, so `x * (1/17)` and `x / 17`
     * differ by a rounding, and these rows compare the AUDIO bit for bit.
     */
    fun line(from: Double, to: Double, blocks: Int): DoubleArray {
        val inv = 1.0 / blocks

        return DoubleArray(blocks) { k ->
            if (k == blocks - 1) to else from + (to - from) * ((k + 1) * inv)
        }
    }

    /**
     * The per-sample value inside a block that ends on [end] and started the block at [begin],
     * written from the END like the stage's own ramp.
     *
     * `/ frames` and not `* (1 / frames)` on purpose, and the two agree here: 128 is a power of
     * two, so both scalings are exact. Contrast [line], whose 1/17 is not a binary fraction.
     */
    fun rampedAt(begin: Double, end: Double, i: Int): Double = end - ((end - begin) / frames) * (frames - 1 - i)

    "settled: a duck whose knobs stand runs exactly what the bare Ducking does" {
        val blocks = 20
        val src = mix(blocks)
        val side = trigger(blocks)

        val got = run(src, side) { settings(orbit = 2, depth = 0.8) }

        val bare = Ducking(sampleRate = sampleRate, attackSeconds = 0.1, depth = 0.8)
        val want = DoubleArray(src.size)
        val l = AudioBuffer(frames)
        val r = AudioBuffer(frames)
        val sl = AudioBuffer(frames)
        val sr = AudioBuffer(frames)

        for (k in 0 until blocks) {
            for (i in 0 until frames) {
                l[i] = src[k * frames + i]
                r[i] = src[k * frames + i]
                sl[i] = side[k * frames + i]
                sr[i] = side[k * frames + i]
            }

            bare.processStereo(l, r, sl, sr, frames)

            for (i in 0 until frames) {
                want[k * frames + i] = l[i]
            }
        }

        for (i in src.indices) {
            withClue("sample $i") {
                got[i].toRawBits() shouldBe want[i].toRawBits()
            }
        }
    }

    "switching off rides the weight to 0 dB and lets go of the envelope only when it lands" {
        val blocks = 6 + glideBlocks + 4
        val src = mix(blocks)
        val side = trigger(blocks)
        val change = 6
        val fx = stage()

        val got = run(src, side, fx = fx) { k -> if (k < change) settings(orbit = 2, depth = 0.8) else null }

        val g = reduction(side, depth = 0.8)
        val w = line(1.0, 0.0, glideBlocks)

        for (b in 0 until glideBlocks) {
            val k = change + b
            val begin = if (b == 0) 1.0 else w[b - 1]

            for (i in 0 until frames) {
                val at = k * frames + i
                val want = src[at] * (1.0 + rampedAt(begin, w[b], i) * (g[at] - 1.0))

                // Bit for bit: the law is one expression, and the ramp is written from the block's
                // END, so a start-based ramp that lands a rounding off goes red here.
                withClue("fade block $b sample $i") {
                    got[at].toRawBits() shouldBe want.toRawBits()
                }
            }
        }

        // The landing block ends at exactly 0 dB of reduction ...
        val landing = change + glideBlocks - 1

        withClue("the landing block's last sample is the mix, bit for bit") {
            got[landing * frames + frames - 1].toRawBits() shouldBe src[landing * frames + frames - 1].toRawBits()
        }

        // ... and only THERE does the stage let go.
        fx.ducking.shouldBeNull()
        fx.weight shouldBe 0.0

        for (k in (landing + 1) until blocks) {
            for (i in 0 until frames) {
                val at = k * frames + i

                withClue("block $k sample $i passes through") {
                    got[at].toRawBits() shouldBe src[at].toRawBits()
                }
            }
        }
    }

    "a duck switched off still ducks, by less every block, while the fade runs" {
        val blocks = 6 + glideBlocks
        val src = mix(blocks)
        val side = trigger(blocks)
        val change = 6
        val fx = stage()

        val got = run(src, side, fx = fx) { k -> if (k < change) settings(orbit = 2, depth = 0.8) else null }

        var previous = 0.0

        for (b in 0 until glideBlocks) {
            val at = (change + b) * frames + frames - 1
            val reduction = abs(src[at] - got[at])

            if (b == 0) {
                withClue("the fade starts from a real reduction") {
                    reduction shouldBeGreaterThan 0.1
                }
            } else {
                withClue("the reduction shrinks at fade block $b") {
                    (reduction < previous) shouldBe true
                }
            }

            previous = reduction
        }
    }

    "the first initialisation is instant, and a later switch-on fades in" {
        val blocks = 6
        val src = mix(blocks)
        val side = trigger(blocks)

        // Fresh: the very first setting is in force on the very first block.
        val fresh = stage()

        fresh.configure(settings(orbit = 2, depth = 0.8))
        fresh.weight shouldBe 1.0

        // A stage that has seen the orbit render, but never ducked, fades its first life IN.
        val later = stage()

        repeat(4) {
            later.configure(null)
            later.orbitBlockRan()
        }

        later.configure(settings(orbit = 2, depth = 0.8))

        withClue("a duck that engages at bar five arrives over the glide, not in one sample") {
            later.weight shouldBe 0.0
        }

        val got = run(src, side) { k -> if (k < 2) null else settings(orbit = 2, depth = 0.8) }
        val g = reduction(side, depth = 0.8, from = 2 * frames)
        val w = line(0.0, 1.0, glideBlocks)

        for (b in 0 until (blocks - 2)) {
            val k = 2 + b
            val begin = if (b == 0) 0.0 else w[b - 1]

            for (i in 0 until frames) {
                val at = k * frames + i
                val want = src[at] * (1.0 + rampedAt(begin, w[b], i) * (g[at] - 1.0))

                withClue("fade-in block $b sample $i") {
                    got[at].toRawBits() shouldBe want.toRawBits()
                }
            }
        }
    }

    "a return mid-fade turns the weight around from where it stands" {
        val fx = stage()
        val context = ctx()
        val side = StereoBuffer(frames)

        side.left.fill(0.9)
        side.right.fill(0.9)

        fun block(s: Voice.Ducking?) {
            fx.configure(s)
            fx.orbitBlockRan()
            context.mixBuffer.fill(0.5)
            context.sidechainBuffer = side
            fx.process(context)
            context.sidechainBuffer = null
        }

        block(settings(orbit = 2, depth = 0.8))
        fx.weight shouldBe 1.0

        repeat(4) { block(null) }

        val turned = fx.weight

        withClue("four blocks of the fade have run") {
            abs(turned - (1.0 - 4.0 / glideBlocks)) shouldBeLessThanOrEqual 1e-12
        }

        // Back on: the weight rises from where it stands, over a full glide.
        block(settings(orbit = 2, depth = 0.8))

        withClue("the fade turns around, it does not restart from 0 or jump to 1") {
            abs(fx.weight - (turned + (1.0 - turned) / glideBlocks)) shouldBeLessThanOrEqual 1e-12
        }
    }

    "depth glides per sample on its own axis and lands exactly" {
        val blocks = 4 + glideBlocks + 2
        val src = mix(blocks)
        val side = trigger(blocks)
        val change = 4

        val got = run(src, side) { k ->
            if (k < change) settings(orbit = 2, depth = 0.2) else settings(orbit = 2, depth = 1.0)
        }

        // The oracle: a bare Ducking driven sample by sample with the hand-written depth line.
        val bare = Ducking(sampleRate = sampleRate, attackSeconds = 0.1, depth = 0.2)
        val want = DoubleArray(src.size)
        val l = AudioBuffer(1)
        val r = AudioBuffer(1)
        val sl = AudioBuffer(1)
        val sr = AudioBuffer(1)
        val d = line(0.2, 1.0, glideBlocks)

        for (k in 0 until blocks) {
            for (i in 0 until frames) {
                val at = k * frames + i
                val b = k - change

                bare.depth = when {
                    b < 0 -> 0.2
                    b >= glideBlocks -> 1.0
                    else -> rampedAt(if (b == 0) 0.2 else d[b - 1], d[b], i)
                }

                l[0] = src[at]
                r[0] = src[at]
                sl[0] = side[at]
                sr[0] = side[at]
                bare.processStereo(l, r, sl, sr, 1)
                want[at] = l[0]
            }
        }

        for (i in src.indices) {
            withClue("sample $i") {
                abs(got[i] - want[i]) shouldBeLessThanOrEqual 1e-12
            }
        }
    }

    "attack does not glide: a new time constant is in force on the block it arrives" {
        val fx = stage()

        fx.configure(settings(orbit = 2, depth = 0.8, attack = 0.5))
        fx.ducking.shouldNotBeNull().attackSeconds shouldBe 0.5

        fx.configure(settings(orbit = 2, depth = 0.8, attack = 0.02))
        fx.ducking.shouldNotBeNull().attackSeconds shouldBe 0.02
    }

    "a changed sidechain orbit keeps the envelope, and the reduction carries on" {
        val blocks = 12
        val src = mix(blocks)
        val side = trigger(blocks)
        val change = 6
        val fx = stage()

        val moved = run(src, side, fx = fx) { k ->
            if (k < change) settings(orbit = 2, depth = 0.8) else settings(orbit = 5, depth = 0.8)
        }
        val held = run(src, side) { settings(orbit = 2, depth = 0.8) }

        fx.duckCylinderId shouldBe 5

        for (i in src.indices) {
            withClue("sample $i") {
                moved[i].toRawBits() shouldBe held[i].toRawBits()
            }
        }
    }

    "a handover keeps the instance, and the stage that gave it away builds no second one" {
        val from = stage()
        val to = stage()

        from.configure(settings(orbit = 2, depth = 0.8))

        val instance = from.ducking.shouldNotBeNull()

        to.takeOver(from)

        to.ducking.shouldNotBeNull() shouldBeSameInstanceAs instance
        to.duckCylinderId shouldBe 2
        from.ducking.shouldBeNull()
        from.handedOver shouldBe true

        from.configure(settings(orbit = 2, depth = 0.8))
        from.ducking.shouldBeNull()
    }

    "a handover mid-fade carries the WEIGHT, so the arriving stage turns the fade around" {
        val from = stage()
        val to = stage()
        val context = ctx()
        val side = StereoBuffer(frames)

        side.left.fill(0.9)
        side.right.fill(0.9)

        fun block(fx: KatalystDuckEffect, s: Voice.Ducking?) {
            fx.configure(s)
            fx.orbitBlockRan()
            context.mixBuffer.fill(0.5)
            context.sidechainBuffer = side
            fx.process(context)
            context.sidechainBuffer = null
        }

        // A live duck, then four blocks of a fade-out: the weight stands part way down.
        block(from, settings(orbit = 2, depth = 0.95))
        repeat(4) { block(from, null) }

        val carried = from.weight

        withClue("the leaving stage really is mid-fade") {
            abs(carried - (1.0 - 4.0 / glideBlocks)) shouldBeLessThanOrEqual 1e-12
        }

        // The swap hands the live envelope over BEFORE the arriving chain's writers run.
        to.takeOver(from)

        withClue("the weight moves with the envelope") {
            to.weight shouldBe carried
        }

        // The arriving chain's writer then asks for the duck. A retarget moves no weight by
        // itself, so the stage still stands where the leaving one did: no snap to full reduction.
        to.configure(settings(orbit = 2, depth = 0.95))

        withClue("configure does not install the arriving stage at full weight") {
            to.weight shouldBe carried
        }

        // And the first block it renders turns the fade around, one step towards 1.
        block(to, settings(orbit = 2, depth = 0.95))

        withClue("the arriving stage turns the fade around from where the weight stands") {
            abs(to.weight - (carried + (1.0 - carried) / glideBlocks)) shouldBeLessThanOrEqual 1e-12
        }
    }

    "a handover carries the DEPTH, so the arriving chain glides to its own instead of snapping" {
        val from = stage()
        val context = ctx()
        val side = StereoBuffer(frames)

        // Saturated: the target gain is 1 - depth, so 0.2 against 0.9 is 18 dB of reduction apart.
        side.left.fill(0.9)
        side.right.fill(0.9)

        fun block(fx: KatalystDuckEffect, s: Voice.Ducking?) {
            fx.configure(s)
            fx.orbitBlockRan()
            context.mixBuffer.fill(0.5)
            context.sidechainBuffer = side
            fx.process(context)
            context.sidechainBuffer = null
        }

        repeat(4) { block(from, settings(orbit = 2, depth = 0.2)) }

        from.depth shouldBe 0.2

        // The swap, in the host's order: `Cylinder.beginFade` RESETS the arriving chain, then hands
        // the live envelope over, then runs its writers. The reset is what would otherwise re-arm
        // the arriving stage's snap and make its own depth land in one sample.
        val to = stage()

        to.reset()
        to.takeOver(from)

        withClue("the depth in force moves with the envelope") {
            to.depth shouldBe 0.2
        }

        to.configure(settings(orbit = 2, depth = 0.9))

        withClue("a retarget moves nothing by itself") {
            to.depth shouldBe 0.2
        }

        to.orbitBlockRan()
        context.mixBuffer.fill(0.5)
        context.sidechainBuffer = side
        to.process(context)
        context.sidechainBuffer = null

        withClue("the arriving chain's depth glides from the one it inherited") {
            abs(to.depth - (0.2 + (0.9 - 0.2) / glideBlocks)) shouldBeLessThanOrEqual 1e-12
        }
    }

    "a second life snaps its depth and fades its weight in" {
        val fx = stage()
        val context = ctx()
        val side = StereoBuffer(frames)

        side.left.fill(0.9)
        side.right.fill(0.9)

        fun block(s: Voice.Ducking?) {
            fx.configure(s)
            fx.orbitBlockRan()
            context.mixBuffer.fill(0.5)
            context.sidechainBuffer = side
            fx.process(context)
            context.sidechainBuffer = null
        }

        block(settings(orbit = 2, depth = 0.9))
        repeat(glideBlocks + 1) { block(null) }

        fx.ducking.shouldBeNull()

        // The next life: depth at once, weight from zero.
        block(settings(orbit = 3, depth = 0.4))

        fx.depth shouldBe 0.4

        withClue("the weight of the new life's first block is one step in, not 1") {
            abs(fx.weight - 1.0 / glideBlocks) shouldBeLessThanOrEqual 1e-12
        }
    }

    "a duck whose sidechain orbit disappears lets go at once: there is nothing left to ride down" {
        val fx = stage()
        val context = ctx()
        val side = StereoBuffer(frames)

        side.left.fill(0.9)
        side.right.fill(0.9)

        // Two ducked blocks, so a reduction is really in force.
        repeat(2) {
            fx.configure(settings(orbit = 2, depth = 0.9))
            fx.orbitBlockRan()
            context.mixBuffer.fill(0.5)
            context.sidechainBuffer = side
            fx.process(context)
            context.sidechainBuffer = null
        }

        // The sidechain orbit is gone: `Cylinders` runs no duck pass at all, so the orbit's mix
        // goes out unducked.
        fx.configure(settings(orbit = 2, depth = 0.9))
        fx.orbitBlockRan()

        // And now the owner switches the stage off. A fade would hold the envelope and the source
        // orbit for good, because only the pass advances it.
        fx.configure(null)

        fx.ducking.shouldBeNull()
        fx.duckCylinderId.shouldBeNull()

        // And the WEIGHT has to land on 0 with it. The short-circuit is reached with the weight
        // normally standing at 1.0, and a life that ends there leaves the next switch-on nothing
        // to ride up: `retarget(1.0)` on an unchanged target is free.
        fx.weight shouldBe 0.0
    }

    "a life that ended without a pass still fades the next one in" {
        val fx = stage()
        val context = ctx()
        val side = StereoBuffer(frames)

        side.left.fill(0.9)
        side.right.fill(0.9)

        // One ducked block, so the snap window is spent and a life really started.
        fx.configure(settings(orbit = 2, depth = 0.9))
        fx.orbitBlockRan()
        context.mixBuffer.fill(0.5)
        context.sidechainBuffer = side
        fx.process(context)
        context.sidechainBuffer = null

        fx.weight shouldBe 1.0

        // The sidechain orbit disappears: no pass. Then the owner drops the duck, and the life
        // ends through the short-circuit rather than through a landed fade.
        fx.configure(settings(orbit = 2, depth = 0.9))
        fx.orbitBlockRan()
        fx.configure(null)

        fx.ducking.shouldBeNull()

        // The next life must RIDE IN, exactly as any later switch-on does.
        fx.configure(settings(orbit = 2, depth = 0.9))

        withClue("the new life's weight starts from 0, it does not resume at full reduction") {
            fx.weight shouldBe 0.0
        }

        fx.orbitBlockRan()
        context.mixBuffer.fill(0.5)
        context.sidechainBuffer = side
        fx.process(context)
        context.sidechainBuffer = null

        withClue("and its first block is one step in") {
            abs(fx.weight - 1.0 / glideBlocks) shouldBeLessThanOrEqual 1e-12
        }
    }

    "reset is a hard cut and re-arms the snap" {
        val fx = stage()
        val context = ctx()
        val side = StereoBuffer(frames)

        side.left.fill(0.9)
        side.right.fill(0.9)

        fx.configure(settings(orbit = 2, depth = 0.8))
        fx.orbitBlockRan()
        context.mixBuffer.fill(0.5)
        context.sidechainBuffer = side
        fx.process(context)
        context.sidechainBuffer = null

        fx.configure(null)
        fx.ducking.shouldNotBeNull()

        fx.reset()

        fx.ducking.shouldBeNull()
        fx.duckCylinderId.shouldBeNull()
        fx.handedOver shouldBe false
        fx.weight shouldBe 0.0

        // The next life's first setting is in force at once.
        fx.configure(settings(orbit = 2, depth = 0.8))
        fx.weight shouldBe 1.0
    }

    "does nothing when no ducking is configured" {
        val fx = stage()
        val context = ctx()

        context.mixBuffer.fill(0.5)
        fx.process(context)

        context.mixBuffer.left[0] shouldBe 0.5
        context.mixBuffer.right[0] shouldBe 0.5
    }

    "does nothing when the sidechain buffer is null" {
        val fx = stage()
        val context = ctx()

        fx.configure(settings(orbit = 0, depth = 1.0))
        context.mixBuffer.fill(0.5)
        context.sidechainBuffer = null
        fx.process(context)

        context.mixBuffer.left[0] shouldBe 0.5
    }

    "linked stereo: both channels get identical gain reduction" {
        val fx = stage()
        val context = ctx()

        fx.configure(settings(orbit = 0, depth = 0.8, attack = 0.001))
        context.mixBuffer.fill(1.0)

        // Asymmetric sidechain: left is louder.
        val side = StereoBuffer(frames)

        side.left.fill(0.9)
        side.right.fill(0.1)
        context.sidechainBuffer = side

        fx.process(context)

        abs(context.mixBuffer.left[frames - 1] - context.mixBuffer.right[frames - 1]) shouldBeLessThanOrEqual 1e-12
    }

    "no ducking when the sidechain is silent" {
        val blocks = 100
        val src = mix(blocks)
        val side = DoubleArray(blocks * frames)

        val got = run(src, side) { settings(orbit = 0, depth = 1.0, attack = 0.001) }

        abs(got[src.size - 1] - 0.5) shouldBeLessThanOrEqual 0.01
    }
})
