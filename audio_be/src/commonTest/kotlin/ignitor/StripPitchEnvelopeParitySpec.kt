/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_be.SampleStore
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.engines.PipelineRegistry
import io.peekandpoke.klang.audio_be.voices.PlaybackCtx
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.VoiceFactory
import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.AdsrCurves
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.SampleRequest
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import kotlin.math.floor
import kotlin.random.Random

/**
 * ONE pitch envelope on two hosts (phase 3 step 5b (c1), decision D3): the voice STRIP's pitch envelope,
 * sprudel's `penv(amount, attack, decay, sustain, release)` and `penvCurves(...)` on the wire, against the
 * Ignitor node `pitchEnvelope`, with the same numbers, rendered through the real [VoiceFactory] as whole
 * voices and compared in RAW BITS.
 *
 * Why this can be bit-identical, and is expected to be: both hosts run [EnvelopeCore] through the one
 * mapping `renderPitchEnvelopeRatios`, and both hand the oscillator the same ratio array. On the strip the
 * renderer writes the frequency-modulation buffer and `IgniteRenderer` passes it as `phaseMod`; on the node
 * `ModApplyingIgnitor` copies its mod's output into a ratio array (`modBuf[i]`, no multiply when no strip
 * mod exists) and passes that as `phaseMod`. The oscillator's phase path is the same code in both.
 *
 * This is a PARITY spec: a change inside the shared core or the shared mapping moves both sides together
 * and stays green here. The law itself is pinned against written-out oracles in `EnvelopeLawSpec` and
 * `ModEnvelopeDefaultCurveSpec`.
 */
class StripPitchEnvelopeParitySpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128
    val blocks = 150
    val frames = blocks * blockFrames
    val gateSec = 0.2

    /** Renders one voice of [data] and returns its output and the frequency ratios the strip wrote. */
    fun render(data: VoiceData, extra: Pair<String, IgnitorDsl>? = null, onsetFrames: Int = 37): Pair<DoubleArray, DoubleArray> {
        val onsetSec = onsetFrames.toDouble() / sampleRate
        val registry = IgnitorRegistry().apply {
            registerDefaults()
            register("plainsine", IgnitorDsl.Sine())

            if (extra != null) {
                register(extra.first, extra.second)
            }
        }
        val freqModBuffer = DoubleArray(blockFrames)
        val factory = VoiceFactory(
            sampleRate = sampleRate,
            sampleRateDouble = sampleRate.toDouble(),
            blockFrames = blockFrames,
            ignitorRegistry = registry,
            pipelineRegistry = PipelineRegistry(),
            cylinders = Cylinders(blockFrames = blockFrames, sampleRate = sampleRate),
            voiceBuffer = DoubleArray(blockFrames),
            freqModBuffer = freqModBuffer,
            scratchBuffers = ScratchBuffers(blockFrames),
        )
        val noSamples: (SampleRequest) -> SampleStore.SampleEntry.Complete? = { null }
        val voice = factory.makeVoice(
            scheduled = ScheduledVoice(
                playbackId = "penv",
                data = data,
                startTime = onsetSec,
                gateEndTime = onsetSec + gateSec,
                playbackStartTime = 0.0,
            ),
            backendStartTimeSec = 0.0,
            playbackCtx = PlaybackCtx(playbackId = "penv", ignitorRegistry = registry, phasePools = PhasePools(Random(1))),
            getSample = noSamples,
        ) ?: error("makeVoice returned null")
        val rc = Voice.RenderContext(
            cylinders = Cylinders(blockFrames = blockFrames, sampleRate = sampleRate),
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            voiceBuffer = DoubleArray(blockFrames),
            freqModBuffer = DoubleArray(blockFrames),
            scratchBuffers = ScratchBuffers(blockFrames),
        )
        val out = DoubleArray(frames)
        val ratios = DoubleArray(frames) { 1.0 }

        repeat(blocks) { block ->
            rc.blockStart = (block * blockFrames).toDouble()
            freqModBuffer.fill(1.0)

            if (voice.render(rc)) {
                freqModBuffer.copyInto(ratios, block * blockFrames)
            }

            val cylinder = rc.cylinders.getOrInit(voice.cylinderId, voice, 0.0)

            cylinder.mixBuffer.left.copyInto(out, block * blockFrames, 0, blockFrames)
            cylinder.mixBuffer.left.fill(0.0)
            cylinder.mixBuffer.right.fill(0.0)
        }

        return out to ratios
    }

    val base = VoiceData.empty.copy(freqHz = 220.0, sound = "plainsine")

    fun bits(a: DoubleArray): List<Long> = a.map { it.toRawBits() }

    /** One row: the stages (null = unwritten), the curves (null = unnamed). */
    class Row(
        val title: String,
        val amount: Double,
        val attack: Double?,
        val decay: Double?,
        val sustain: Double?,
        val release: Double?,
        val curves: Triple<AdsrCurve, AdsrCurve, AdsrCurve>? = null,
    ) {
        fun strip(base: VoiceData): VoiceData = base.copy(
            pEnv = amount, pAttack = attack, pDecay = decay, pSustain = sustain, pRelease = release,
            pAttackCurve = curves?.first, pDecayCurve = curves?.second, pReleaseCurve = curves?.third,
        )

        fun node(): IgnitorDsl {
            val bare = IgnitorDsl.PitchEnvelope(inner = IgnitorDsl.Sine(), semitones = IgnitorDsl.Constant(amount))

            return bare.copy(
                attackSec = attack?.let { IgnitorDsl.Constant(it) } ?: bare.attackSec,
                decaySec = decay?.let { IgnitorDsl.Constant(it) } ?: bare.decaySec,
                sustainLevel = sustain?.let { IgnitorDsl.Constant(it) } ?: bare.sustainLevel,
                releaseSec = release?.let { IgnitorDsl.Constant(it) } ?: bare.releaseSec,
                attackCurve = curves?.let { AdsrCurves.knob(it.first) } ?: bare.attackCurve,
                decayCurve = curves?.let { AdsrCurves.knob(it.second) } ?: bare.decayCurve,
                releaseCurve = curves?.let { AdsrCurves.knob(it.third) } ?: bare.releaseCurve,
            )
        }
    }

    val rows = listOf(
        Row("a kick: the gate after the sweep", 24.0, 0.001, 0.08, 0.0, 0.0),
        Row("a held sustain, the release inside the render", 12.0, 0.02, 0.1, 0.5, 0.3),
        Row("the gate inside the decay, a short release", 24.0, 0.001, 0.3, 0.0, 0.05),
        Row("the gate inside the attack", 7.0, 0.3, 0.1, 0.0, 0.02),
        Row("named curves", 12.0, 0.05, 0.1, 0.3, 0.1, Triple(AdsrCurve.Linear, AdsrCurve.SCurve, AdsrCurve.Square)),
        Row("a negative amount and a raw sustain above 1", -12.0, 0.01, 0.05, 1.5, 0.1),
        Row("every stage unwritten: the shared defaults", 24.0, null, null, null, null),
        Row("fractional frame counts", 9.0, 0.00501, 0.10001, 0.2, 0.05001),
    )

    for (row in rows) {
        "whole voice: the strip's penv IS the node's pitchEnvelope, bit for bit: ${row.title}" {
            for (onset in listOf(0, 37)) {
                val (strip, stripRatios) = render(row.strip(base), onsetFrames = onset)
                val (node, _) = render(base.copy(sound = "pitchsine"), extra = "pitchsine" to row.node(), onsetFrames = onset)
                val (bare, _) = render(base, onsetFrames = onset)

                withClue("onset $onset: the strip's pitch envelope is engaged") { bits(strip) shouldNotBe bits(bare) }
                withClue("onset $onset: the ratios moved") { stripRatios.any { it != 1.0 } shouldBe true }
                withClue("onset $onset: first mismatching frame") {
                    strip.indices.firstOrNull { strip[it].toRawBits() != node[it].toRawBits() } shouldBe null
                }
            }
        }
    }

    "a gate inside the sweep returns to the note AT the gate with the default release 0 (Q3)" {
        // A 0.3 s decay, the gate at 0.2 s after a 37-frame onset, no release written: the ratio is exactly
        // 1.0 (level 0) from the gate frame on, and above 1 on the frame before it.
        val (_, ratios) = render(base.copy(pEnv = 24.0, pAttack = 0.001, pDecay = 0.3))
        // The factory's own arithmetic: absolute frames floored from the scheduled times.
        val onsetSec = 37.0 / sampleRate
        val gateFrame = floor((onsetSec + gateSec) * sampleRate).toInt()

        assertSoftly {
            withClue("the frame before the gate is still in the sweep") { (ratios[gateFrame - 1] > 1.0) shouldBe true }
            withClue("on the note from the gate frame") { ratios.drop(gateFrame).all { it == 1.0 } shouldBe true }
        }
    }

    "a non-finite amount is no pitch envelope, a non-finite sustain is the unwritten sustain (Q7)" {
        val (bare, _) = render(base)
        val (nanAmount, _) = render(base.copy(pEnv = Double.NaN, pAttack = 0.01, pDecay = 0.1))
        val (unwrittenSustain, _) = render(base.copy(pEnv = 12.0, pAttack = 0.01, pDecay = 0.1))
        val (nanSustain, _) = render(base.copy(pEnv = 12.0, pAttack = 0.01, pDecay = 0.1, pSustain = Double.NaN))

        withClue("a NaN amount renders the bare voice") { bits(nanAmount) shouldBe bits(bare) }
        withClue("a NaN sustain renders the unwritten sustain") { bits(nanSustain) shouldBe bits(unwrittenSustain) }
        withClue("anti-vacuous: the envelope with an unwritten sustain is engaged") { bits(unwrittenSustain) shouldNotBe bits(bare) }
    }
})
