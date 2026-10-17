/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.cylinders.Cylinder
import io.peekandpoke.klang.audio_be.cylinders.offerAndCommit
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_bridge.constants.KNOB_GLIDE_SECONDS
import io.peekandpoke.klang.common.infra.KlangMap
import io.peekandpoke.klang.common.infra.klangMapOf
import io.peekandpoke.klang.common.infra.toKlangMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.sin

/**
 * Every switch-off of the orbit delay and reverb FADES the feed over the wet glide instead of cutting it (engine
 * follow-up 42, maintainer Q39, 2026-10-13: "fade, do not cut"; `docs/tasks-archive/2026-10/20261016-delay-reverb-owner-change-cut.md`
 * option (b)). Unmodulated stages throughout: the modulated gates have faded since knob expressions step 5b
 * (`KatalystModulatedStagesSpec`).
 *
 * The oracle is the stage's own law, not a run of the code under test: a second stage of the same kind kept ON at
 * wet 1, fed the mix times the hand-computed ramp (from the wet to 0, linear per sample over [KNOB_GLIDE_SECONDS],
 * written from the end so the last sample is 0), and then silence (a drain is "Active on silent input"). The fade
 * needs ONE off-config: the block it lands in moves the stage on into the drain by itself, which is what lets an
 * orbit whose owner stopped offering drain (`Cylinder.commitOwner` configures nothing without an owner).
 *
 * 44.1 kHz and 128 frames: one fade is 17 blocks.
 */
class KatalystSwitchOffFadeSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128
    val fadeBlocks = round(KNOB_GLIDE_SECONDS * sampleRate / blockFrames).toInt()
    val fadeSamples = fadeBlocks * blockFrames

    /** Two partials, so every cell of the ring or the network differs. */
    fun input(block: Int, i: Int): Double {
        val n = (block * blockFrames + i).toDouble()

        return 0.3 * sin(2.0 * PI * 440.0 * n / sampleRate) + 0.2 * sin(2.0 * PI * 97.0 * n / sampleRate)
    }

    fun stateOf(state: Any): String = state::class.simpleName ?: "?"

    /** One stage under test, delay or reverb, behind the four things a row does with it. */
    class Stage(
        val name: String,
        val effect: KatalystEffect,
        val on: (wet: Double) -> Unit,
        val off: () -> Unit,
        val state: () -> Any,
        val wetInForce: () -> Double,
    )

    fun delay(offTime: Double) = KatalystDelayEffect(
        rings = SizedBuffers.forRings(sampleRate),
        sampleRate = sampleRate,
        blockFrames = blockFrames,
    ).let { fx ->
        Stage(
            name = "delay, off at time $offTime",
            effect = fx,
            on = { wet -> fx.configure(time = 0.02, feedback = 0.5, cap = 1.0, wet = wet) },
            off = { fx.configure(time = offTime, feedback = 0.5, cap = 1.0, wet = 0.8) },
            state = { fx.currentState },
            wetInForce = { fx.wetInForce },
        )
    }

    fun reverb(offSize: Double) = KatalystReverbEffect(
        units = ReverbUnits(sampleRate),
        blockFrames = blockFrames,
    ).let { fx ->
        Stage(
            name = "reverb, off at size $offSize",
            effect = fx,
            on = { wet -> fx.configure(size = 0.5, lowpass = null, wet = wet) },
            off = { fx.configure(size = offSize, lowpass = null, wet = 0.8) },
            state = { fx.currentState },
            wetInForce = { fx.wetInForce },
        )
    }

    /**
     * Every off path of each stage: a time or size below the gate (what a `katp` pattern writes), zero, unset (the
     * slot writer hands NaN for a stage nobody asked for, the owner-change case) and infinite.
     */
    val delayOffs = listOf(0.005, 0.0, Double.NaN, Double.POSITIVE_INFINITY)
    val reverbOffs = listOf(0.005, 0.0, Double.NaN, Double.POSITIVE_INFINITY)

    fun pairs(): List<Pair<Stage, Stage>> =
        delayOffs.map { delay(it) to delay(it) } + reverbOffs.map { reverb(it) to reverb(it) }

    fun ctx() = KatalystContext(blockFrames = blockFrames, mixBuffer = StereoBuffer(blockFrames))

    /** One block of [stage] on [c], whose mix is [mix] per sample; returns what the stage ADDED to the mix. */
    fun block(stage: Stage, c: KatalystContext, mix: (Int) -> Double): DoubleArray {
        for (i in 0 until blockFrames) {
            c.mixBuffer.left[i] = mix(i)
            // Not the negated mix: the reverb sums its input to mono, and a mix that cancels would leave the room empty.
            c.mixBuffer.right[i] = 0.5 * mix(i)
        }

        stage.effect.process(c)

        return DoubleArray(blockFrames) { i -> c.mixBuffer.left[i] - mix(i) }
    }

    /** Runs [stage] block by block with no configure until it reports no tail; returns the blocks it took. */
    fun drainToOff(stage: Stage, c: KatalystContext): Int {
        var blocks = 0

        while (stage.effect.hasTail() && blocks < 100_000) {
            block(stage, c) { 0.0 }
            blocks++
        }

        return blocks
    }

    "every switch-off path fades the feed linearly to 0, then drains on its own with no further off-config" {
        val wet = 0.8

        for ((fx, ref) in pairs()) {
            withClue(fx.name) {
                val c = ctx()
                val r = ctx()

                fx.on(wet)
                ref.on(1.0)

                repeat(30) { b ->
                    block(fx, c) { i -> input(b, i) }
                    block(ref, r) { i -> input(b, i) * wet }
                }

                // ONE off-config, as on the block an owner changes and then stops offering.
                fx.off()

                var worst = 0.0

                for (k in 0 until fadeBlocks + 40) {
                    val b = 30 + k
                    val got = block(fx, c) { i -> input(b, i) }
                    val want = block(ref, r) { i ->
                        val n = k * blockFrames + i

                        if (n < fadeSamples) input(b, i) * wet * (1.0 - (n + 1).toDouble() / fadeSamples) else 0.0
                    }

                    for (i in 0 until blockFrames) {
                        worst = maxOf(worst, abs(got[i] - want[i]))
                    }

                    val expected = if (k < fadeBlocks - 1) "Active" else "Draining"

                    withClue("block $k of the fade") { stateOf(fx.state()) shouldBe expected }

                    if (k < fadeBlocks) {
                        withClue("block $k: the wet in force") {
                            fx.wetInForce() shouldBe (wet * (1.0 - (k + 1).toDouble() / fadeBlocks) plusOrMinus 1e-12)
                        }
                    }
                }

                withClue("the fade and the drain against the law: $worst") { (worst <= 1e-12) shouldBe true }

                // The drain completes, still with no configure.
                drainToOff(fx, c)
                withClue("the drain ends in Off") { stateOf(fx.state()) shouldBe "Off" }
                fx.effect.hasTail() shouldBe false
            }
        }
    }

    "a return mid-fade turns the glide around and the stage never leaves Active" {
        val wet = 0.8

        for ((fx, _) in pairs()) {
            withClue(fx.name) {
                val c = ctx()

                fx.on(wet)

                repeat(30) { b -> block(fx, c) { i -> input(b, i) } }

                fx.off()

                repeat(5) { k -> block(fx, c) { i -> input(30 + k, i) } }

                fx.wetInForce() shouldBe (wet * (1.0 - 5.0 / fadeBlocks) plusOrMinus 1e-12)

                // Back before the fade landed: the glide turns around where it stands and lands on the wet again.
                fx.on(wet)

                repeat(fadeBlocks + 40) { k ->
                    block(fx, c) { i -> input(35 + k, i) }

                    withClue("block $k after the return") { stateOf(fx.state()) shouldBe "Active" }
                }

                fx.wetInForce() shouldBe wet
            }
        }
    }

    "an off-config re-applied every block, applied once, or dropped mid-fade renders the same samples" {
        // The three ways a fade can be driven: an owner that keeps offering its off-config, an owner that offers it
        // once and is gone, an owner lost mid-fade. The fade needs only the first; the block it lands in moves on.
        for (useReverb in listOf(false, true)) {
            fun make(): Stage = if (useReverb) reverb(Double.NaN) else delay(Double.NaN)

            val every = make()
            val once = make()
            val lost = make()
            val stages = listOf(every, once, lost)
            val contexts = stages.map { ctx() }

            withClue(every.name) {
                stages.forEach { it.on(0.8) }

                repeat(30) { b ->
                    stages.forEachIndexed { s, stage -> block(stage, contexts[s]) { i -> input(b, i) } }
                }

                once.off()

                for (k in 0 until fadeBlocks + 40) {
                    every.off()

                    if (k < 8) {
                        lost.off()
                    }

                    val outs = stages.mapIndexed { s, stage -> block(stage, contexts[s]) { i -> input(30 + k, i) } }

                    withClue("block $k: re-applied against once") { outs[0].toList() shouldBe outs[1].toList() }
                    withClue("block $k: lost mid-fade against once") { outs[2].toList() shouldBe outs[1].toList() }
                    withClue("block $k: the states") {
                        stages.map { stateOf(it.state()) } shouldBe List(3) { stateOf(once.state()) }
                    }
                }

                withClue("the fade landed and the drain runs") { stateOf(once.state()) shouldBe "Draining" }
            }
        }
    }

    // ── On a cylinder: the owner change, and the orbit that loses its owner mid-fade ─────────────────────────────

    /** What `.delay(...)` and `.reverb(...)` write: the stages' slots, the classic chain's names. */
    val owner: KlangMap<Double> = klangMapOf(
        "delay.wet" to 0.5, "delay.time" to 0.02, "delay.feedback" to 0.5,
        "reverb.wet" to 0.5, "reverb.size" to 6.0,
    )

    "an owner change switches the delay and the reverb off with a fade, and the orbit drains with no owner left" {
        // Two next owners: one that names neither stage (the writers hand NaN), one whose `katp` sets the time and the
        // size below their gates (authored size 0.05 normalizes under 0.01). Each offers for 5 blocks of the fade and
        // is gone; nothing configures the orbit after that.
        val nextOwners = listOf(
            "names neither stage" to emptyMap<String, Double>().toKlangMap(),
            "katp below the gates" to klangMapOf(
                "delay.wet" to 0.5, "delay.time" to 0.005, "delay.feedback" to 0.5,
                "reverb.wet" to 0.5, "reverb.size" to 0.05,
            ),
        )

        for ((label, next) in nextOwners) {
            withClue(label) {
                val cylinder = Cylinder(id = 0, blockFrames = blockFrames, sampleRate = sampleRate)
                val first = VoiceTestHelpers.createSynthVoice(katalystParams = owner)
                val second = VoiceTestHelpers.createSynthVoice(startFrame = 10.0 * blockFrames, katalystParams = next)

                fun run(b: Int) {
                    for (i in 0 until blockFrames) {
                        cylinder.mixBuffer.left[i] = input(b, i)
                        cylinder.mixBuffer.right[i] = input(b, i)
                    }

                    cylinder.processEffects()
                }

                for (b in 0 until 10) {
                    cylinder.offerAndCommit(first, blockStart = b.toDouble() * blockFrames)
                    run(b)
                }

                val delay = cylinder.delay!!
                val reverb = cylinder.reverb!!

                for (k in 0 until fadeBlocks) {
                    val b = 10 + k

                    if (k < 5) {
                        cylinder.offerAndCommit(second, blockStart = b.toDouble() * blockFrames)
                    }

                    run(b)

                    val wetAfter = 0.5 * (1.0 - (k + 1).toDouble() / fadeBlocks)
                    val expected = if (k < fadeBlocks - 1) "Active" else "Draining"

                    withClue("block $k of the fade") {
                        delay.wetInForce shouldBe (wetAfter plusOrMinus 1e-12)
                        reverb.wetInForce shouldBe (wetAfter plusOrMinus 1e-12)
                        stateOf(delay.currentState) shouldBe expected
                        stateOf(reverb.currentState) shouldBe expected
                    }
                }

                var blocks = 0

                while ((delay.hasTail() || reverb.hasTail()) && blocks < 100_000) {
                    run(10 + fadeBlocks + blocks)
                    blocks++
                }

                withClue("both drains end in Off with no owner") {
                    stateOf(delay.currentState) shouldBe "Off"
                    stateOf(reverb.currentState) shouldBe "Off"
                }
            }
        }
    }
})
