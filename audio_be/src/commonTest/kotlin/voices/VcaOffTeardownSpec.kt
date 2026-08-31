/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.ignitor.IgniteContext
import io.peekandpoke.klang.audio_be.ignitor.Ignitor
import io.peekandpoke.klang.audio_be.ignitor.Ignitors
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.ignitor.toExciter
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.filter.EnvelopeRenderer
import io.kotest.assertions.withClue
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import kotlin.math.abs

/**
 * Guards the teardown fade on the `on = false` (`.adsrOff()`) path.
 *
 * **The bug this exists for.** Found by ear on Der Schmetterling's guitars, 2026-08-27, right after
 * Phase 3 shipped. The VCA sits LAST in the strip, so with a curve it drove the fully amplified
 * signal to zero before `Voice.render` dropped the voice. Switching the curve off removed that
 * guarantee, and an ignitor's own envelope cannot replace it, because it sits BEFORE the
 * instrument's amp: the guitar is `adsr → distort("tube") → highpass`, so a tail the envelope has
 * taken to ~1e-4 comes back out of the tube stage 20 dB louder. Teardown then stepped 0.015
 * straight to zero, once per note.
 *
 * The topology below is that guitar in miniature. `envelopeAfterAmp` is the control: the same
 * patch with the envelope last, which never had the problem, and which the numbers here must land
 * near.
 */
class VcaOffTeardownSpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128
    val gateFrames = sampleRate / 4                  // 250 ms
    val releaseFrames = sampleRate * 50 / 1000       // 50 ms, the guitar's release
    val totalFrames = gateFrames + releaseFrames

    fun env(inner: IgnitorDsl) = IgnitorDsl.Adsr(
        inner = inner,
        attackSec = IgnitorDsl.Constant(0.010),
        decaySec = IgnitorDsl.Constant(2.0),
        sustainLevel = IgnitorDsl.Constant(0.0),      // decays toward silence, like the guitar
        releaseSec = IgnitorDsl.Constant(0.050),
    )

    fun amp(inner: IgnitorDsl) = IgnitorDsl.Highpass(
        inner = IgnitorDsl.Distort(
            inner = inner, amount = IgnitorDsl.Constant(0.80), shape = "tube", oversample = 4,
        ),
        freq = IgnitorDsl.Constant(100.0),
    )

    /** The guitar's topology: the envelope is INSIDE the instrument, before its amp. */
    val envelopeBeforeAmp: IgnitorDsl = amp(env(IgnitorDsl.Sawtooth()))

    /** The control: envelope last, the way the strip VCA used to apply it. */
    val envelopeAfterAmp: IgnitorDsl = env(amp(IgnitorDsl.Sawtooth()))

    /**
     * Renders a whole voice through the real EnvelopeRenderer with the VCA off, over an arbitrary
     * frame span and an arbitrary (possibly fractional) endFrame — the branches the fade's clamps
     * actually depend on.
     */
    fun renderSpan(dsl: IgnitorDsl, freqHz: Double, gate: Int, rel: Int, endFrame: Double): AudioBuffer {
        val total = gate + rel
        val signal: Ignitor = dsl.toExciter()
        val signalCtx = IgniteContext(
            sampleRate = sampleRate, voiceDurationFrames = gate, gateEndFrame = gate,
            releaseFrames = rel,  scratchBuffers = ScratchBuffers(blockFrames),
        )
        val out = AudioBuffer(total)
        val block = AudioBuffer(blockFrames)
        val renderer = EnvelopeRenderer(
            Voice.Envelope(attackFrames = 1.0, decayFrames = 1.0, sustainLevel = 1.0, releaseFrames = rel.toDouble()),
            startFrame = 0.0, on = false,
        )
        val ctx = BlockContext(
            audioBuffer = block, freqModBuffer = DoubleArray(blockFrames),
            scratchBuffers = ScratchBuffers(blockFrames), sampleRate = sampleRate,
            startFrame = 0.0, endFrame = endFrame, gateEndFrame = gate.toDouble(), freqHz = freqHz,
            signal = Ignitors.silence(), signalCtx = signalCtx,
            cylinders = Cylinders(blockFrames = blockFrames, sampleRate = sampleRate),
        )
        var pos = 0
        while (pos < total) {
            val n = minOf(blockFrames, total - pos)
            signalCtx.updateOffsetAndLength(0, n); signalCtx.voiceElapsedFrames = pos
            signal.generate(block, freqHz, signalCtx)
            ctx.updateOffsetAndLength(0, n); ctx.blockStart = pos.toDouble()
            renderer.render(ctx)
            for (i in 0 until n) out[pos + i] = block[i]
            pos += n
        }
        return out
    }

    /** Renders a whole voice through the real EnvelopeRenderer with the VCA switched off. */
    fun renderVoiceVcaOff(dsl: IgnitorDsl, freqHz: Double): AudioBuffer {
        val signal: Ignitor = dsl.toExciter()
        val signalCtx = IgniteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = gateFrames,
            gateEndFrame = gateFrames,
            releaseFrames = releaseFrames,
            scratchBuffers = ScratchBuffers(blockFrames),
        )
        val out = AudioBuffer(totalFrames)
        val block = AudioBuffer(blockFrames)
        val renderer = EnvelopeRenderer(
            Voice.Envelope(attackFrames = 480.0, decayFrames = 480.0, sustainLevel = 1.0, releaseFrames = 2400.0),
            startFrame = 0.0,
            on = false,
        )
        val ctx = BlockContext(
            audioBuffer = block,
            freqModBuffer = DoubleArray(blockFrames),
            scratchBuffers = ScratchBuffers(blockFrames),
            sampleRate = sampleRate,
            startFrame = 0.0,
            endFrame = totalFrames.toDouble(),
            gateEndFrame = gateFrames.toDouble(),
            freqHz = freqHz,
            signal = Ignitors.silence(),
            signalCtx = signalCtx,
            cylinders = Cylinders(blockFrames = blockFrames, sampleRate = sampleRate),
        )

        var pos = 0
        while (pos < totalFrames) {
            val n = minOf(blockFrames, totalFrames - pos)
            signalCtx.updateOffsetAndLength(0, n); signalCtx.voiceElapsedFrames = pos
            signal.generate(block, freqHz, signalCtx)

            ctx.updateOffsetAndLength(0, n); ctx.blockStart = pos.toDouble()
            renderer.render(ctx)

            for (i in 0 until n) out[pos + i] = block[i]
            pos += n
        }
        return out
    }

    /** The same signal with NO EnvelopeRenderer at all — the reference the gate must not shape. */
    fun renderRaw(dsl: IgnitorDsl, freqHz: Double): AudioBuffer {
        val signal: Ignitor = dsl.toExciter()
        val signalCtx = IgniteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = gateFrames,
            gateEndFrame = gateFrames,
            releaseFrames = releaseFrames,
            scratchBuffers = ScratchBuffers(blockFrames),
        )
        val out = AudioBuffer(totalFrames)
        val block = AudioBuffer(blockFrames)
        var pos = 0
        while (pos < totalFrames) {
            val n = minOf(blockFrames, totalFrames - pos)
            signalCtx.updateOffsetAndLength(0, n); signalCtx.voiceElapsedFrames = pos
            signal.generate(block, freqHz, signalCtx)
            for (i in 0 until n) out[pos + i] = block[i]
            pos += n
        }
        return out
    }

    /** Level of the very last sample the voice renders before it is dropped. */
    fun lastSample(buf: AudioBuffer) = abs(buf[buf.size - 1])

    fun peak(buf: AudioBuffer) = (0 until buf.size).maxOf { abs(buf[it]) }

    listOf(41.2, 82.4, 164.8).forEach { freq ->

        "the last sample before teardown is EXACTLY silent at ${freq}Hz, envelope BEFORE the amp" {
            // Without the fade this was 0.015 at 82 Hz — a step straight to zero, once per note.
            // The ramp reaches 0 on endFrame-1, the frame Voice.render actually stops on, so this
            // is an exact assertion rather than a threshold. A fade that bottoms out at
            // 1/fadeFrames (the first version of this code) fails here.
            lastSample(renderVoiceVcaOff(envelopeBeforeAmp, freq)) shouldBe 0.0
        }

        "the fade touches ONLY the last few ms at ${freq}Hz" {
            // The previous version of this case compared two different topologies and was inert:
            // raising the fade 60x still passed it. This compares the SAME signal with and without
            // the gate, so widening the window makes it red immediately.
            val gated = renderVoiceVcaOff(envelopeBeforeAmp, freq)
            val raw = renderRaw(envelopeBeforeAmp, freq)
            // A FIXED ceiling, deliberately not derived from VCA_OFF_TEARDOWN_FADE_SECONDS: deriving
            // it lets the test follow the constant, and a 60x widening then still passes (it did).
            // 10 ms is the bound this guard must stay under to remain a guard rather than an envelope.
            val maxGuardFrames = sampleRate * 10 / 1000

            // Everything before the ramp is untouched: the guard is not a second envelope. The
            // comparison can be exact because outside the window the gate does not touch the buffer.
            for (i in 0 until totalFrames - maxGuardFrames) {
                withClue("frame $i must be untouched by the guard") {
                    abs(gated[i] - raw[i]) shouldBe 0.0
                }
            }
            // ...and the ramp actually does something over the window it claims.
            (abs(gated[totalFrames - 2]) < abs(raw[totalFrames - 2])) shouldBe true
        }
    }

    // ── The clamp branches. Round 2 found every one of these dead under the earlier cases. ──

    "a FRACTIONAL endFrame still lands on exactly zero" {
        // endFrame = gateEnd + release*sampleRate is a Double and is fractional at most sample
        // rates. Voice.render truncates, so the last rendered frame is floor(endFrame)-1; targeting
        // endFrame-1 left frac(endFrame)/fadeDen behind. Integral-frame cases cannot see this.
        val buf = renderSpan(envelopeBeforeAmp, 82.4, gate = 4800, rel = 2400, endFrame = 7200.5)
        lastSample(buf) shouldBe 0.0
    }

    "a SHORT non-zero release still gets a real ramp, not a 4-frame one" {
        // The earlier version preferred to start the ramp at gate end, which collapsed the window
        // for a tiny release: at 0.1 ms it got 4 frames and ended on 0.21 of full scale — worse
        // than release = 0 on the same patch. The guard now always takes its full window.
        // A DC source, so the output IS the fade curve and the ramp's LENGTH is visible. Checking
        // only the endpoint is not enough: it reaches 0 either way, and the collapsed 4-frame
        // version is a step from near-full scale one frame earlier.
        val rel = 5   // ~0.1 ms at 48k
        val dc = IgnitorDsl.Constant(1.0)
        val buf = renderSpan(dc, 82.4, gate = 4800, rel = rel, endFrame = (4800 + rel).toDouble())

        lastSample(buf) shouldBe 0.0
        // 20 frames from the end the ramp must already be well down. With the window collapsed to
        // the 5-frame release this frame is still at full scale.
        (abs(buf[buf.size - 21]) < 0.5) shouldBe true
    }

    "a voice with NO release still ends on zero" {
        val buf = renderSpan(envelopeBeforeAmp, 82.4, gate = 4800, rel = 0, endFrame = 4800.0)
        lastSample(buf) shouldBe 0.0
    }

    "the guard never takes more than half of a very short voice" {
        // Floored at the voice midpoint: without that floor a note shorter than the 4 ms window
        // becomes one long linear decay, i.e. the guard turns into the envelope.
        val total = 100
        val buf = renderSpan(envelopeBeforeAmp, 82.4, gate = total, rel = 0, endFrame = total.toDouble())
        val raw = renderRaw(envelopeBeforeAmp, 82.4)
        // The first half is untouched...
        for (i in 0 until total / 2) {
            withClue("frame $i of a short voice must be untouched") {
                abs(buf[i] - raw[i]) shouldBe 0.0
            }
        }
        // ...and it still ends silent.
        lastSample(buf) shouldBe 0.0
    }

    "the envelope-after-amp control was always silent at teardown" {
        // Proves the spec measures the right thing: this topology never had the bug, so it must
        // pass with or without the fade.
        (lastSample(renderVoiceVcaOff(envelopeAfterAmp, 82.4)) < 1e-4) shouldBe true
    }
})
