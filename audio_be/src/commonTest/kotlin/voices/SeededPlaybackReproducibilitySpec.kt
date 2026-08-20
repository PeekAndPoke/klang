/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBackendContext
import io.peekandpoke.klang.audio_be.BackendClock
import io.peekandpoke.klang.audio_be.PlaybackEngine
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.audio_bridge.FilterDefs
import io.peekandpoke.klang.audio_bridge.SampleRequest
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink

/**
 * Seeded-voice-rng (D3c), PRODUCTION wiring: the per-voice streams dealt by VoiceFactory
 * from `PlaybackCtx.coreRandom` must make a full playback render (a) bit-REPRODUCIBLE for
 * the same playback id + note order — including the per-voice filter cutoff tolerance and
 * filter drift, which draw from the voice stream — and (b) DECORRELATED between voices and
 * between different playback ids.
 *
 * The unit-level SeededVoiceRngSpec cannot see this layer (it drives toExciter/IgniteContext
 * directly); these rows go through the real engine, so severing ANY VoiceFactory wiring —
 * the deal itself, the createExciter/IgniteContext threading, the filter rng params — reddens
 * here. The catastrophic-and-silent mutant this spec exists for: a constant per-voice seed,
 * which gives every note the IDENTICAL stream (same unison phases, same drift, phasey
 * coherent sums) with everything else green.
 */
class SeededPlaybackReproducibilitySpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    // Drift-heavy note: analog engages osc drift, unison jitter, the per-voice filter
    // cutoff tolerance AND the filter-modulator drift — every voice-stream consumer family.
    fun noteData(): VoiceData = VoiceData.empty.copy(
        sound = "supersaw",
        oscParams = mapOf("analog" to 3.0),
        filters = FilterDefs(listOf(FilterDef.LowPass(cutoffHz = 2000.0, q = 1.0))),
    )

    val sampleSound = "seedtest"
    val sampleReq = SampleRequest(bank = null, sound = sampleSound, index = null, note = null)

    class Rig(val pid: String) {
        val clock = BackendClock(sampleRate)
        val context = AudioBackendContext.create(
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            commLink = KlangCommLink(capacity = 1024).backend,
            clock = clock,
            // Fixed pool seed: the phase-pool stream ALSO mixes the pid, so without this the
            // different-pid row could stay green with the coreRandom pid mix stripped once
            // pools ever engage — pinning it here makes coreRandom the only pid carrier.
            phasePoolSeed = 1,
        )
        val engine = PlaybackEngine.create(context)
        private val mix = StereoBuffer(blockFrames)

        fun schedule(startSec: Double, data: VoiceData) {
            engine.scheduler.scheduleVoice(
                ScheduledVoice(
                    playbackId = pid,
                    startTime = startSec,
                    gateEndTime = startSec + 0.5,
                    data = data,
                    playbackStartTime = 0.0,
                )
            )
        }

        /** Renders [blocks] and returns the raw LEFT channel (bit-comparable). */
        fun render(blocks: Int): DoubleArray {
            val out = DoubleArray(blocks * blockFrames)
            for (block in 0 until blocks) {
                mix.clear()
                engine.renderInto(mix, clock.cursorFrame)
                for (i in 0 until blockFrames) {
                    out[block * blockFrames + i] = mix.left[i]
                }
                clock.cursorFrame += blockFrames
            }
            return out
        }
    }

    "same playback id + same note order renders bit-identically (drift + filters active)" {
        fun run(): DoubleArray {
            val rig = Rig("song")
            rig.schedule(0.0, noteData())
            rig.schedule(0.1, noteData())
            return rig.render(blocks = 40)
        }
        val a = run()
        val b = run()
        for (i in a.indices) {
            a[i].toRawBits() shouldBe b[i].toRawBits()
        }
    }

    "two simultaneous identical notes DECORRELATE (per-voice deal is live)" {
        // Under a constant per-voice seed, both voices carry the identical stream and the
        // mix is exactly 2x a single voice — the loud, phasey failure this row exists for.
        val double = Rig("song").also {
            it.schedule(0.0, noteData())
            it.schedule(0.0, noteData())
        }.render(blocks = 20)

        val single = Rig("song").also {
            it.schedule(0.0, noteData())
        }.render(blocks = 20)

        var decorrelated = false
        for (i in double.indices) {
            if (double[i].toRawBits() != (2.0 * single[i]).toRawBits()) {
                decorrelated = true
                break
            }
        }
        decorrelated shouldBe true
    }

    "sample voices with wow/flutter are seed-reproducible too" {
        // The SampleIgnitor drift channel (rng ctor param fed from the voice stream): a
        // breakbeat with analog must render identically for the same pid + note order —
        // pre-D3c its wow/flutter came from the global and differed every run.
        fun run(): DoubleArray {
            val rig = Rig("song")
            rig.context.sampleStore.addSample(
                KlangCommLink.Cmd.Sample.Complete(
                    req = sampleReq,
                    note = null,
                    pitchHz = 440.0,
                    // NON-constant PCM on purpose: wow/flutter modulates the playback RATE,
                    // and rate modulation is invisible on DC (reading a constant at any
                    // speed yields the constant) — a sine makes the drift values
                    // output-bearing, so a global-rng mutant genuinely diverges the runs.
                    sample = TestSamples.sine(size = sampleRate, sampleRate = sampleRate),
                )
            )
            rig.schedule(
                0.0,
                VoiceData.empty.copy(sound = sampleSound, oscParams = mapOf("analog" to 3.0)),
            )
            return rig.render(blocks = 20)
        }
        val a = run()
        val b = run()
        for (i in a.indices) {
            a[i].toRawBits() shouldBe b[i].toRawBits()
        }
    }

    "different playback ids decorrelate (coreRandom mixes the pid)" {
        // Two concurrent playbacks of the same song must not sum their doubled notes
        // coherently — the pid is mixed into coreRandom's seed (PhasePools precedent).
        fun run(pid: String): DoubleArray {
            val rig = Rig(pid)
            rig.schedule(0.0, noteData())
            return rig.render(blocks = 20)
        }
        val a = run("songA")
        val b = run("songB")
        var differs = false
        for (i in a.indices) {
            if (a[i].toRawBits() != b[i].toRawBits()) {
                differs = true
                break
            }
        }
        differs shouldBe true
    }
})
