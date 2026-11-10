/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.ignitor.PhasePools
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.AdsrCurves
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.classic
import io.peekandpoke.klang.common.infra.toKlangMap
import kotlin.math.abs
import kotlin.math.floor
import kotlin.random.Random

/**
 * The end of a voice that ends in its own envelope (`BuiltIgnitor.endsInEnvelope`), through the real `VoiceFactory`:
 * `classic()`'s envelope over a DC source, so the voice's output IS the applied gain and no phase can hide a step.
 *
 * Audit L2-01: the release lands its raw level on 0 on its last frame, but the de-click one-pole lags it, and the
 * voice used to end there, cut with the lag left on its last frame (-49.6 dB at the default release, -23.4 dB at
 * 5 ms, -14.1 dB at 5 ms linear). Its tail now runs on by the de-click's settle (`ENV_DECLICK_SETTLE_TIME_CONSTANTS`),
 * so the smoothed gain reaches an exact 0 before the voice ends.
 *
 * Audit L4-03: the de-click's state is flushed, so a sustain of 0 holds an exact 0, never a subnormal.
 */
class EnvelopeVoiceEndSpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128
    val dcName = "dcvoice"

    /** The voice's output (the DC source times the envelope's gain), from frame 0 while it renders, and the voice. */
    fun renderDcVoice(
        params: Map<String, Double>,
        gateFrames: Int,
        maxFrames: Int,
        instrument: IgnitorDsl = IgnitorDsl.Constant(1.0).classic(),
        freqHz: Double = 110.0,
    ): Pair<DoubleArray, Voice> {
        val registry = IgnitorRegistry().apply { register(dcName, instrument) }
        val factory = VoiceFactory(sampleRate = sampleRate, blockFrames = blockFrames, scratchBuffers = ScratchBuffers(blockFrames))
        val voice = factory.makeVoice(
            scheduled = ScheduledVoice(
                playbackId = "test",
                data = VoiceData.empty.copy(sound = dcName, freqHz = freqHz, ignitorParams = params.toKlangMap()),
                startTime = 0.0,
                gateEndTime = gateFrames.toDouble() / sampleRate,
                playbackStartTime = 0.0,
            ),
            backendStartTimeSec = 0.0,
            playbackCtx = PlaybackCtx(playbackId = "test", ignitorRegistry = registry, phasePools = PhasePools(Random(1))),
            getSample = { null },
        ) ?: error("makeVoice returned null")

        val ctx = VoiceTestHelpers.createContext(blockStart = 0.0, blockFrames = blockFrames, sampleRate = sampleRate)
        val out = DoubleArray(maxFrames)
        var blockStart = 0

        while (blockStart < maxFrames) {
            ctx.blockStart = blockStart.toDouble()
            ctx.voiceBuffer.fill(0.0)

            if (!voice.render(ctx)) {
                break
            }

            for (i in 0 until minOf(blockFrames, maxFrames - blockStart)) {
                out[blockStart + i] = ctx.voiceBuffer[i]
            }

            blockStart += blockFrames
        }

        return out to voice
    }

    for ((curveName, curve) in listOf("exponential" to AdsrCurve.Exponential, "linear" to AdsrCurve.Linear)) {
        for (release in listOf(0.05, 0.005)) {
            "a voice that ends in classic()'s envelope ends on an exact zero: release $release s, $curveName" {
                val gate = sampleRate / 4
                val (out, voice) = renderDcVoice(
                    params = mapOf("adsr.release" to release, "adsrCurves.release" to AdsrCurves.indexOf(curve)),
                    gateFrames = gate,
                    maxFrames = 2 * sampleRate,
                )
                // Where the release's raw level reaches 0, the frame the voice used to end on (`EnvelopeCore`)
                val releaseEnd = gate + floor(release * sampleRate).toInt() - 1
                val lastFrame = floor(voice.endFrame).toInt() - 1

                withClue("engaged: the de-click's lag is left where the release ends (the old last frame)") {
                    (out[releaseEnd] > 1e-4) shouldBe true
                }
                withClue("the voice runs on past it and its last frame is an exact 0") {
                    (lastFrame > releaseEnd) shouldBe true
                    out[lastFrame] shouldBe 0.0
                }
                withClue("it falls to the 0 without a step: no frame after the release's end rises, none jumps") {
                    for (i in releaseEnd + 1..lastFrame) {
                        (out[i] <= out[i - 1]) shouldBe true
                    }
                }
                withClue("and it ends: Done at its end frame, nothing rendered after it") {
                    voice.state shouldBe Voice.State.Done
                    (lastFrame + 1 until out.size).all { out[it] == 0.0 } shouldBe true
                }
            }
        }
    }

    "a pitch-relative release under detune(-12) plays out to its own end: the lifetime is read at the detuned pitch (audit L5-06)" {
        // `sine.adsr(release = 200 / freq).detune(-12)` at 440 Hz: the envelope runs at 220 Hz, a release of 0.909 s.
        // Read at the note, the voice lived 0.4545 s past its gate and was cut at -14.7 dB in mid-release.
        val release = IgnitorDsl.Times(left = IgnitorDsl.Recip(inner = IgnitorDsl.Freq), right = IgnitorDsl.Constant(200.0))
        val env = IgnitorDsl.Adsr(
            inner = IgnitorDsl.Sine(freq = IgnitorDsl.Freq),
            attack = IgnitorDsl.Constant(0.001),
            decay = IgnitorDsl.Constant(0.01),
            sustain = IgnitorDsl.Constant(1.0),
            release = release,
        )
        val gate = sampleRate / 10
        val (out, voice) = renderDcVoice(
            params = emptyMap(),
            gateFrames = gate,
            maxFrames = 2 * sampleRate,
            instrument = IgnitorDsl.Detune(inner = env, semitones = IgnitorDsl.Constant(-12.0)),
            freqHz = 440.0,
        )
        val lastFrame = floor(voice.endFrame).toInt() - 1
        val lastBlockPeak = (lastFrame - blockFrames + 1..lastFrame).maxOf { abs(out[it]) }

        withClue("the voice lives the detuned release, 200 / 220 s past its gate") {
            ((voice.endFrame - gate) / sampleRate) shouldBe (200.0 / 220.0 plusOrMinus 1e-9)
        }
        withClue("its last block is the release's quiet end, not a cut in mid-release") { (lastBlockPeak < 1e-3) shouldBe true }
        withClue("its last frame is the release's exact 0") { out[lastFrame] shouldBe 0.0 }
    }

    "a sustain of 0 holds an exact 0 under the de-click, never a subnormal (audit L4-03)" {
        val held = 3 * sampleRate / 2
        val (out, _) = renderDcVoice(
            params = mapOf("adsr.attack" to 0.01, "adsr.decay" to 0.1, "adsr.sustain" to 0.0, "adsr.release" to 0.05),
            gateFrames = held,
            maxFrames = held,
        )
        val subnormals = out.count { it != 0.0 && abs(it) < 2.2250738585072014E-308 }

        withClue("no subnormal gain") { subnormals shouldBe 0 }
        withClue("exact 0 from about 0.15 s on (the decay ends at 0.11 s, then 34.5 time constants of 1 ms)") {
            (sampleRate * 15 / 100 until held).all { out[it] == 0.0 } shouldBe true
        }
        withClue("engaged: the decay sounds") { (out[sampleRate / 20] > 0.1) shouldBe true }
    }
})
