/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_be.SampleStore
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.engines.PipelineRegistry
import io.peekandpoke.klang.audio_be.ignitor.IgniteContext
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.ignitor.PhasePools
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.ignitor.registerDefaults
import io.peekandpoke.klang.audio_be.ignitor.toExciter
import io.peekandpoke.klang.audio_bridge.AdsrDef
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.audio_bridge.FilterDefs
import io.peekandpoke.klang.audio_bridge.FilterEnvDef
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.MonoSamplePcm
import io.peekandpoke.klang.audio_bridge.SampleMetadata
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.optimize
import kotlin.random.Random

/**
 * **The sample instrument (phase 3 step 7): a sample voice IS the built-in shape over its PCM.**
 *
 * The oracle needs no voice strip, so it outlives it: the PCM is the exact output of the plain sine source
 * at the render rate, played back at rate 1.0 (the sample's pitch equals the note's, its rate the engine's),
 * where the playhead's linear interpolation returns each PCM value bit for bit. So the sample's SOURCE is the
 * built-in `sine`'s source, and every row renders the same note twice through the real [VoiceFactory]:
 *
 *  - BUILT-IN: `sound("sine")`, the built-in shape over the sine (`IgnitorRegistry.builtInVoice`);
 *  - SAMPLE: an unregistered name whose sample is that PCM, i.e. the sample instrument,
 *
 * with the same typed fields, and compares the left mix bus in raw bits, at 48 kHz and 44.1 kHz, with a
 * mid-block onset and the gate inside the render. Every row also checks it is ENGAGED (the setting changes
 * the sound), so no row compares two renders of nothing. If a sample still ran the voice strip, every stage
 * would apply twice and every row but the adsrOff rows would part (with the envelope off, the strip's
 * switched-off VCA is the very teardown fade the tree voice runs, so running the one instead of the other
 * renders the same bits).
 */
class SampleInstrumentSpec : StringSpec({

    val blockFrames = 128
    val blocks = 160
    val frames = blocks * blockFrames
    val onsetFrame = 37
    val gateSec = 0.25

    val registry = IgnitorRegistry().apply { registerDefaults() }

    /**
     * The plain sine source rendered raw at [sampleRate] (220 Hz, no drift), long enough to outlast the
     * render. The sine's loop is per sample, so its output does not depend on how the frames are split
     * into blocks.
     */
    fun sinePcm(sampleRate: Int): DoubleArray {
        val source = IgnitorDsl.Sine(freq = IgnitorDsl.Freq).toExciter()
        val size = frames + blockFrames
        val out = DoubleArray(size)
        val buffer = DoubleArray(blockFrames)
        val ctx = IgniteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = size,
            gateEndFrame = size,
            releaseFrames = 0,
            scratchBuffers = ScratchBuffers(blockFrames),
            random = Random(1),
        )
        var pos = 0

        while (pos < size) {
            val length = minOf(blockFrames, size - pos)

            ctx.updateOffsetAndLength(0, length)
            source.generate(buffer, 220.0, ctx)
            buffer.copyInto(out, pos, 0, length)
            pos += length
        }

        return out
    }

    /** Renders one voice through the real [VoiceFactory]; the voice and the left mix bus. */
    fun render(data: VoiceData, sampleRate: Int, pcm: MonoSamplePcm, gate: Double = gateSec): Pair<Voice, DoubleArray> {
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
        val start = onsetFrame.toDouble() / sampleRate
        val voice = factory.makeVoice(
            scheduled = ScheduledVoice(
                playbackId = "test",
                data = data,
                startTime = start,
                gateEndTime = start + gate,
                playbackStartTime = 0.0,
            ),
            backendStartTimeSec = 0.0,
            playbackCtx = PlaybackCtx(playbackId = "test", ignitorRegistry = registry, phasePools = PhasePools(Random(1))),
            getSample = { req -> SampleStore.SampleEntry.Complete(req = req, note = null, pitchHz = 220.0, sample = pcm) },
        ) ?: error("makeVoice returned null for ${data.sound}")

        val ctx = VoiceTestHelpers.createContext(blockStart = 0.0, blockFrames = blockFrames, sampleRate = sampleRate)
        val out = DoubleArray(frames)

        repeat(blocks) { block ->
            ctx.blockStart = (block * blockFrames).toDouble()
            voice.render(ctx)

            val cylinder = ctx.cylinders.getOrInit(voice.cylinderId, voice, 0.0)

            cylinder.mixBuffer.left.copyInto(out, block * blockFrames, 0, blockFrames)
            cylinder.mixBuffer.left.fill(0.0)
            cylinder.mixBuffer.right.fill(0.0)
        }

        return voice to out
    }

    /** Where a sample voice's gate ends: the end of one with a zero release. */
    fun gateEnd(sampleRate: Int, pcm: MonoSamplePcm): Double =
        render(VoiceData.empty.copy(freqHz = 220.0, sound = "probe", adsr = AdsrDef.Std(release = 0.0)), sampleRate, pcm).first.endFrame

    fun firstMismatch(a: DoubleArray, b: DoubleArray): Int =
        a.indices.firstOrNull { a[it].toRawBits() != b[it].toRawBits() } ?: -1

    val base = VoiceData.empty.copy(freqHz = 220.0)
    val env = FilterEnvDef(attack = 0.01, decay = 0.1, sustain = 0.3, release = 0.1, depth = 24.0)

    /** One row per `classic()` stage plus the two the built-in shape places on the source. */
    val rows: List<Pair<String, VoiceData>> = listOf(
        "crush" to base.copy(crush = 4.0),
        "coarse" to base.copy(coarse = 3.0),
        "distort" to base.copy(distort = 0.6, distortShape = "tube"),
        "highpass" to base.copy(filters = FilterDefs(listOf(FilterDef.HighPass(900.0, 1.2)))),
        "bandpass" to base.copy(filters = FilterDefs(listOf(FilterDef.BandPass(300.0, 2.0)))),
        "notch" to base.copy(filters = FilterDefs(listOf(FilterDef.Notch(220.0, 1.0)))),
        "lowpass with its envelope" to base.copy(filters = FilterDefs(listOf(FilterDef.LowPass(150.0, 1.5, envelope = env)))),
        "tremolo" to base.copy(tremoloDepth = 0.7, tremoloSync = 6.0, tremoloShape = "square"),
        "adsr" to base.copy(adsr = AdsrDef.Std(attack = 0.03, decay = 0.05, sustain = 0.4, release = 0.1)),
        "adsrOff (the teardown fade)" to base.copy(adsr = AdsrDef.Std(on = false)),
        "onepole" to base.copy(oscParams = mapOf("onepole" to 400.0)),
        "pregain into distort" to base.copy(distort = 0.3, oscParams = mapOf("pregain" to 1.7)),
    )

    for (sampleRate in listOf(48000, 44100)) {
        val pcm = MonoSamplePcm(sampleRate = sampleRate, pcm = sinePcm(sampleRate), meta = SampleMetadata.default)
        val (_, plainBuiltIn) = render(base.copy(sound = "sine"), sampleRate, pcm)
        val (_, plainSample) = render(base.copy(sound = "probe"), sampleRate, pcm)

        "[$sampleRate Hz] the untouched sample voice is the untouched built-in, bit for bit" {
            withClue("not silent") { plainSample.any { it != 0.0 } shouldBe true }
            withClue("first mismatching frame") { firstMismatch(plainSample, plainBuiltIn) shouldBe -1 }
        }

        for ((title, data) in rows) {
            "[$sampleRate Hz] $title: the sample voice is the built-in, bit for bit" {
                val (_, builtIn) = render(data.copy(sound = "sine"), sampleRate, pcm)
                val (_, sample) = render(data.copy(sound = "probe"), sampleRate, pcm)

                withClue("engaged: the setting changes the sound") { firstMismatch(sample, plainSample) shouldNotBe -1 }
                withClue("first mismatching frame") { firstMismatch(sample, builtIn) shouldBe -1 }
            }
        }

        // A SoundFont zone's transparent envelope: attack 0, decay 0, sustain 1, release 0.05.
        val metaEnvelope = AdsrDef.Std(attack = 0.0, decay = 0.0, sustain = 1.0, release = 0.05)
        val metaPcm = MonoSamplePcm(
            sampleRate = sampleRate,
            pcm = pcm.pcm,
            meta = SampleMetadata(anchor = 0.0, loop = null, adsr = metaEnvelope),
        )

        "[$sampleRate Hz] a sample's meta envelope fills the envelope the pattern left unset" {
            val (voice, sample) = render(base.copy(sound = "probe"), sampleRate, metaPcm)
            val (_, builtIn) = render(base.copy(sound = "sine", adsr = metaEnvelope), sampleRate, pcm)

            withClue("engaged: the meta envelope changes the sound") { firstMismatch(sample, plainSample) shouldNotBe -1 }
            withClue("first mismatching frame") { firstMismatch(sample, builtIn) shouldBe -1 }
            withClue("the voice lives its gate plus the meta release") {
                voice.endFrame shouldBe gateEnd(sampleRate, pcm) + 0.05 * sampleRate
            }
        }

        "[$sampleRate Hz] the pattern's envelope wins over the sample's meta envelope, stage by stage" {
            val pattern = AdsrDef.Std(attack = 0.02, release = 0.12)
            val (voice, sample) = render(base.copy(sound = "probe", adsr = pattern), sampleRate, metaPcm)
            val merged = AdsrDef.Std(attack = 0.02, decay = 0.0, sustain = 1.0, release = 0.12)
            val (_, builtIn) = render(base.copy(sound = "sine", adsr = merged), sampleRate, pcm)

            withClue("first mismatching frame") { firstMismatch(sample, builtIn) shouldBe -1 }
            withClue("the pattern's release sets the lifetime") {
                voice.endFrame shouldBe gateEnd(sampleRate, pcm) + 0.12 * sampleRate
            }
        }
    }

    "a negative release on a sample plays to its gate: the lifetime is floored at 0" {
        val sampleRate = 48000
        val pcm = MonoSamplePcm(sampleRate = sampleRate, pcm = sinePcm(sampleRate), meta = SampleMetadata.default)
        val (voice, _) = render(base.copy(sound = "probe", adsr = AdsrDef.Std(release = -0.1)), sampleRate, pcm)

        voice.endFrame shouldBe gateEnd(sampleRate, pcm)
    }

    // Close to a value echo on purpose: the shape itself is pinned by the render rows above. What this row adds
    // is only that the constant is the OPTIMIZED tree, as every registered tree is.
    "the sample instrument is the OPTIMIZED built-in shape (this row pins only the optimize call)" {
        IgnitorRegistry.SAMPLE_INSTRUMENT shouldBe IgnitorRegistry.builtInVoice(IgnitorDsl.Sample).optimize()
    }

    "a tremolo reached only through its classic slot keeps a sample voice from being culled" {
        // The PCM sounds for 0.1 s and is silent after, so the release (from 0.25 s) is exact silence: an
        // unprotected voice is culled one cull window into it. The tremolo is written ONLY as the slot
        // `tremolo.depth` (no typed field), so only the build can see it (`BuiltIgnitor.gatesOutput`).
        val sampleRate = 48000
        val burst = sinePcm(sampleRate).copyOf()
        burst.fill(0.0, sampleRate / 10, burst.size)
        val pcm = MonoSamplePcm(sampleRate = sampleRate, pcm = burst, meta = SampleMetadata.default)
        val long = AdsrDef.Std(release = 1.0)
        val depthSlot = (IgnitorDsl.Slots.tremolo.depth as IgnitorDsl.Param).name
        val (plain, _) = render(base.copy(sound = "probe", adsr = long), sampleRate, pcm)
        val (tremolo, _) = render(base.copy(sound = "probe", adsr = long, oscParams = mapOf(depthSlot to 0.8)), sampleRate, pcm)

        withClue("engaged: the silent release culls a voice without the tremolo") { plain.culled shouldBe true }
        withClue("the tree's tremolo marks the voice never-cull") { tremolo.culled shouldBe false }
    }

    "a sample draws nothing for a voice strip: a typed filter equals the same filter written as slots" {
        // At analog 2 every filter draws its tolerance and drift from the voice's stream (after the playhead's
        // own lane). A strip filter built for a sample and never run would still draw, and shift the tree's.
        val sampleRate = 48000
        val pcm = MonoSamplePcm(sampleRate = sampleRate, pcm = sinePcm(sampleRate), meta = SampleMetadata.default)
        val lpf = IgnitorDsl.Slots.lpf
        val name = { slot: IgnitorDsl -> (slot as IgnitorDsl.Param).name }
        val typed = base.copy(
            sound = "probe",
            oscParams = mapOf("analog" to 2.0),
            filters = FilterDefs(listOf(FilterDef.LowPass(1200.0, 0.707))),
        )
        val slots = base.copy(
            sound = "probe",
            oscParams = mapOf("analog" to 2.0, name(lpf.freq) to 1200.0, name(lpf.q) to 0.707, name(lpf.passes) to 1.0),
        )
        val (_, unfiltered) = render(base.copy(sound = "probe", oscParams = mapOf("analog" to 2.0)), sampleRate, pcm)
        val (_, viaTyped) = render(typed, sampleRate, pcm)
        val (_, viaSlots) = render(slots, sampleRate, pcm)

        withClue("engaged: the lowpass changes the sound") { firstMismatch(viaSlots, unfiltered) shouldNotBe -1 }
        withClue("first mismatching frame") { firstMismatch(viaTyped, viaSlots) shouldBe -1 }
    }

    "a non-finite envelope slot reads as unset: the sample's meta envelope fills it" {
        // A meta release of 0.2 s, not the voice default (0.05 s), so the two fills are told apart.
        val sampleRate = 48000
        val pcm = MonoSamplePcm(
            sampleRate = sampleRate,
            pcm = sinePcm(sampleRate),
            meta = SampleMetadata(anchor = 0.0, loop = null, adsr = AdsrDef.Std(release = 0.2)),
        )
        val releaseSlot = (IgnitorDsl.Slots.adsr.release as IgnitorDsl.Param).name
        val (voice, _) = render(base.copy(sound = "probe", oscParams = mapOf(releaseSlot to Double.NaN)), sampleRate, pcm)

        voice.endFrame shouldBe gateEnd(sampleRate, pcm) + 0.2 * sampleRate
    }
})
