/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.master

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.cylinders.Cylinder
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystReverbEffect
import io.peekandpoke.klang.audio_be.effects.Reverb
import io.peekandpoke.klang.audio_be.engines.PipelineRegistry
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.ignitor.PhasePools
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.ignitor.registerDefaults
import io.peekandpoke.klang.audio_be.voices.PlaybackCtx
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.VoiceFactory
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers
import io.peekandpoke.klang.audio_bridge.MasterDsl
import io.peekandpoke.klang.audio_bridge.MasterStageDsl
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import kotlin.math.abs
import kotlin.random.Random

/**
 * **The guard for the bug this whole change exists to fix.**
 *
 * The shared Freeverb is driven from two hosts — the per-orbit bus and the master bus. For a while
 * the same authored number meant different things on each: sprudel divided `roomsize` by 10, the
 * master did not, so `roomSize(3)` was a ~1 s tail on an orbit and a ~12.5 s one on the master (it
 * clamped to the maximum). Nothing compared the two, so nothing noticed. Since 2026-09-16 both doors
 * also share the knob names: `reverb(size, lowpass)` on an orbit, `r.size().lowpass()` on the master.
 *
 * Both sides here go through their **real** production path — `VoiceFactory` for the orbit,
 * `MasterChain.build` for the master — so a regression in either one fails this spec.
 */
class MasterOrbitReverbParitySpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    /** The Freeverb the ORBIT path configures for a given authored size and lowpass. */
    fun orbitReverb(authored: Double, lowpass: Double? = null): Reverb {
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

        val voice = factory.makeVoice(
            scheduled = ScheduledVoice(
                playbackId = "test",
                data = VoiceData.empty.copy(
                    freqHz = 440.0,
                    sound = "triangle",
                    // The orbit's reverb stage reads the SLOTS (Katalyst step 5b-1), on the same
                    // authored 0-to-10 scale the master stage takes, which is the whole point of
                    // this spec.
                    katalystParams = buildMap {
                        put("reverb.wet", 0.5)
                        put("reverb.size", authored)
                        lowpass?.let { put("reverb.lowpass", it) }
                    },
                ),
                startTime = 0.0,
                gateEndTime = 1.0,
                playbackStartTime = 0.0,
            ),
            backendStartTimeSec = 0.0,
            playbackCtx = PlaybackCtx(playbackId = "test", ignitorRegistry = registry, phasePools = PhasePools(Random(1))),
            getSample = { null },
        ) ?: error("makeVoice returned null")

        // Valid for authored >= 0.1 ONLY: below that the orbit door reads the config as OFF and
        // never writes the DSP, so this helper would return the previous/default value, not the
        // normalized one (drain lifecycle, review round 3).
        // ...and then through the cylinder, which is what actually writes the DSP. Reading
        // `voice.reverb.size` here would stop one step short and miss a second /10 introduced
        // in `Cylinder` — exactly the class of bug this spec exists to catch.
        val cylinder = Cylinder(id = 0, blockFrames = blockFrames, sampleRate = sampleRate)
        cylinder.updateFromVoice(voice, blockStart = 0.0)

        return cylinder.reverb!!.reverb!!
    }

    /** The master chain for one reverb stage with the given authored size and lowpass. */
    fun masterChain(size: Double, lowpass: Double? = null) = MasterChain.build(
        dsl = MasterDsl.of(MasterStageDsl.Reverb(wet = 0.5, size = size, lowpass = lowpass)),
        sampleRate = sampleRate,
        blockFrames = blockFrames,
    )

    /** The Freeverb the MASTER path configures for the same authored size and lowpass. */
    fun masterReverb(size: Double, lowpass: Double? = null) =
        masterChain(size, lowpass).reverbs.firstOrNull().shouldNotBeNull()

    "the same authored size reaches the DSP identically on both buses" {
        listOf(3.0, 5.0, 8.0, 10.0).forEach { authored ->
            masterReverb(authored).size shouldBe orbitReverb(authored).size
        }
    }

    "an authored 3 is the ~1 s tail it reads like, on both buses" {
        // 3 / 10 = 0.3 -> comb feedback 0.784 -> ~1 s. Before the fix the master clamped this to
        // 1.0 (feedback 0.98, ~12.5 s) — the reported symptom.
        masterReverb(3.0).size shouldBe 0.3
        orbitReverb(3.0).size shouldBe 0.3
    }

    "both buses apply the same bound at authored 10" {
        // Normalized 1.0 is comb feedback 0.98, canonical Freeverb's top, kept deliberately
        // (maintainer, 2026-09-16, see `Reverb.normalizeSize`); unity feedback would sit at about
        // 10.71. What matters for parity is that BOTH buses agree on where the bound is.
        masterReverb(30.0).size shouldBe 1.0
        orbitReverb(30.0).size shouldBe 1.0
    }

    "the same lowpass reaches the DSP identically on both buses" {
        masterReverb(size = 8.0, lowpass = 3500.0).lowpass shouldBe 3500.0
        orbitReverb(8.0, lowpass = 3500.0).lowpass shouldBe 3500.0

        masterReverb(size = 8.0).lowpass shouldBe null
        orbitReverb(8.0).lowpass shouldBe null
    }

    "a non-finite lowpass is UNSET on both buses" {
        masterReverb(size = 3.0, lowpass = Double.POSITIVE_INFINITY).lowpass shouldBe null
        masterReverb(size = 3.0, lowpass = Double.NaN).lowpass shouldBe null

        val orbit = KatalystReverbEffect(Reverb(sampleRate), blockFrames)
        // A finite lowpass first, so the +Inf outcome is provably "unset", not a fresh default.
        orbit.configure(size = 0.5, lowpass = 3000.0, wet = 1.0)
        orbit.reverb!!.lowpass shouldBe 3000.0

        orbit.configure(size = 0.5, lowpass = Double.POSITIVE_INFINITY, wet = 1.0)
        orbit.reverb!!.lowpass shouldBe null
    }

    "the delay keeps its ceiling — there, unlike the reverb, self-oscillation is real" {
        val chain = MasterChain.build(
            dsl = MasterDsl.of(
                MasterStageDsl.Delay(wet = 0.5, time = 0.25, feedback = 1.0, cap = 3.0)
            ),
            sampleRate = sampleRate,
            blockFrames = blockFrames,
        )

        chain.delays.firstOrNull().shouldNotBeNull().cap shouldBe 3.0
    }

    "both buses switch the reverb on at the same authored size" {
        // Below authored 0.1 (normalized 0.01) the reverb is off on both buses: the comb feedback
        // floor would otherwise ring a 0.7 s tail for a size nobody asked for. Authored 0.1 sits ON
        // the threshold (0.1 / 10 == 0.01 exactly), so a `>` on either gate breaks the agreement.
        fun orbitRenders(authored: Double): Boolean {
            val cylinder = Cylinder(id = 0, blockFrames = blockFrames, sampleRate = sampleRate)
            cylinder.updateFromVoice(
                VoiceTestHelpers.createSynthVoice(
                    blockFrames = blockFrames,
                    // The slot carries the AUTHORED size, exactly as the master stage does, which
                    // is what makes the two gates comparable at all.
                    katalystParams = mapOf("reverb.wet" to 0.6, "reverb.size" to authored),
                ),
                blockStart = 0.0,
            )

            // Feed the mix and look for wet output on top of it. Freeverb's shortest comb is 1116
            // samples, so a single 128-frame block returns nothing no matter what: render past
            // that. Audible wet, not the ~1e-20 anti-denormal residue any processed block carries.
            val ctx = cylinder.katalystContext
            var heard = false

            repeat(30) {
                for (i in 0 until blockFrames) {
                    ctx.mixBuffer.left[i] = 0.5
                    ctx.mixBuffer.right[i] = 0.5
                }
                cylinder.reverb!!.process(ctx)

                if ((0 until blockFrames).any { abs(ctx.mixBuffer.left[it] - 0.5) > 1e-6 }) {
                    heard = true
                }
            }

            return heard
        }

        listOf(0.0 to false, 0.05 to false, 0.1 to true, 0.2 to true, 5.0 to true).forEach { (authored, on) ->
            orbitRenders(authored) shouldBe on
            masterChain(authored).isActive shouldBe on
        }
    }
})
