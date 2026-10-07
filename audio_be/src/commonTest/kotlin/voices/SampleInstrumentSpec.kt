/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.audio_be.SampleStore
import io.peekandpoke.klang.audio_be.cylinders.offerAndCommit
import io.peekandpoke.klang.audio_be.ignitor.IgniteContext
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.ignitor.PhasePools
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.ignitor.registerDefaults
import io.peekandpoke.klang.audio_be.ignitor.toExciter
import io.peekandpoke.klang.audio_bridge.AdsrDef
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.MonoSamplePcm
import io.peekandpoke.klang.audio_bridge.SampleMetadata
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.optimize
import kotlin.random.Random

/** This file's one seeded stream: every run draws the same, and successive builds still draw
 *  differently (as they did from the process-wide stream these calls used before). */
private val testRandom = Random(0x5EED)

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
 * with the same settings (stated as test-local `DoorFields`, sent as slots through `withClassicSlots`), and compares the left mix bus in raw bits, at 48 kHz and 44.1 kHz, with a
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
        val source = IgnitorDsl.Sine(freq = IgnitorDsl.Freq).toExciter(random = testRandom)
        val size = frames + blockFrames
        val out = DoubleArray(size)
        val buffer = DoubleArray(blockFrames)
        val ctx = IgniteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = size,
            gateEndFrame = size,
            scratchBuffers = ScratchBuffers(blockFrames),
            random = Random(1),
        )
        var pos = 0

        while (pos < size) {
            val length = minOf(blockFrames, size - pos)

            ctx.updateOffsetAndLength(offset = 0, length = length)
            source.generate(buffer, 220.0, ctx)
            buffer.copyInto(destination = out, destinationOffset = pos, startIndex = 0, endIndex = length)
            pos += length
        }

        return out
    }

    /** Renders one voice through the real [VoiceFactory]; the voice and the left mix bus. */
    fun render(
        data: VoiceData,
        sampleRate: Int,
        pcm: MonoSamplePcm,
        gate: Double = gateSec,
        doors: DoorFields = DoorFields(),
    ): Pair<Voice, DoubleArray> {
        val factory = VoiceFactory(
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            voiceBuffer = DoubleArray(blockFrames),
            freqModBuffer = DoubleArray(blockFrames),
            scratchBuffers = ScratchBuffers(blockFrames),
        )
        val start = onsetFrame.toDouble() / sampleRate
        val voice = factory.makeVoice(
            scheduled = ScheduledVoice(
                playbackId = "test",
                // Every voice here is a tree (the sample instrument, a built-in): its settings travel as slots (step 8).
                data = data.withClassicSlots(doors),
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

            val cylinder = ctx.cylinders.offerAndCommit(voice.cylinderId, voice, 0.0)

            cylinder.mixBuffer.left.copyInto(destination = out, destinationOffset = block * blockFrames, startIndex = 0, endIndex = blockFrames)
            cylinder.mixBuffer.left.fill(0.0)
            cylinder.mixBuffer.right.fill(0.0)
        }

        return voice to out
    }

    /** Where a sample voice's gate ends: the end of one with a zero release. */
    fun gateEnd(sampleRate: Int, pcm: MonoSamplePcm): Double =
        render(VoiceData.empty.copy(freqHz = 220.0, sound = "probe"), sampleRate, pcm, doors = DoorFields(adsr = DoorAdsr(release = 0.0))).first.endFrame

    fun firstMismatch(a: DoubleArray, b: DoubleArray): Int =
        a.indices.firstOrNull { a[it].toRawBits() != b[it].toRawBits() } ?: -1

    val base = VoiceData.empty.copy(freqHz = 220.0)
    val env = DoorFilterEnv(attack = 0.01, decay = 0.1, sustain = 0.3, release = 0.1, depth = 24.0)

    /** One row per `classic()` stage plus the two the built-in shape places on the source. */
    val rows: List<Triple<String, VoiceData, DoorFields>> = listOf(
        Triple("crush", base, DoorFields(crush = 4.0)),
        Triple("coarse", base, DoorFields(coarse = 3.0)),
        Triple("distort", base, DoorFields(distort = 0.6, distortShape = "tube")),
        Triple("highpass", base, DoorFields(filters = listOf(DoorFilter.HighPass(freq = 900.0, q = 1.2)))),
        Triple("bandpass", base, DoorFields(filters = listOf(DoorFilter.BandPass(freq = 300.0, q = 2.0)))),
        Triple("notch", base, DoorFields(filters = listOf(DoorFilter.Notch(freq = 220.0, q = 1.0)))),
        Triple("lowpass with its envelope", base, DoorFields(filters = listOf(DoorFilter.LowPass(freq = 150.0, q = 1.5, envelope = env)))),
        Triple("tremolo", base, DoorFields(tremoloDepth = 0.7, tremoloRate = 6.0, tremoloShape = "square")),
        Triple("adsr", base, DoorFields(adsr = DoorAdsr(attack = 0.03, decay = 0.05, sustain = 0.4, release = 0.1))),
        Triple("adsrOff (the teardown fade)", base, DoorFields(adsr = DoorAdsr(on = false))),
        Triple("onepole", base.copy(ignitorParams = mapOf("onepole" to 400.0)), DoorFields()),
        Triple("pregain into distort", base.copy(ignitorParams = mapOf("pregain" to 1.7)), DoorFields(distort = 0.3)),
    )

    for (sampleRate in listOf(48000, 44100)) {
        val pcm = MonoSamplePcm(sampleRate = sampleRate, pcm = sinePcm(sampleRate), meta = SampleMetadata.default)
        val (_, plainBuiltIn) = render(base.copy(sound = "sine"), sampleRate, pcm)
        val (_, plainSample) = render(base.copy(sound = "probe"), sampleRate, pcm)

        "[$sampleRate Hz] the untouched sample voice is the untouched built-in, bit for bit" {
            withClue("not silent") { plainSample.any { it != 0.0 } shouldBe true }
            withClue("first mismatching frame") { firstMismatch(a = plainSample, b = plainBuiltIn) shouldBe -1 }
        }

        for ((title, data, doors) in rows) {
            "[$sampleRate Hz] $title: the sample voice is the built-in, bit for bit" {
                val (_, builtIn) = render(data.copy(sound = "sine"), sampleRate, pcm, doors = doors)
                val (_, sample) = render(data.copy(sound = "probe"), sampleRate, pcm, doors = doors)

                withClue("engaged: the setting changes the sound") { firstMismatch(a = sample, b = plainSample) shouldNotBe -1 }
                withClue("first mismatching frame") { firstMismatch(a = sample, b = builtIn) shouldBe -1 }
            }
        }

        // A SoundFont zone's transparent envelope: attack 0, decay 0, sustain 1, release 0.05.
        val metaEnvelope = AdsrDef.Std(attack = 0.0, decay = 0.0, sustain = 1.0, release = 0.05)
        val metaAsDoors = DoorAdsr(attack = 0.0, decay = 0.0, sustain = 1.0, release = 0.05)
        val metaPcm = MonoSamplePcm(
            sampleRate = sampleRate,
            pcm = pcm.pcm,
            meta = SampleMetadata(anchor = 0.0, loop = null, adsr = metaEnvelope),
        )

        "[$sampleRate Hz] a sample's meta envelope fills the envelope the pattern left unset" {
            val (voice, sample) = render(base.copy(sound = "probe"), sampleRate, metaPcm)
            val (_, builtIn) = render(base.copy(sound = "sine"), sampleRate, pcm, doors = DoorFields(adsr = metaAsDoors))

            withClue("engaged: the meta envelope changes the sound") { firstMismatch(a = sample, b = plainSample) shouldNotBe -1 }
            withClue("first mismatching frame") { firstMismatch(a = sample, b = builtIn) shouldBe -1 }
            withClue("the voice lives its gate plus the meta release") {
                voice.endFrame shouldBe gateEnd(sampleRate, pcm) + 0.05 * sampleRate
            }
        }

        "[$sampleRate Hz] the pattern's envelope wins over the sample's meta envelope, stage by stage" {
            val pattern = DoorAdsr(attack = 0.02, release = 0.12)
            val (voice, sample) = render(base.copy(sound = "probe"), sampleRate, metaPcm, doors = DoorFields(adsr = pattern))
            val merged = DoorAdsr(attack = 0.02, decay = 0.0, sustain = 1.0, release = 0.12)
            val (_, builtIn) = render(base.copy(sound = "sine"), sampleRate, pcm, doors = DoorFields(adsr = merged))

            withClue("first mismatching frame") { firstMismatch(a = sample, b = builtIn) shouldBe -1 }
            withClue("the pattern's release sets the lifetime") {
                voice.endFrame shouldBe gateEnd(sampleRate, pcm) + 0.12 * sampleRate
            }
        }
    }

    "a negative release on a sample plays to its gate: the lifetime is floored at 0" {
        val sampleRate = 48000
        val pcm = MonoSamplePcm(sampleRate = sampleRate, pcm = sinePcm(sampleRate), meta = SampleMetadata.default)
        val (voice, _) = render(base.copy(sound = "probe"), sampleRate, pcm, doors = DoorFields(adsr = DoorAdsr(release = -0.1)))

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
        val long = DoorFields(adsr = DoorAdsr(release = 1.0))
        val depthSlot = (IgnitorDsl.Slots.tremolo.depth as IgnitorDsl.Param).name
        val (plain, _) = render(base.copy(sound = "probe"), sampleRate, pcm, doors = long)
        val (tremolo, _) = render(base.copy(sound = "probe", ignitorParams = mapOf(depthSlot to 0.8)), sampleRate, pcm, doors = long)

        // The tremolo voice's STATE, not only "not culled": a render that ran past its end would also read "not
        // culled", for the wrong reason. Releasing pins that it is still in its release here.
        withClue("engaged: the silent release culls a voice without the tremolo") { plain.culled shouldBe true }
        withClue("the tree's tremolo marks the voice never-cull") { tremolo.state.shouldBeInstanceOf<Voice.State.Releasing>() }
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
        val (voice, _) = render(base.copy(sound = "probe", ignitorParams = mapOf(releaseSlot to Double.NaN)), sampleRate, pcm)

        voice.endFrame shouldBe gateEnd(sampleRate, pcm) + 0.2 * sampleRate
    }
})
