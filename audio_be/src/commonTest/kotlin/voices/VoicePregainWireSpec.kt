/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.engines.PipelineRegistry
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.ignitor.PhasePools
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.ignitor.registerDefaults
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createContext
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.distort
import io.peekandpoke.klang.audio_bridge.pregain
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * `pregain` through the path a song actually takes: `VoiceData.oscParams` -> `IgnitorRegistry`
 * (which OPTIMIZES the registered tree) -> `VoiceFactory` -> `Voice.render` -> the orbit's mix bus.
 *
 * `PregainSlotRenderSpec` proves what the slot does to a hand-built tree. This one exists because
 * the production path has two things that spec does not: the optimizer, which rewrites
 * `x.mul(slot)` into an `Affine` and could in principle fold the slot away, and the whole voice
 * around the ignitor (envelope, filters, the send stage). Three claims:
 *
 *  - **inert through the whole path**: on an instrument that never places the slot, a written
 *    `pregain` cannot move one sample of the mix bus;
 *  - **the non-finite guard**: NaN, +Infinity and -Infinity in the bag read as UNSET at the
 *    `Param` leaf, so they render bit for bit what an unwritten slot renders. Without the guard
 *    a NaN multiplies through the tree and the voice is dead for its whole life, and the bag is
 *    an open map any frontend fills, so the value is reachable;
 *  - **`gain` stays tone-neutral on a DRIVEN instrument**: halving the channel fader halves the
 *    bus, sample for sample, on the very tree where halving `pregain` changes the SHAPE. The
 *    two words are told apart on one instrument, which is what the parity rule is about, and
 *    the shape half is measured level-blind (both renders normalised to unit RMS first), so
 *    neither a pure level change nor an inert slot can satisfy it.
 */
class VoicePregainWireSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    /**
     * An instrument that DRIVES its waveshaper from the slot: the phase-3 built-in shape.
     *
     * `distort(0.5)` and not `2.0`, which this spec used until 2026-09-19: at `2.0` the drive is
     * about 250x, the shaper is in hard saturation and the slot moves neither the tone nor the
     * level (a normalised shape distance of 0.006 and a level ratio of 0.999). A spec whose
     * "driven" instrument is deaf to the knob under test is worse than none, and it is also the
     * instrument somebody copies out of here.
     */
    val driven: IgnitorDsl = IgnitorDsl.Sawtooth().pregain().distort(0.5)

    /** The same instrument with no slot anywhere, like an authored instrument that never places it. */
    val plain: IgnitorDsl = IgnitorDsl.Sawtooth().distort(0.5)

    /**
     * One voice, built the production way from [data] on an instrument registered as `"test-inst"`,
     * rendered for [blocks] blocks; the orbit's left mix bus comes back.
     *
     * The registry is fresh per call and the phase pool is seeded, so two renders that should
     * agree draw the same numbers.
     */
    fun renderMix(dsl: IgnitorDsl, oscParams: Map<String, Double>?, gain: Double? = null, blocks: Int = 4): DoubleArray {
        val registry = IgnitorRegistry().apply {
            registerDefaults()
            register("test-inst", dsl)
        }
        val factory = VoiceFactory(
            sampleRate = sampleRate,
            sampleRateDouble = sampleRate.toDouble(),
            blockFrames = blockFrames,
            ignitorRegistry = registry,
            pipelineRegistry = PipelineRegistry(),
            cylinders = Cylinders(blockFrames = blockFrames, sampleRate = sampleRate),
            voiceBuffer = DoubleArray(blockFrames),
            freqModBuffer = DoubleArray(blockFrames),
            scratchBuffers = ScratchBuffers(blockFrames),
        )
        val scheduled = ScheduledVoice(
            playbackId = "test",
            data = VoiceData.empty.copy(
                freqHz = 220.0,
                sound = "test-inst",
                oscParams = oscParams,
                gain = gain,
            ),
            startTime = 0.0,
            gateEndTime = 1.0,
            playbackStartTime = 0.0,
        )
        val voice = factory.makeVoice(
            scheduled = scheduled,
            backendStartTimeSec = 0.0,
            playbackCtx = PlaybackCtx(
                playbackId = "test",
                ignitorRegistry = registry,
                phasePools = PhasePools(Random(1)),
            ),
            getSample = { null },
        ) ?: error("makeVoice returned null")

        val ctx = createContext(blockStart = 0.0, blockFrames = blockFrames, sampleRate = sampleRate)
        val out = DoubleArray(blockFrames * blocks)

        for (b in 0 until blocks) {
            ctx.blockStart = (b * blockFrames).toDouble()
            ctx.cylinders.getOrInit(voice.cylinderId, voice, ctx.blockStart).clear()
            voice.render(ctx)

            val mix = ctx.cylinders.getOrInit(voice.cylinderId, voice, ctx.blockStart).mixBuffer.left

            for (i in 0 until blockFrames) {
                out[b * blockFrames + i] = mix[i]
            }
        }

        return out
    }

    fun DoubleArray.bits(): List<Long> = map { it.toRawBits() }

    fun DoubleArray.peak(): Double = maxOf { abs(it) }

    "an instrument with no slot renders bit for bit the same however pregain is written" {
        val unset = renderMix(plain, null)
        val written = renderMix(plain, mapOf("pregain" to 0.3))

        withClue("not-silence floor: the bus carries the instrument") {
            unset.peak() shouldBeGreaterThan 0.05
        }

        written.bits() shouldBe unset.bits()

        withClue("engagement: on the DRIVEN instrument the same write does change the bus") {
            renderMix(driven, mapOf("pregain" to 0.3)).bits() shouldNotBe renderMix(driven, null).bits()
        }
    }

    "a NaN pregain reads as unset, through the real path" {
        renderMix(driven, mapOf("pregain" to Double.NaN)).bits() shouldBe renderMix(driven, null).bits()
    }

    "a +Infinity pregain reads as unset" {
        renderMix(driven, mapOf("pregain" to Double.POSITIVE_INFINITY)).bits() shouldBe
            renderMix(driven, null).bits()
    }

    "a -Infinity pregain reads as unset" {
        renderMix(driven, mapOf("pregain" to Double.NEGATIVE_INFINITY)).bits() shouldBe
            renderMix(driven, null).bits()
    }

    "the non-finite rows are not vacuous: a finite pregain on the same instrument moves the bus" {
        // Without this, all three rows above would also pass on an instrument whose slot was
        // never wired, or on a path that renders silence.
        val unset = renderMix(driven, null)

        withClue("not-silence floor") { unset.peak() shouldBeGreaterThan 0.05 }

        renderMix(driven, mapOf("pregain" to 0.5)).bits() shouldNotBe unset.bits()
    }

    "gain halves the driven instrument exactly, while pregain reshapes it" {
        // The `gain` half is a SCALING LAW, not an identity: both sides are engine renders, so
        // what it pins is that the output is LINEAR in the knob, not that any particular sample is
        // right. The independent anchor for the samples is the non-finite rows above, which are
        // identities between two different bag states.
        //
        // The `pregain` half is a DISTANCE, and the measure matters. Until 2026-09-19 it was
        // `max|halvedIn - unity * 0.5| > 0.01`, which an INERT slot satisfies comfortably (with
        // `halvedIn == unity`, that distance is about half the peak), so the row would have stayed
        // green with `pregain` folded out of the tree entirely. It is a normalised shape distance
        // now: both renders scaled to unit RMS first, so a pure level change reads ZERO and an
        // inert slot reads zero too. Only a real change of SHAPE can pass.
        val unity = renderMix(driven, null, gain = 1.0)
        val halvedOut = renderMix(driven, null, gain = 0.5)
        val halvedIn = renderMix(driven, mapOf("pregain" to 0.5), gain = 1.0)

        withClue("not-silence floor") { unity.peak() shouldBeGreaterThan 0.05 }

        for (i in unity.indices) {
            withClue("gain is a pure scale, sample $i") {
                halvedOut[i].toRawBits() shouldBe (unity[i] * 0.5).toRawBits()
            }
        }

        withClue("engagement: the slot has to move the bus at all") {
            halvedIn.bits() shouldNotBe unity.bits()
        }

        fun rms(a: DoubleArray): Double = sqrt(a.sumOf { it * it } / a.size)

        val unityRms = rms(unity)
        val halvedRms = rms(halvedIn)

        withClue("both renders carry signal to normalise") {
            unityRms shouldBeGreaterThan 0.01
            halvedRms shouldBeGreaterThan 0.005
        }

        var sum = 0.0

        for (i in unity.indices) {
            val d = unity[i] / unityRms - halvedIn[i] / halvedRms

            sum += d * d
        }

        withClue("pregain changes the SHAPE: an inert slot and a pure level both read 0 here") {
            sqrt(sum / unity.size) shouldBeGreaterThan 0.1
        }
    }
})
