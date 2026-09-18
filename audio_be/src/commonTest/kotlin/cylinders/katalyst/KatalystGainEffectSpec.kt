/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.voices.TestIgnitors
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.constants.SLOT_UNSET
import kotlin.math.abs

/**
 * The orbit's group fader ([KatalystGainEffect]): exact where it must be, and never a step.
 *
 * The master snaps its gain (its factor is resolved at chain build, so a new number is a new
 * chain and the 60 ms bus crossfade covers it). Here the factor is a SLOT, so `.katp` moves it
 * while the stage stands, and the stage owns the smoothing: one linear ramp per sample across one
 * block, which is exactly the interval between two possible param-state reads.
 */
class KatalystGainEffectSpec : StringSpec({

    val blockFrames = 128
    val sampleRate = 44100

    /** The DC probe: a constant makes every step in the output the ramp's own. */
    val probe = 0.5

    fun ctx(): KatalystContext = KatalystContext(
        blockFrames = blockFrames,
        mixBuffer = StereoBuffer(blockFrames),
        delaySendBuffer = StereoBuffer(blockFrames),
        reverbSendBuffer = StereoBuffer(blockFrames),
    )

    /** One block of DC through [fx], returning the left channel. */
    fun block(fx: KatalystGainEffect, level: Double = probe): DoubleArray {
        val ctx = ctx()

        for (i in 0 until blockFrames) {
            ctx.mixBuffer.left[i] = level
            ctx.mixBuffer.right[i] = level
        }

        fx.process(ctx)

        return ctx.mixBuffer.left.copyOf()
    }

    /** The largest sample-to-sample step in [samples], the zipper measure. */
    fun maxStep(samples: DoubleArray): Double {
        var worst = 0.0

        for (i in 1 until samples.size) {
            val step = abs(samples[i] - samples[i - 1])

            if (step > worst) {
                worst = step
            }
        }

        return worst
    }

    // ── Exactness ────────────────────────────────────────────────────────────────────────────────

    "unity is bit-transparent, negative zero included" {
        val fx = KatalystGainEffect()
        fx.configure(1.0)

        val ctx = ctx()

        for (i in 0 until blockFrames) {
            ctx.mixBuffer.left[i] = if (i % 2 == 0) 0.3 else -0.0
            ctx.mixBuffer.right[i] = -0.0
        }

        fx.process(ctx)

        withClue("unity to unity is not a ramp: the stage takes the constant path") {
            fx.ramps shouldBe 0
        }

        for (i in 0 until blockFrames) {
            withClue("frame $i") {
                // Raw bits, not equality: `-0.0 == 0.0` is true, so a stage that flipped the sign
                // of every silent sample would pass a plain compare. A multiply by exactly 1.0
                // keeps the sign anyway, which is why the skip is a saving and not a change.
                ctx.mixBuffer.left[i].toRawBits() shouldBe (if (i % 2 == 0) 0.3 else -0.0).toRawBits()
                ctx.mixBuffer.right[i].toRawBits() shouldBe (-0.0).toRawBits()
            }
        }
    }

    "a gain of 2 doubles every sample exactly, from the first block on" {
        val fx = KatalystGainEffect()
        fx.configure(2.0)

        val first = block(fx)
        val steady = block(fx)

        for (i in 0 until blockFrames) {
            withClue("frame $i") {
                // An ARRIVING factor is not a move: nothing was produced under the old one, so
                // there is nothing to be continuous with and the stage snaps.
                first[i] shouldBe probe * 2.0
                steady[i] shouldBe probe * 2.0
            }
        }

        fx.ramps shouldBe 0
    }

    "the fader is raw: a negative factor flips polarity and nothing is clamped" {
        val fx = KatalystGainEffect()
        fx.configure(-3.0)

        block(fx)
        val steady = block(fx)

        steady[blockFrames - 1] shouldBe probe * -3.0
    }

    "a non-finite factor is UNSET: the fader keeps what it had, and nothing reaches the mix" {
        // The trap the body resonator fell into (found in review 2026-09-18), closed at this door
        // too. With a non-finite target `from == to` in `process` is FALSE FOREVER for a NaN, so
        // every block ramps, multiplies the whole orbit by NaN and counts a ramp, without bound;
        // an infinity settles but poisons the mix just the same. Unreachable through
        // `KatalystGainWriter`, which reads an unset slot as unity, so this guards the stage's own
        // door, where the cost of being wrong is unbounded.
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { unset ->
            val fx = KatalystGainEffect()

            fx.configure(0.5)
            block(fx) // the arriving factor snaps, so the fader now stands at 0.5

            val rampsBefore = fx.ramps

            fx.configure(unset)

            val out = block(fx)

            withClue("gain = $unset") {
                fx.gain shouldBe 0.5
                fx.ramps shouldBe rampsBefore
                out[blockFrames - 1] shouldBe probe * 0.5
            }
        }
    }

    // ── The ramp ─────────────────────────────────────────────────────────────────────────────────

    "a change ramps per sample across exactly one block and lands on the new factor" {
        val fx = KatalystGainEffect()
        fx.configure(1.0)
        block(fx)

        fx.configure(2.0)
        val ramped = block(fx)

        // Derived from the class's own definition: the first sample is one step in and the last is
        // the new factor, so the block is continuous with the block before (which ended at 1.0)
        // and with the block after (which starts at 2.0).
        val step = (2.0 - 1.0) / blockFrames

        for (i in 0 until blockFrames) {
            withClue("frame $i") {
                ramped[i] shouldBe probe * (1.0 + step * (i + 1))
            }
        }

        // And the block after it is the new factor exactly, with no residue of the ramp.
        block(fx)[0] shouldBe probe * 2.0
    }

    "the ramp bounds the step at the change by the block length, where a snap would not" {
        val fx = KatalystGainEffect()
        fx.configure(1.0)
        val before = block(fx)

        fx.configure(2.0)
        val across = before + block(fx) + block(fx)

        // The bound is derived, not borrowed: the weight moves by `|new - old| / blockFrames` per
        // sample, so the output can step by at most `probe * 1.0 / 128` = 0.0039. A snap, which is
        // what the master accepts for the same knob, would step by `probe * 1.0` = 0.5, 128 times
        // more; the threshold sits just above the ramp and far below the snap.
        maxStep(across) shouldBeLessThan (probe * 1.0 / blockFrames) * 1.01
    }

    "a steady fader never ramps at all: it arrives and then multiplies by a constant" {
        val fx = KatalystGainEffect()

        repeat(20) {
            fx.configure(0.25)
            block(fx)
        }

        // The work pin (the `hasRawTap` / `staticConfigureSkips` precedent): re-applying the same
        // number every block, which is what a chain without `.katp` does, must not keep ramping.
        fx.ramps shouldBe 0
        fx.gain shouldBe 0.25
    }

    "reset puts the fader back to unity, so a reused orbit starts transparent" {
        val fx = KatalystGainEffect()
        fx.configure(4.0)
        block(fx)
        block(fx)

        fx.reset()

        val after = block(fx)

        // The FIRST sample, not just the last: a reset that only re-targeted unity would ramp
        // DOWN from the factor it still held, so the orbit's next tenant would hear a block of
        // the previous one's level. The clean slate is immediate.
        after[0] shouldBe probe
        after[blockFrames - 1] shouldBe probe
        fx.gain shouldBe 1.0

        withClue("and getting there was not a ramp either") {
            fx.ramps shouldBe 0
        }
    }

    "retire puts the fader back to unity too: a shelved stage holds no level" {
        val fx = KatalystGainEffect()
        fx.configure(4.0)
        block(fx)

        fx.retire()

        // Retiring is the clean slate for a stage going to the shelf WITH its cylinder, and the
        // next tenant configures it from scratch. A retire that forgot the factor would scale the
        // first block of whoever rents this cylinder next.
        block(fx)[0] shouldBe probe
        fx.gain shouldBe 1.0
    }

    // ── Through the chain: the writer's one guard ────────────────────────────────────────────────

    "an unset gain slot is unity, not a NaN in the mix" {
        // The writer's NaN guard, the same reading `MasterChain.buildGain` gives the same knob: a
        // non-finite slot was never set, and an unset fader is the identity. Without it the whole
        // orbit would be NaN for good.
        val chain = KatalystChainBuilder.build(
            dsl = KatalystDsl.of(KatalystStageDsl.Gain(gain = IgnitorDsl.Param("gain", SLOT_UNSET))),
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            rings = SizedBuffers.forRings(sampleRate),
            reverbs = ReverbUnits(sampleRate),
            voiceDriven = false,
        )

        chain.applyParams(null)

        val ctx = ctx()

        for (i in 0 until blockFrames) {
            ctx.mixBuffer.left[i] = probe
            ctx.mixBuffer.right[i] = probe
        }

        chain.process(ctx)

        ctx.mixBuffer.left[blockFrames - 1] shouldBe probe
    }

    "the gain slot is what `katp` moves, and the move ramps to the value the pattern wrote" {
        val chain = KatalystChainBuilder.build(
            dsl = KatalystDsl.of(KatalystStageDsl.Gain(gain = IgnitorDsl.Param("gain", 1.0))),
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            rings = SizedBuffers.forRings(sampleRate),
            reverbs = ReverbUnits(sampleRate),
            voiceDriven = false,
        )

        fun renderBlock(): DoubleArray {
            val ctx = ctx()

            for (i in 0 until blockFrames) {
                ctx.mixBuffer.left[i] = probe
                ctx.mixBuffer.right[i] = probe
            }

            chain.process(ctx)

            return ctx.mixBuffer.left.copyOf()
        }

        // In service first, at the authored default, so what follows is a MOVE and not an arrival.
        chain.applyParams(null)
        renderBlock()

        chain.applyParams(mapOf("gain" to 0.5))

        // One block of ramp, landing exactly on what the pattern wrote.
        renderBlock()[blockFrames - 1] shouldBe probe * 0.5
        renderBlock()[0] shouldBe probe * 0.5
    }

    // ── The mute idiom, through a real cylinder ──────────────────────────────────────────────────

    "a gain-0 orbit stays exactly silent across the deactivation grace, block after block" {
        // `k.classic().gain(0)` is how a mix is muted at the bus. The mix is then silent while the
        // voices play, so `Cylinder.tryDeactivate` reaches its silence grace every tenth block,
        // deactivates and RESETS the chain; the next voice to claim the orbit reconfigures the
        // fader. If an arriving factor ramped, that block would come out at nearly full level,
        // once every eleven blocks: a 31 Hz buzz out of an orbit the author muted.
        val registry = KatalystRegistry()
        val cylinders = Cylinders(blockFrames = blockFrames, sampleRate = sampleRate, katalysts = registry)
        val orbit = 1

        registry.register(
            "mute",
            KatalystDsl(KatalystDsl.classic.stages + KatalystStageDsl.Gain(gain = IgnitorDsl.Constant(0.0))),
        )
        cylinders.requestChain(orbit, "mute")

        val renderCtx = Voice.RenderContext(
            cylinders = cylinders,
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            voiceBuffer = AudioBuffer(blockFrames),
            freqModBuffer = DoubleArray(blockFrames),
            scratchBuffers = ScratchBuffers(blockFrames),
        )

        val blocks = 40
        val voice = VoiceTestHelpers.createSynthVoice(
            endFrame = (blocks * blockFrames).toDouble(),
            gateEndFrame = (blocks * blockFrames).toDouble(),
            cylinderId = orbit,
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            signal = TestIgnitors.constant,
        )

        val fusion = StereoBuffer(blockFrames)

        for (b in 0 until blocks) {
            renderCtx.blockStart = (b * blockFrames).toDouble()
            cylinders.clearAll()
            fusion.clear()
            voice.render(renderCtx)
            cylinders.processAndMix(fusion)

            for (i in 0 until blockFrames) {
                withClue("block $b frame $i") {
                    fusion.left[i] shouldBe 0.0
                    fusion.right[i] shouldBe 0.0
                }
            }
        }
    }
})
