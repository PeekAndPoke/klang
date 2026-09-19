/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import kotlin.math.abs

/**
 * [KatalystDsl.classic] ends in a group fader at unity (the signal-flow plan, section 6, spot C,
 * 2026-09-19). Two claims, and they pull in opposite directions, which is why both are here.
 *
 * **It costs nothing.** At unity the stage returns before it multiplies, so the classic chain must
 * render exactly what it rendered before the stage existed. The oracle is not a remembered number
 * and not a render from another commit: it is the chain the classic chain WAS, built here by
 * dropping the gain stage from the declaration. Same stages, same order, same knobs; the only
 * difference is the stage under test. Sample for sample, by raw bits, over several blocks so the
 * stateful stages (the room, the line, the compressor's follower) have to agree as they evolve.
 *
 * **It is reachable.** `katp("gain.gain", x)` moves it on ANY orbit, because a gain stage is
 * slot-driven whatever the chain's `voiceDriven` flag says. Halving it must halve the mix exactly
 * (0.5 is a power of two), INCLUDING what the delay and the reverb return, which is the honest
 * question about a fader while the sends are still send buses: the returns are mixed in by their
 * own stages, and those sit before this one, so they are covered. The duck is not, and cannot be
 * (it runs outside the list, in the cross-orbit pass); that is stated in the stage's KDoc and is
 * not something this spec can change.
 */
class KatalystClassicGainStageSpec : StringSpec({

    val blockFrames = 128
    val sampleRate = 44100
    /**
     * Long enough for the RETURNS to arrive. A Freeverb comb is 1116 to 1617 samples at 44.1 kHz
     * and the line below taps at 0.02 s (882 samples), so a six-block run would compare two dry
     * mixes and call the room guarded. 24 blocks is 3072 samples, past both.
     */
    val blocks = 24

    /** The classic chain minus its fader: what `KatalystDsl.classic` was until 2026-09-19. */
    val withoutFader = KatalystDsl(KatalystDsl.classic.stages.filterNot { it is KatalystStageDsl.Gain })

    fun build(dsl: KatalystDsl, voiceDriven: Boolean = false): KatalystChain = KatalystChainBuilder.build(
        dsl = dsl,
        sampleRate = sampleRate,
        blockFrames = blockFrames,
        rings = SizedBuffers.forRings(sampleRate),
        reverbs = ReverbUnits(sampleRate),
        voiceDriven = voiceDriven,
    )

    fun ctx(): KatalystContext = KatalystContext(
        blockFrames = blockFrames,
        mixBuffer = StereoBuffer(blockFrames),
        delaySendBuffer = StereoBuffer(blockFrames),
        reverbSendBuffer = StereoBuffer(blockFrames),
    )

    /**
     * Runs [blocks] blocks of a deterministic pseudo-random signal through [chain], feeding the
     * dry mix AND both send buses, and returns every output sample of both channels.
     *
     * Pseudo-random rather than DC or a ramp: DC would hide a stage that only touches transients
     * and a ramp would hide one that reverses a buffer. The same seed on both sides of every
     * comparison, so the two renders see the identical input stream.
     */
    fun render(chain: KatalystChain, params: Map<String, Double>?): DoubleArray {
        val ctx = ctx()
        val out = DoubleArray(blockFrames * blocks * 2)
        var x = 0x5EED

        fun next(): Double {
            x = x * 1103515245 + 12345

            return ((x ushr 8) and 0xFFFF) / 65535.0 - 0.5
        }

        // Resolved and applied inside the loop, before every `process`, which is where the
        // production cylinder does it too. No second call out here: the first pass through the
        // loop already resolves (`KatalystChain.everResolved`), so one would be dead code.
        for (b in 0 until blocks) {
            for (i in 0 until blockFrames) {
                val dry = next()
                val send = next()

                ctx.mixBuffer.left[i] = dry
                ctx.mixBuffer.right[i] = dry * 0.5
                ctx.delaySendBuffer.left[i] = send
                ctx.delaySendBuffer.right[i] = send * 0.5
                ctx.reverbSendBuffer.left[i] = send
                ctx.reverbSendBuffer.right[i] = send * 0.5
            }

            chain.applyParams(params)
            chain.process(ctx)

            for (i in 0 until blockFrames) {
                out[(b * blockFrames + i) * 2] = ctx.mixBuffer.left[i]
                out[(b * blockFrames + i) * 2 + 1] = ctx.mixBuffer.right[i]
            }
        }

        return out
    }

    /**
     * The same, with the DRY input held at silence, so every sample that comes out is a RETURN
     * the delay or the reverb put there. A fader sitting before the send stages would leave this
     * render untouched whatever its factor says, which is the discriminator [render] cannot be.
     */
    fun renderReturnsOnly(chain: KatalystChain, params: Map<String, Double>?): DoubleArray {
        val ctx = ctx()
        val out = DoubleArray(blockFrames * blocks)
        var x = 0x1234

        for (b in 0 until blocks) {
            for (i in 0 until blockFrames) {
                x = x * 1103515245 + 12345
                val send = ((x ushr 8) and 0xFFFF) / 65535.0 - 0.5

                ctx.mixBuffer.left[i] = 0.0
                ctx.mixBuffer.right[i] = 0.0
                ctx.delaySendBuffer.left[i] = send
                ctx.delaySendBuffer.right[i] = send
                ctx.reverbSendBuffer.left[i] = send
                ctx.reverbSendBuffer.right[i] = send
            }

            chain.applyParams(params)
            chain.process(ctx)

            for (i in 0 until blockFrames) {
                out[b * blockFrames + i] = ctx.mixBuffer.left[i]
            }
        }

        return out
    }

    /** The slot state of an audible room and line, so the returns are really in the mix. */
    val wet: Map<String, Double> = mapOf(
        "delay.wet" to 0.5, "delay.time" to 0.02, "delay.feedback" to 0.5,
        "reverb.wet" to 0.5, "reverb.size" to 4.0,
    )

    fun DoubleArray.bits(): List<Long> = map { it.toRawBits() }

    fun DoubleArray.peak(): Double = maxOf { abs(it) }

    "the classic chain at unity renders what the chain WITHOUT the stage renders, bit for bit" {
        val before = render(build(withoutFader), null)
        val now = render(build(KatalystDsl.classic), null)

        withClue("not-silence floor") { before.peak() shouldBeGreaterThan 0.1 }

        now.bits() shouldBe before.bits()
    }

    "and with the room and the line running, where the stage has returns to leave alone" {
        val before = render(build(withoutFader), wet)
        val now = render(build(KatalystDsl.classic), wet)

        withClue("not-silence floor") { before.peak() shouldBeGreaterThan 0.1 }

        withClue("engagement: the wet state really changes what the chain renders") {
            render(build(withoutFader), null).bits() shouldNotBe before.bits()
        }

        now.bits() shouldBe before.bits()
    }

    "katp on the fader halves the mix exactly, returns included" {
        // A SCALING LAW, not an identity: both sides are engine renders, so what this pins is
        // that the output is LINEAR in the knob, not that any particular sample is right. The
        // independent anchor for the samples themselves is the two rows above, which compare this chain against the chain
        // WITHOUT the stage, a structurally different one.
        val unity = render(build(KatalystDsl.classic), wet)
        val halved = render(build(KatalystDsl.classic), wet + mapOf("gain.gain" to 0.5))

        withClue("not-silence floor") { unity.peak() shouldBeGreaterThan 0.1 }

        for (i in unity.indices) {
            withClue("sample $i") { halved[i].toRawBits() shouldBe (unity[i] * 0.5).toRawBits() }
        }
    }

    "the halving covers the RETURNS, not only the dry: the wet-only mix halves too" {
        // The dry input is zeroed for this row, so every sample that comes out is a return. If the
        // fader sat before the send stages, this render would be silent at both settings and the
        // row above could pass on the dry alone.
        //
        // A SCALING LAW like the row above (both sides are engine renders), and what makes it
        // worth its own row is WHICH samples it holds for, not the law: the anchor for the
        // samples is the wet-state identity row further up.
        val unity = renderReturnsOnly(build(KatalystDsl.classic), wet)
        val halved = renderReturnsOnly(build(KatalystDsl.classic), wet + mapOf("gain.gain" to 0.5))

        withClue("not-silence floor: the returns alone are audible") {
            unity.peak() shouldBeGreaterThan 0.01
        }

        for (i in unity.indices) {
            withClue("return sample $i") { halved[i].toRawBits() shouldBe (unity[i] * 0.5).toRawBits() }
        }
    }

    "the born-with chain has the fader too, and katp reaches it there as well" {
        // A SCALING LAW, not an identity: both sides are engine renders, so what this pins is
        // that the output is LINEAR in the knob, not that any particular sample is right. The
        // independent anchor for the samples themselves is the first two rows of this spec, and the cylinder-level twin in
        // `CylinderKatalystParamsSpec`.
        // `voiceDriven = true` is the chain a cylinder is BORN with. A gain stage is slot-driven
        // on every chain, so an orbit that declares nothing still has a group fader a pattern can
        // move, which is half the reason the stage is in `classic` at all.
        val unity = render(build(KatalystDsl.classic, voiceDriven = true), null)
        val halved = render(build(KatalystDsl.classic, voiceDriven = true), mapOf("gain.gain" to 0.5))

        withClue("not-silence floor") { unity.peak() shouldBeGreaterThan 0.1 }

        for (i in unity.indices) {
            withClue("sample $i") { halved[i].toRawBits() shouldBe (unity[i] * 0.5).toRawBits() }
        }
    }

    "k.classic().gain(0.8) is two faders in series, and they multiply" {
        // The author's own stage after classic's slot. At unity the product is 0.8; with the slot
        // at 0.5 it is 0.4. Two multiplies, no special case, which is what "an ordinary stage"
        // means on this side of the wire too.
        val authored = KatalystDsl(KatalystDsl.classic.stages + KatalystStageDsl.Gain(IgnitorDsl.Constant(0.8)))

        val reference = render(build(KatalystDsl.classic), null)
        val trimmed = render(build(authored), null)
        val both = render(build(authored), mapOf("gain.gain" to 0.5))

        withClue("not-silence floor") { reference.peak() shouldBeGreaterThan 0.1 }

        for (i in reference.indices) {
            withClue("0.8 alone, sample $i") {
                trimmed[i].toRawBits() shouldBe (reference[i] * 0.8).toRawBits()
            }
        }

        for (i in reference.indices) {
            // 0.5 then 0.8: the engine multiplies in that order, so the oracle does too.
            withClue("0.5 then 0.8, sample $i") {
                both[i].toRawBits() shouldBe (reference[i] * 0.5 * 0.8).toRawBits()
            }
        }
    }

    "two stages whose knobs are slots of the SAME name apply the write twice: 0.5 becomes 0.25" {
        // The sharp edge of "a stage list is a list", stated in `KatalystStageDsl.Gain`'s KDoc and
        // pinned here so nobody "fixes" it into a special case. Nothing dedupes slot names across
        // stages: each stage's writer reads the key it was given, so a second fader wired to
        // `gain.gain` squares the pattern's value.
        val doubled = KatalystDsl(
            KatalystDsl.classic.stages + KatalystStageDsl.Gain(IgnitorDsl.Param("gain.gain", 1.0))
        )

        val reference = render(build(KatalystDsl.classic), null)
        val once = render(build(KatalystDsl.classic), mapOf("gain.gain" to 0.5))
        val twice = render(build(doubled), mapOf("gain.gain" to 0.5))

        withClue("not-silence floor") { reference.peak() shouldBeGreaterThan 0.1 }

        withClue("the second stage is inert at unity, so the two chains start equal") {
            render(build(doubled), null).bits() shouldBe reference.bits()
        }

        withClue("engagement: one stage really halves") {
            once.bits() shouldNotBe reference.bits()
        }

        for (i in reference.indices) {
            withClue("0.5 applied twice, sample $i") {
                twice[i].toRawBits() shouldBe (reference[i] * 0.5 * 0.5).toRawBits()
            }
        }
    }
})
