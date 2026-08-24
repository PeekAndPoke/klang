/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.effects

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.engines.PipelineRegistry
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.ignitor.PhasePools
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.ignitor.registerDefaults
import io.peekandpoke.klang.audio_be.voices.PlaybackCtx
import io.peekandpoke.klang.audio_be.voices.VoiceFactory
import io.peekandpoke.klang.audio_be.filters.NoOpAudioFilter
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.strip.filter.StripPhaserRenderer
import io.peekandpoke.klang.audio_be.voices.strip.filter.buildFilterPipeline
import io.peekandpoke.klang.audio_be.voices.strip.filter.renderInPlace
import io.peekandpoke.klang.audio_bridge.PipelineDsl
import io.peekandpoke.klang.audio_bridge.StageDsl
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.random.Random

/**
 * C4.2 guard (docs/plans/filter-unification.md): the phaser's `floor` knob follows the shared
 * wet/dry law — `dry = max(floor, cos²(w·π/2))` — on BOTH engine paths (cylinder-bus [Phaser]
 * and [StripPhaserRenderer]; one knob, one law), and `phaserFloor` reaches the voice through
 * [VoiceFactory] with the ADDITIVE default `1.0` when absent.
 *
 * Method: the wet path of two phaser instances fed the same input is sample-identical (the
 * allpass state is input-determined), so `outA − outB = (dryC_A − dryC_B) · dry` exactly.
 */
class PhaserFloorLawSpec : StringSpec({

    val frames = 512
    val depth = 0.9
    // cos²(0.9·π/2) ≈ 0.0245 — well below the floors used here, so max() genuinely engages.
    val cos2 = cos(depth * PI / 2.0).let { it * it }

    fun noise(seed: Int): DoubleArray {
        val r = Random(seed)
        return DoubleArray(frames) { r.nextDouble() * 2.0 - 1.0 }
    }

    // ── cylinder-bus Phaser (stereo) ─────────────────────────────────────────

    fun runBusPhaser(floor: Double?, dry: DoubleArray): DoubleArray {
        val p = Phaser(sampleRate = 48000)
        p.rate = 1.0
        p.depth = depth
        if (floor != null) {
            p.floor = floor
        }
        val buf = StereoBuffer(frames)
        for (i in 0 until frames) {
            buf.left[i] = dry[i]
            buf.right[i] = dry[i]
        }
        p.process(buf, frames)
        return DoubleArray(frames) { buf.left[it] }
    }

    "bus phaser: default floor is 1.0 — bit-identical to an explicit floor(1.0)" {
        val dry = noise(11)
        val a = runBusPhaser(null, dry)
        val b = runBusPhaser(1.0, dry)
        for (i in 0 until frames) {
            a[i].toRawBits() shouldBe b[i].toRawBits()
        }
    }

    "bus phaser: floor scales the dry by max(floor, cos²(w·π/2)) — the C4 law" {
        val dry = noise(12)
        val a = runBusPhaser(0.3, dry) // dryC = max(0.3, cos2) = 0.3
        val b = runBusPhaser(0.0, dry) // dryC = cos2
        for (i in 0 until frames) {
            (a[i] - b[i]) shouldBe ((0.3 - cos2) * dry[i] plusOrMinus 1e-12)
        }
    }

    // ── strip phaser (per-voice, mono) — MUST match the bus law ─────────────

    fun runStripPhaser(floor: Double?, dry: DoubleArray): DoubleArray {
        val renderer = if (floor != null) {
            StripPhaserRenderer(rate = 1.0, depth = depth, center = 1000.0, sweep = 1000.0, sampleRate = 48000, floor = floor)
        } else {
            StripPhaserRenderer(rate = 1.0, depth = depth, center = 1000.0, sweep = 1000.0, sampleRate = 48000)
        }
        val buf = AudioBuffer(frames)
        for (i in 0 until frames) {
            buf[i] = dry[i]
        }
        renderer.renderInPlace(buf, sampleRate = 48000)
        return DoubleArray(frames) { buf[it] }
    }

    "strip phaser: default floor is 1.0 — bit-identical to an explicit floor = 1.0" {
        val dry = noise(21)
        val a = runStripPhaser(null, dry)
        val b = runStripPhaser(1.0, dry)
        for (i in 0 until frames) {
            a[i].toRawBits() shouldBe b[i].toRawBits()
        }
    }

    "strip phaser: floor scales the dry by max(floor, cos²(w·π/2)) — same law as the bus" {
        val dry = noise(22)
        val a = runStripPhaser(0.3, dry)
        val b = runStripPhaser(0.0, dry)
        for (i in 0 until frames) {
            (a[i] - b[i]) shouldBe ((0.3 - cos2) * dry[i] plusOrMinus 1e-12)
        }
    }

    "sanity: the wet path is audible in these fixtures (not a two-silent-renders pass)" {
        val dry = noise(23)
        val out = runStripPhaser(0.0, dry)
        var diff = 0.0
        for (i in 0 until frames) {
            val d = abs(out[i] - dry[i])
            if (d > diff) {
                diff = d
            }
        }
        (diff > 1e-3) shouldBe true
    }

    // ── FilterPipelineBuilder forwarding: Voice.Phaser.floor -> StripPhaserRenderer ──

    "buildFilterPipeline forwards the floor into the strip renderer (bit-identical to direct construction)" {
        val dry = noise(31)
        fun runBuilt(floor: Double): DoubleArray {
            val renderers = buildFilterPipeline(
                pipeline = PipelineDsl(stages = listOf(StageDsl.Phaser)),
                modulators = emptyList(),
                startFrame = 0.0,
                gateEndFrame = 1e9,
                crush = Voice.Crush(amount = 0.0),
                coarse = Voice.Coarse(amount = 0.0),
                mainFilter = NoOpAudioFilter,
                envelope = Voice.Envelope(attackFrames = 0.0, decayFrames = 0.0, sustainLevel = 1.0, releaseFrames = 0.0),
                distort = Voice.Distort(amount = 0.0),
                tremolo = Voice.Tremolo(rate = 0.0, depth = 0.0, skew = 0.0, phase = 0.0, shape = null),
                phaser = Voice.Phaser(rate = 1.0, depth = depth, center = 1000.0, sweep = 1000.0, floor = floor),
                sampleRate = 48000,
            )
            renderers.size shouldBe 1
            val buf = AudioBuffer(frames)
            for (i in 0 until frames) {
                buf[i] = dry[i]
            }
            renderers[0].renderInPlace(buf, sampleRate = 48000)
            return DoubleArray(frames) { buf[it] }
        }
        // A dropped `floor = phaser.floor` line falls back to the additive default and
        // the two runs collapse onto each other — this difference row goes red.
        val a = runBuilt(0.3)
        val b = runBuilt(0.0)
        for (i in 0 until frames) {
            (a[i] - b[i]) shouldBe ((0.3 - cos2) * dry[i] plusOrMinus 1e-12)
        }
        // and the built renderer matches direct construction bit-for-bit
        val direct = runStripPhaser(0.3, dry)
        for (i in 0 until frames) {
            a[i].toRawBits() shouldBe direct[i].toRawBits()
        }
    }

    // ── VoiceFactory mapping: phaserFloor -> Voice.Phaser.floor ─────────────

    fun voicePhaserFloorFor(wireFloor: Double?): Double {
        val sampleRate = 44100
        val blockFrames = 128
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
        val data = VoiceData.empty.copy(
            freqHz = 440.0,
            sound = "triangle",
            phaser = 2.0,
            phaserDepth = 0.5,
            phaserFloor = wireFloor,
        )
        val scheduled = ScheduledVoice(
            playbackId = "test",
            data = data,
            startTime = 0.0,
            gateEndTime = 1.0,
            playbackStartTime = 0.0,
        )
        val voice = factory.makeVoice(
            scheduled = scheduled,
            nowFrame = 0.0,
            backendStartTimeSec = 0.0,
            playbackCtx = PlaybackCtx(playbackId = "test", ignitorRegistry = registry, phasePools = PhasePools(Random(1))),
            getSample = { null },
        ) ?: error("makeVoice returned null")
        return voice.phaser.floor
    }

    "VoiceFactory: absent phaserFloor defaults to 1.0 (additive), a set value passes through" {
        voicePhaserFloorFor(null) shouldBe 1.0
        voicePhaserFloorFor(0.25) shouldBe 0.25
    }
})
