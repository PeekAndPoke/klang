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
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createVoice
import io.peekandpoke.klang.audio_bridge.AdsrDef
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * `gain` is the one level word on the wire (signal-flow plan section 6): the channel fader, applied
 * once, with pan, in the send stage. Three things are guarded here.
 *
 * **At the factory.** A non-finite gain reads as UNSET, 1.0, like every other wire number. It is
 * not a clamp: a negative gain and a gain above 1 are legal and pass through raw (the Motor stays
 * raw).
 *
 * **At the send stage, absolutely.** One row renders a voice whose ignitor's samples the TEST knows
 * (`TestIgnitors.ramp`) and compares BOTH mix channels and the delay send bus against those samples
 * times the pan law computed here, by raw bits. Neither side of that comparison comes from the code
 * under test, so a constant trim in a stage THIS voice renders moves the bits: the amp VCA and the
 * send stage itself (the retired second multiplier at any value but 1, a stray `* 0.9`, a gain
 * dropped from one channel or from the send write). Its reach stops there: a trim inside a stage
 * only a factory-built voice instantiates, a filter or a waveshaper, is not in this voice's
 * pipeline and this row cannot see it. Nor can any bit comparison see a second multiplier at
 * EXACTLY unity; nothing here claims to catch that.
 *
 * **Through the whole path.** Two rows render `VoiceData -> VoiceFactory -> Voice -> render`, so the
 * finite guard is in the rendered path: a NaN gain must render bit for bit what an unset gain
 * renders, and a gain of 0.5 must render exactly half of it (0.5 is a power of two, so the product
 * is exact whichever way the multiplication associates).
 *
 * **The two readers the guard exists for.** `Voice.heard` starts latched on a gain of exactly 0 and
 * `NaN == 0.0` is false, so a NaN voice used to start unlatched; and `SendRenderer.measurePeak`
 * scales the block peak by `abs(gain)`, so a NaN gain made the peak NaN, which fails every compare
 * against the cull floor and reads as audible forever. Both are asserted through the one public
 * seam they have, [Voice.culled].
 */
class VoiceGainWireSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    fun voiceOf(data: VoiceData, gateEndTime: Double = 1.0): Voice {
        val registry = IgnitorRegistry().apply { registerDefaults() }
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
            data = data,
            startTime = 0.0,
            gateEndTime = gateEndTime,
            playbackStartTime = 0.0,
        )

        return factory.makeVoice(
            scheduled = scheduled,
            backendStartTimeSec = 0.0,
            playbackCtx = PlaybackCtx(
                playbackId = "test",
                ignitorRegistry = registry,
                phasePools = PhasePools(Random(1)),
            ),
            getSample = { null },
        ) ?: error("makeVoice returned null")
    }

    fun wireGain(gain: Double?): Double =
        voiceOf(VoiceData.empty.copy(freqHz = 440.0, sound = "triangle", gain = gain)).gain

    /**
     * One block of the cylinder's three left-hand buses, rendered from the given [voice]:
     * the mix bus left, the mix bus right and the delay send bus left, in that order.
     *
     * All three come from ONE render, so the send row cannot disagree with the mix row about
     * which block it is looking at.
     */
    fun renderBuses(voice: Voice): Triple<DoubleArray, DoubleArray, DoubleArray> {
        val ctx = createContext(blockStart = 0.0, blockFrames = blockFrames, sampleRate = sampleRate)

        voice.render(ctx)

        val cylinder = ctx.cylinders.getOrInit(voice.cylinderId, voice, 0.0)

        return Triple(
            cylinder.mixBuffer.left.copyOf(),
            cylinder.mixBuffer.right.copyOf(),
            cylinder.delaySendBuffer.left.copyOf(),
        )
    }

    /** One block of the cylinder's left mix bus, rendered from the given [voice]. */
    fun renderLeft(voice: Voice): DoubleArray = renderBuses(voice).first

    /**
     * A hand-built voice whose only settings are its gain, its pan and its delay send, on the ramp
     * ignitor. Its pipeline is the helper's: a transparent filter chain and the amp VCA, then the
     * send stage, which is what the absolute rows below are about.
     *
     * The rows use POSITIVE gains on purpose. A negative gain would fail them at frame 0 for a
     * reason that has nothing to do with the level: the ramp's first sample is `0.0`, so the oracle
     * computes `0.0 * -g` and gets `-0.0`, while the engine adds its `-0.0` into a zeroed bus and
     * `0.0 + -0.0` is `+0.0`. That a negative gain reaches the voice unclamped is asserted at the
     * factory instead, where no summation is in the way.
     */
    fun rampVoice(gain: Double, pan: Double, delayAmount: Double = 0.0): Voice = createVoice(
        startFrame = 0.0,
        endFrame = 10_000.0,
        gateEndFrame = 10_000.0,
        sampleRate = sampleRate,
        blockFrames = blockFrames,
        gain = gain,
        pan = pan,
        delay = Voice.Delay(amount = delayAmount, time = 0.0, feedback = 0.0),
        signal = TestIgnitors.ramp,
    )

    /** A factory-built voice from the wire, percussive so its release is silent. */
    fun factoryVoice(gain: Double?): Voice = voiceOf(
        VoiceData.empty.copy(
            freqHz = 440.0,
            sound = "triangle",
            gain = gain,
            adsr = AdsrDef.Std(attack = 0.0, decay = 0.0005, sustain = 0.0, release = 0.05),
            cull = 0.0,
        ),
        gateEndTime = 0.02,
    )

    /** Renders block by block; returns the block index the voice was culled on, or -1. */
    fun cullBlock(voice: Voice, blocks: Int): Int {
        val ctx = createContext(blockStart = 0.0, blockFrames = blockFrames, sampleRate = sampleRate)

        for (b in 0 until blocks) {
            ctx.blockStart = (b * blockFrames).toDouble()
            voice.render(ctx)

            if (voice.culled) {
                return b
            }
        }

        return -1
    }

    // ── The factory's finite guard, and the raw values it must not touch ──────────────────────

    "an unset gain reads as 1.0" {
        wireGain(null) shouldBe 1.0
    }

    "a NaN gain reads as unset" {
        wireGain(Double.NaN) shouldBe 1.0
    }

    "a +Infinity gain reads as unset" {
        wireGain(Double.POSITIVE_INFINITY) shouldBe 1.0
    }

    "a -Infinity gain reads as unset" {
        wireGain(Double.NEGATIVE_INFINITY) shouldBe 1.0
    }

    "a negative gain passes through raw" {
        wireGain(-0.75) shouldBe -0.75
    }

    "a gain above 1 passes through raw" {
        wireGain(3.5) shouldBe 3.5
    }

    "a gain of exactly 0 passes through raw" {
        wireGain(0.0) shouldBe 0.0
    }

    // ── The send stage, against an oracle the code under test did not produce ─────────────────

    "both mix channels and the delay send are the ignitor's own samples times pan and gain" {
        // What the ramp ignitor writes, by its own definition: (i - offset) / length.
        val expected = DoubleArray(blockFrames) { it.toDouble() / blockFrames }
        val pan = 0.35
        val panAngle = pan * (PI / 2.0)
        val gain = 0.625
        val delayAmount = 0.25

        // Why these are raw-bit comparisons and not approximations: the oracle GROUPS the product
        // the way `SendRenderer` does, `sample * (panLaw * gain)` and then `* delayAmount` on the
        // already-panned value. Two properties of this voice are load-bearing for that, and both
        // would have to be re-checked if they changed: `gainMultiplier` is 1.0 (no scheduler has
        // touched this voice), and the helper's always-on envelope makes the amp VCA exactly
        // `x * 1.0` (sustain 1.0, the de-click smoother primed at the first rendered gain), so the
        // sample reaching the send stage IS the ignitor's. Being exact in binary, 0.625 and 0.25
        // only keep the oracle readable; they are not what makes the bits agree.
        val (mixLeft, mixRight, delayLeft) = renderBuses(
            rampVoice(gain = gain, pan = pan, delayAmount = delayAmount)
        )

        withClue("not-silence floor: the ramp must actually reach every bus") {
            mixLeft.maxOf { abs(it) } shouldBeGreaterThan 0.1
            mixRight.maxOf { abs(it) } shouldBeGreaterThan 0.1
            delayLeft.maxOf { abs(it) } shouldBeGreaterThan 0.01
        }

        withClue("engagement: a different gain must not render the same buses") {
            val other = renderBuses(rampVoice(gain = 1.0, pan = pan, delayAmount = delayAmount))

            other.first.toList() shouldNotBe mixLeft.toList()
            other.second.toList() shouldNotBe mixRight.toList()
            other.third.toList() shouldNotBe delayLeft.toList()
        }

        withClue("the two channels must not be the same signal") {
            mixRight.toList() shouldNotBe mixLeft.toList()
        }

        for (i in 0 until blockFrames) {
            val left = expected[i] * (cos(panAngle) * gain)

            withClue("mix left, frame $i") {
                mixLeft[i].toRawBits() shouldBe left.toRawBits()
            }

            withClue("mix right, frame $i") {
                mixRight[i].toRawBits() shouldBe (expected[i] * (sin(panAngle) * gain)).toRawBits()
            }

            // The send is taken from the PANNED and GAINED value, not from the raw signal:
            // `delaySendL[idx] + left * delayAmount` into a zeroed bus.
            withClue("delay send left, frame $i") {
                delayLeft[i].toRawBits() shouldBe (left * delayAmount).toRawBits()
            }
        }
    }

    // ── The whole path: VoiceData -> VoiceFactory -> Voice -> render ──────────────────────────

    "a NaN gain from the wire renders exactly what an unset gain renders" {
        val reference = renderLeft(factoryVoice(null))
        val poisoned = renderLeft(factoryVoice(Double.NaN))

        withClue("not-silence floor: the reference must actually carry signal") {
            reference.maxOf { abs(it) } shouldBeGreaterThan 0.01
        }

        for (i in 0 until blockFrames) {
            withClue("frame $i") {
                poisoned[i].toRawBits() shouldBe reference[i].toRawBits()
            }
        }
    }

    "a gain of 0.5 from the wire renders exactly half of the unset-gain voice" {
        val reference = renderLeft(factoryVoice(null))
        val halved = renderLeft(factoryVoice(0.5))

        withClue("engagement: gain 0.5 must not render what an unset gain renders") {
            halved.toList() shouldNotBe reference.toList()
        }

        for (i in 0 until blockFrames) {
            withClue("frame $i") {
                halved[i].toRawBits() shouldBe (reference[i] * 0.5).toRawBits()
            }
        }
    }

    // ── The two readers the guard exists for, through Voice.culled ────────────────────────────

    "a gain of 0 starts the heard latch, so a voice that can never sound is culled" {
        val muted = createVoice(
            startFrame = 0.0,
            gateEndFrame = blockFrames.toDouble(),
            endFrame = (blockFrames * 8).toDouble(),
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            gain = 0.0,
            cull = 0.0,
            signal = TestIgnitors.silence,
        )
        val late = createVoice(
            startFrame = 0.0,
            gateEndFrame = blockFrames.toDouble(),
            endFrame = (blockFrames * 8).toDouble(),
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            gain = 1.0,
            cull = 0.0,
            signal = TestIgnitors.silence,
        )

        withClue("gain 0: latched at construction, so the silent release is culled") {
            cullBlock(muted, blocks = 8) shouldBe 1
        }

        withClue("gain 1: never heard, so it is late rather than silent and is not culled") {
            cullBlock(late, blocks = 8) shouldBe -1
        }
    }

    "a NaN gain from the wire is culled like an unset one, so the cull peak stayed usable" {
        val reference = cullBlock(factoryVoice(null), blocks = 64)
        val poisoned = cullBlock(factoryVoice(Double.NaN), blocks = 64)

        withClue("the reference must actually cull, or the row proves nothing") {
            reference shouldBeGreaterThan 0
        }

        poisoned shouldBe reference
    }
})
