/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.master

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.cylinders.Cylinder
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.effects.Reverb
import io.peekandpoke.klang.audio_be.engines.PipelineRegistry
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.ignitor.PhasePools
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.ignitor.registerDefaults
import io.peekandpoke.klang.audio_be.voices.PlaybackCtx
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.VoiceFactory
import io.peekandpoke.klang.audio_bridge.MasterDsl
import io.peekandpoke.klang.audio_bridge.MasterStageDsl
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.constants.DELAY_CAP
import io.peekandpoke.klang.audio_bridge.constants.DELAY_FEEDBACK
import io.peekandpoke.klang.audio_bridge.constants.DELAY_TIME_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.REVERB_SIZE
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * One set of send-effect defaults on every surface (`constants/SendEffectDefaults.kt`, maintainer
 * 2026-09-16): an orbit voice that touches the delay or the reverb gets, for every slot it leaves
 * unset, exactly what an unconfigured master stage gets. A voice that does not touch an effect
 * sends nothing and configures nothing.
 *
 * Both sides go through their real production path, `VoiceFactory` + `Cylinder` for the orbit and
 * `MasterChain.build` for the master, so a default that drifts on either side fails here.
 *
 * **Where the two halves now live (Katalyst step 5b-1, 2026-09-19), and what is left here.** The
 * orbit's STAGE reads the slot state; since step 5b-2 the `wet` slot is the amount too (the stage is
 * fed from the orbit mix), and the voice FIELDS have no reader on the bus at all, so the rows that
 * read a field's send amount went with that step. The rows about the DSP write the slots by hand.
 * That costs this file half of its old subject, honestly stated: a row that writes
 * `DELAY_TIME_SECONDS` into the map and then reads it back out of the delay line does NOT pin "the
 * door's fill equals the master's default" any more, it pins "the orbit and the master build the
 * same DSP from the same number", which is still worth having (either host could convert or clamp
 * differently, and the reverb's `normalizeSize` is exactly such a conversion).
 *
 * The half that left is pinned in two other places, and this is the pointer:
 *
 *  - **what the DOOR fills** is `sprudel`'s `LangKatalystParamSpec`, which reads the slots a
 *    `reverb(0.4)` writes and compares them against `REVERB_SIZE` and `DELAY_TIME_SECONDS`;
 *  - **that the CHAIN's own defaults are those same constants** is `audio_bridge`'s
 *    `KatalystDefaultsSyncSpec`.
 *
 * The "unset" that reaches the orbit is therefore an ABSENT slot, not a non-finite number.
 *
 * **One asymmetry, recorded rather than asserted away**: a non-finite `delay.time` or
 * `reverb.size` in the slot state is the DECLARED OFF state on the orbit (a bus knob's non-finite
 * value is "never set", and an unset gate is off), while the master substitutes the shared
 * constant for it (`MasterChain.buildDelay`'s `finite(...)`). Sprudel reaches neither: its doors
 * fill with numbers. The last row states it as it is.
 */
class SendEffectDefaultsParitySpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    fun voiceOf(data: VoiceData): Voice {
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

        return factory.makeVoice(
            scheduled = ScheduledVoice(
                playbackId = "test",
                data = data.copy(freqHz = 440.0, sound = "triangle"),
                startTime = 0.0,
                gateEndTime = 1.0,
                playbackStartTime = 0.0,
            ),
            backendStartTimeSec = 0.0,
            playbackCtx = PlaybackCtx(playbackId = "test", ignitorRegistry = registry, phasePools = PhasePools(Random(1))),
            getSample = { null },
        ) ?: error("makeVoice returned null")
    }

    /** The orbit's DSP after one voice claimed it, through the cylinder that writes the DSP. */
    fun orbitOf(data: VoiceData): Pair<Voice, Cylinder> {
        val voice = voiceOf(data)
        val cylinder = Cylinder(id = 0, blockFrames = blockFrames, sampleRate = sampleRate)
        cylinder.updateFromVoice(voice, blockStart = 0.0)

        return voice to cylinder
    }

    fun masterOf(stage: MasterStageDsl) = MasterChain.build(
        dsl = MasterDsl.of(stage),
        sampleRate = sampleRate,
        blockFrames = blockFrames,
    )

    /**
     * Renders 30 blocks of the same two-partial input through the orbit's chain (the cylinder the
     * voice configured) and through the master chain, and demands the same bits, plus a return that
     * is really there.
     */
    fun renderParity(cylinder: Cylinder, master: MasterChain) {
        val bus = StereoBuffer(blockFrames)
        var returned = 0.0

        for (block in 0 until 30) {
            for (i in 0 until blockFrames) {
                val n = (block * blockFrames + i).toDouble()
                val x = 0.3 * sin(2.0 * PI * 440.0 * n / sampleRate) + 0.2 * sin(2.0 * PI * 97.0 * n / sampleRate)

                cylinder.mixBuffer.left[i] = x
                cylinder.mixBuffer.right[i] = x * 0.5
                bus.left[i] = x
                bus.right[i] = x * 0.5
            }

            cylinder.processEffects()
            master.process(bus, blockFrames)

            for (i in 0 until blockFrames) {
                withClue("block $block frame $i") {
                    cylinder.mixBuffer.left[i].toRawBits() shouldBe bus.left[i].toRawBits()
                    cylinder.mixBuffer.right[i].toRawBits() shouldBe bus.right[i].toRawBits()
                }

                val n = (block * blockFrames + i).toDouble()
                val x = 0.3 * sin(2.0 * PI * 440.0 * n / sampleRate) + 0.2 * sin(2.0 * PI * 97.0 * n / sampleRate)

                returned = maxOf(returned, abs(bus.left[i] - x))
            }
        }

        withClue("not two dry signals: the stage returned something, largest $returned") {
            (returned > 1e-3) shouldBe true
        }
    }

    // ── Delay ────────────────────────────────────────────────────────────────────────────────────

    "delay: the orbit and the master build the same line from the same three numbers" {
        // What `.delay(0.4)` puts on the wire: the amount in the field, and in the slot state
        // the named knob plus the three companions the door fills from the shared constants. The
        // MASTER side is the oracle: its stage reads the same constants through its own code, so a
        // literal that drifted into either host fails here.
        val (_, cylinder) = orbitOf(
            VoiceData.empty.copy(
                delay = 0.4,
                katalystParams = mapOf(
                    "delay.wet" to 0.4,
                    "delay.time" to DELAY_TIME_SECONDS,
                    "delay.feedback" to DELAY_FEEDBACK,
                    "delay.cap" to DELAY_CAP,
                ),
            )
        )
        val master = masterOf(MasterStageDsl.Delay(wet = 0.4)).delays.firstOrNull().shouldNotBeNull()

        val orbit = cylinder.delay!!.delayLine.shouldNotBeNull()
        orbit.time shouldBe master.time
        orbit.feedback shouldBe master.feedback
        orbit.cap shouldBe master.cap
    }

    "delay: a slot state that names ONLY the send leaves the orbit's line off" {
        // The other half of the rule, and the reason the row above writes four slots: filling is
        // the DOOR's job (`/dsl-design` §4). A raw `katp("delay.wet", 0.4)` writes one slot, and
        // the classic chain's own `delay.time` default is the untouched voice's 0.0, so nothing
        // runs. Documented in `docs/tasks/katalyst-dsl.md` §1 since step 1.
        val (_, cylinder) = orbitOf(
            VoiceData.empty.copy(delay = 0.4, katalystParams = mapOf("delay.wet" to 0.4))
        )

        cylinder.delay!!.delayLine.shouldBeNull()
    }

    "delay: a non-finite TIME is off on the orbit and the constant on the master, the one asymmetry" {
        // Recorded, not asserted away (see the class KDoc). A bus SLOT's non-finite value is the
        // declared "never set", and an unset gate is off, which is what lets the classic chain
        // express "no line" at all; the master stage has no such state and
        // substitutes. Neither door can produce it: both fill with numbers.
        val (_, cylinder) = orbitOf(
            VoiceData.empty.copy(
                delay = 0.4,
                katalystParams = mapOf("delay.wet" to 0.4, "delay.time" to Double.POSITIVE_INFINITY),
            )
        )
        val master = masterOf(
            MasterStageDsl.Delay(wet = 0.4, time = Double.POSITIVE_INFINITY),
        ).delays.firstOrNull().shouldNotBeNull()

        withClue("the orbit reads it as the off state") { cylinder.delay!!.delayLine.shouldBeNull() }
        withClue("the master substitutes the shared constant") { master.time shouldBe DELAY_TIME_SECONDS }
    }

    "delay: a voice that does not touch it leaves the orbit's delay off" {
        val (_, cylinder) = orbitOf(VoiceData.empty)

        cylinder.delay!!.delayLine.shouldBeNull()
    }

    "delay: the same wet feeds the same echo on both buses, sample for sample" {
        // The amount's parity, now that it is the orbit's too (step 5b-2): both stages copy their
        // bus into a feed scaled by `wet` and let the shared `DelayLine` add its return. A PARITY
        // LAW between two production paths, like the rest of this file, not an identity against a
        // hand-built line (that is `KatalystInsertFeedSpec`).
        val wet = 0.4
        val (_, cylinder) = orbitOf(
            VoiceData.empty.copy(
                katalystParams = mapOf(
                    "delay.wet" to wet,
                    "delay.time" to 0.01,
                    "delay.feedback" to DELAY_FEEDBACK,
                    "delay.cap" to DELAY_CAP,
                ),
            )
        )
        val master = masterOf(MasterStageDsl.Delay(wet = wet, time = 0.01))

        renderParity(cylinder, master)
    }

    // ── Reverb ───────────────────────────────────────────────────────────────────────────────────

    "reverb: the orbit and the master build the same room from the same authored size" {
        // The delay row's twin: what `.reverb(0.4)` writes, the named knob plus the filled size.
        val (_, cylinder) = orbitOf(
            VoiceData.empty.copy(
                reverb = 0.4,
                katalystParams = mapOf("reverb.wet" to 0.4, "reverb.size" to REVERB_SIZE),
            )
        )
        val master = masterOf(MasterStageDsl.Reverb(wet = 0.4)).reverbs.firstOrNull().shouldNotBeNull()

        cylinder.reverb!!.reverb.shouldNotBeNull().size shouldBe master.size
    }

    "reverb: a non-finite SIZE slot, and the two answers the orbit gives it" {
        // The asymmetry's reverb half, and it has a second half of its own, so both are written
        // down rather than folded: the orbit's slot goes through `Reverb.normalizeSize`, which
        // guards NaN (to 0.0, the off state) and CLAMPS +Infinity (to 1.0, the biggest room
        // there is). The master substitutes the shared constant for both.
        fun orbitReverbFor(size: Double) = orbitOf(
            VoiceData.empty.copy(
                reverb = 0.4,
                katalystParams = mapOf("reverb.wet" to 0.4, "reverb.size" to size),
            )
        ).second.reverb!!.reverb

        withClue("NaN is the off state") { orbitReverbFor(Double.NaN).shouldBeNull() }

        withClue("+Infinity clamps to the largest room, it does not read as unset") {
            orbitReverbFor(Double.POSITIVE_INFINITY).shouldNotBeNull().size shouldBe 1.0
        }

        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY)) {
            val master = masterOf(MasterStageDsl.Reverb(wet = 0.4, size = bad))
                .reverbs.firstOrNull().shouldNotBeNull()

            withClue("the master substitutes the shared constant for $bad") {
                master.size shouldBe Reverb.normalizeSize(REVERB_SIZE)
            }
        }
    }

    "reverb: a voice that does not touch it leaves the orbit's reverb off" {
        val (_, cylinder) = orbitOf(VoiceData.empty)

        cylinder.reverb!!.reverb.shouldBeNull()
    }

    "reverb: the same wet feeds the same room on both buses, sample for sample" {
        // The delay row's twin.
        val wet = 0.4
        val (_, cylinder) = orbitOf(
            VoiceData.empty.copy(katalystParams = mapOf("reverb.wet" to wet, "reverb.size" to REVERB_SIZE)),
        )
        val master = masterOf(MasterStageDsl.Reverb(wet = wet))

        renderParity(cylinder, master)
    }
})
