/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.probes.AliasingConfig
import io.peekandpoke.klang.audio_be.probes.PROBE_BLOCK_FRAMES
import io.peekandpoke.klang.audio_be.probes.PROBE_SAMPLE_RATE
import io.peekandpoke.klang.audio_be.probes.heldSilence
import io.peekandpoke.klang.audio_be.probes.measureAliasing
import io.peekandpoke.klang.audio_be.probes.probeSine
import io.peekandpoke.klang.audio_be.probes.sampleVoiceProbeStage
import io.peekandpoke.klang.audio_be.probes.wholeProbeBlocks
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import java.util.Locale
import kotlin.math.pow
import kotlin.random.Random

/**
 * **The band-limited sources alias where the aliasing meter says they should** (KLANG-00430, audit findings L3-02 and
 * L3-05). Each row renders one source through one of its loops and reads the aliasing meter's non-harmonic energy below 5
 * kHz ([io.peekandpoke.klang.audio_be.probes.AliasingReading.nonHarmonicBelow5kDb]), where an alias is heard.
 *
 * - The saw, the square and their unison stacks read their shape through a box one sample's increment wide
 *   ([waveTrapezoidBoxed]). Before, at 1244.5 Hz: the saw -46.6 dB, the square -49.3; after about -68.5 and -70.6.
 *   Every trapezoid loop has a row: the mono oscillator's block-constant loop, its PWM loop (a duty that is a signal),
 *   its phased loop (a phase that is a signal), the same loop under a pitch modulation of an octave (the box follows the
 *   per-sample increment: -26.4 before, about -50.6 after), and the unison stack's plain and phased loops.
 * - The sample playhead reads with a four-point cubic Lagrange kernel: a sine sample a semitone up at 4186 Hz read
 *   -39.8 dB through the linear read, about -65.9 through the cubic one.
 *
 * The thresholds sit between the two, about 10 dB from each side.
 */
class BandLimitedSourcesSpec : StringSpec({

    val sr = PROBE_SAMPLE_RATE
    val frames = wholeProbeBlocks(AliasingConfig().framesNeeded)
    val pitch = 1244.5

    /** A signal that writes [value] on every sample: not block-constant, so the oscillator takes its per-sample path. */
    class Held(private val value: Double) : Ignitor {
        override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
            for (i in ctx.offset until ctx.windowEnd) {
                buffer[i] = value
            }
        }
    }

    /** [source] at [freqHz] for [frames] frames, in blocks of 128, with every sample's increment times [phaseMod] when set. */
    fun render(source: Ignitor, freqHz: Double, phaseMod: Double? = null): DoubleArray {
        val block = PROBE_BLOCK_FRAMES
        val ctx = IgniteContext(
            sampleRate = sr,
            voiceDurationFrames = frames,
            gateEndFrame = frames,
            scratchBuffers = ScratchBuffers(block),
            random = Random(1),
        )
        val pm = if (phaseMod != null) DoubleArray(block) { phaseMod } else null
        val buffer = AudioBuffer(block)
        val out = DoubleArray(frames)
        var at = 0

        while (at < frames) {
            ctx.updateOffsetAndLength(offset = 0, length = block)
            ctx.voiceElapsedFrames = at
            ctx.phaseMod = pm
            source.generate(buffer, freqHz, ctx)
            buffer.copyInto(destination = out, destinationOffset = at, startIndex = 0, endIndex = block)
            at += block
        }

        return out
    }

    fun below5k(x: DoubleArray, nominalHz: Double): Double = measureAliasing(samples = x, nominalHz = nominalHz).nonHarmonicBelow5kDb

    fun f(x: Double): String = String.format(Locale.ROOT, "%.1f", x)

    val analog0 = ConstantIgnitor(0.0)

    "the saw, the square and their stacks: the non-harmonic energy below 5 kHz at 1244.5 Hz, through every trapezoid loop (L3-02)" {
        val rows = listOf(
            "saw, block-constant loop" to render(Ignitors.saw(analog = analog0), pitch),
            "ramp, block-constant loop" to render(Ignitors.ramp(analog = analog0), pitch),
            "square, block-constant loop" to render(Ignitors.square(analog = analog0), pitch),
            "square, PWM loop (duty a signal)" to render(Ignitors.pulze(duty = Held(0.5), analog = analog0), pitch),
            "saw, phased loop (phase a signal)" to render(Ignitors.saw(analog = analog0, phase = Held(0.0)), pitch),
            "supersaw voices 1, stack loop" to render(Ignitors.superSaw(voices = ConstantIgnitor(1.0), analog = analog0, rng = Random(3)), pitch),
            "supersquare voices 1, stack loop" to render(Ignitors.superSquare(voices = ConstantIgnitor(1.0), analog = analog0, rng = Random(3)), pitch),
            "supersaw voices 1, phased stack loop" to render(
                Ignitors.superSaw(voices = ConstantIgnitor(1.0), analog = analog0, rng = Random(3), phase = Held(0.0)),
                pitch,
            ),
        )

        for ((label, x) in rows) {
            val db = below5k(x = x, nominalHz = pitch)

            println("BAND-LIMITED $label: ${f(db)} dB below 5 kHz")

            withClue("$label: non-harmonic energy below 5 kHz re the fundamental") {
                db shouldBeLessThan -60.0
            }
        }
    }

    "the saw raised an octave by a pitch modulation keeps the box of its own step (L3-02)" {
        // The flank is sized from the block's unmodulated increment; the box follows the per-sample one, so the octave up
        // is still read through a box one sample wide. Before: -26.4 dB (the flank alone, half a sample at this step).
        val x = render(Ignitors.saw(analog = analog0), freqHz = pitch / 2.0, phaseMod = 2.0)
        val db = below5k(x = x, nominalHz = pitch)

        println("BAND-LIMITED saw an octave up by phaseMod 2: ${f(db)} dB below 5 kHz")

        db shouldBeLessThan -45.0
    }

    "the sample playhead's cubic read: a sine sample a semitone up at 4186 Hz (L3-05)" {
        val rate = 2.0.pow(1.0 / 12.0)
        val tone = 4186.0
        val pcm = probeSine(hz = tone / rate, amplitude = 0.5, frames = frames * 2)
        val x = sampleVoiceProbeStage(tree = IgnitorDsl.Sample, pcm = pcm, rate = rate, freqHz = tone).render(heldSilence(frames)).left
        val nonHarmonic = measureAliasing(samples = x, nominalHz = tone).nonHarmonicDb

        println("BAND-LIMITED sample playhead a semitone up at 4186 Hz: ${f(nonHarmonic)} dB non-harmonic")

        nonHarmonic shouldBeLessThan -52.0
    }
})
