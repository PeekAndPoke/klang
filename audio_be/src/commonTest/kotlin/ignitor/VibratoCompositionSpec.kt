/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.utils.SAFE_MAX
import io.peekandpoke.klang.audio_be.utils.TWO_PI
import io.peekandpoke.klang.audio_be.utils.fastExp2
import io.peekandpoke.klang.audio_be.utils.fastSin
import io.peekandpoke.klang.audio_be.utils.wrapPhase
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.mul
import io.peekandpoke.klang.audio_bridge.plus
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * **The vibrato as a composition** (pitch pipeline 7b): the `vibrato` node's runtime arm composes
 * `pitchModSemitones(sine(rate, analog = 0) * max(semitones, 0))` (`vibratoModIgnitor`), so every pitched source under
 * it reads the ratio `2^((sin * max(semitones, 0)) / 12)` with the depth taken PER SAMPLE.
 *
 * **The seam** is `PitchModSemitonesSpec`'s: the [IgnitorDsl.Sample] leaf over a [RatioProbe], which records the ratio
 * stream it reads (`ctx.phaseMod`), what an oscillator multiplies its phase increment by.
 *
 * **The oracles.** The tolerance row writes the REPLACED node's law with the engine's `fastSin` and `fastExp2`, on
 * purpose: it bounds the rounding between the two. The shield, rate and mid-voice rate rows write the LFO with the
 * library's `sin` and `pow`, so they pin the LFO independently. The signal, crossing and +Infinity rows take the LFO's
 * values from the engine's own sine rendered alone (pinned by those rows) and the depth's from the depth rendered
 * alone, and apply the law with `pow`.
 *
 * The rows follow the edge rules, whose one home is the `vibrato` row of `audio/ref/off-values.md`. A non-finite
 * LITERAL knob is pinned in `IgnitorGateSpec` and `PitchModSafetyTest`.
 */
class VibratoCompositionSpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128
    val blocks = 12
    val frames = blocks * blockFrames
    val noteHz = 220.0

    fun c(v: Double) = IgnitorDsl.Constant(v)

    /** A sine LFO at a fixed rate and no drift. */
    fun lfo(rate: Double) = IgnitorDsl.Sine(freq = c(rate), analog = c(0.0))

    /** [v] as a SIGNAL: the gate and the literal read cannot answer it at build, the runtime reads [v]. */
    fun nonLeaf(v: Double) = IgnitorDsl.OptimizerHint(inner = c(v))

    fun vibrato(rate: IgnitorDsl, semitones: IgnitorDsl) = IgnitorDsl.Vibrato(inner = IgnitorDsl.Sample, rate = rate, semitones = semitones)

    /** Records the ratio the [IgnitorDsl.Sample] leaf reads and whether a pitch mod reached it, frame by frame. */
    class RatioProbe(frames: Int) : Ignitor {
        val ratios = DoubleArray(frames)
        val modded = BooleanArray(frames)
        var calls = 0

        override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
            val pm = ctx.phaseMod

            calls++

            for (i in ctx.offset until ctx.windowEnd) {
                val frame = ctx.voiceElapsedFrames + (i - ctx.offset)

                ratios[frame] = pm?.get(i) ?: 1.0
                modded[frame] = pm != null
                buffer[i] = 0.0
            }
        }
    }

    /** Renders [dsl] block by block and hands back its output; [sampleSource] serves its [IgnitorDsl.Sample] leaves. */
    fun render(dsl: IgnitorDsl, sampleSource: Ignitor? = null): DoubleArray {
        val ctx = IgniteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = frames,
            gateEndFrame = frames,
            scratchBuffers = ScratchBuffers(blockFrames),
            random = Random(7),
        )
        val built = dsl.buildExciter(freqHz = noteHz, sampleRate = sampleRate, random = ctx.random, sampleSource = sampleSource).ignitor
        val buffer = AudioBuffer(blockFrames)
        val out = DoubleArray(frames)

        for (b in 0 until blocks) {
            ctx.updateOffsetAndLength(offset = 0, length = blockFrames)
            ctx.voiceElapsedFrames = b * blockFrames
            built.generate(buffer, noteHz, ctx)
            buffer.copyInto(destination = out, destinationOffset = b * blockFrames, startIndex = 0, endIndex = blockFrames)
        }

        return out
    }

    /** The ratio stream the probed source under [dsl] reads; asks for one call per block and a built node on every frame. */
    fun ratiosOf(dsl: IgnitorDsl): DoubleArray {
        val probe = RatioProbe(frames)

        render(dsl = dsl, sampleSource = probe)

        withClue("the probed source is called once per block") {
            probe.calls shouldBe blocks
        }

        withClue("a pitch mod reaches the probed source on every frame") {
            probe.modded.count { !it } shouldBe 0
        }

        return probe.ratios
    }

    /** The worst relative error of [got] against [expected], frame by frame. */
    fun worstRelative(got: DoubleArray, expected: (Int) -> Double): Double {
        var worst = 0.0

        for (i in got.indices) {
            worst = maxOf(worst, abs(got[i] / expected(i) - 1.0))
        }

        return worst
    }

    // ── A constant depth: one rounding from the node it replaced ────────────────────────────────────────────

    "a constant depth: the composed ratio is the replaced node's law to about one rounding, never further" {
        // The node 7b replaced wrote `fastExp2(fastSin(phase) * (semitones / 12))`, its phase stepping `2 pi rate / sr`
        // per frame from 0, wrapped at 2 pi. The composition steps the same phase (the sine oscillator) and writes
        // `fastExp2((fastSin(phase) * semitones) / 12)`: the grouping differs, so a frame may differ by a rounding of
        // the exponent (measured in 7a: up to 1.5 ulp at 12 semitones). The bound is 5e-16 relative, about two ulps.
        for ((rate, semitones) in listOf(7.0 to 0.012, 5.0 to 0.25, 6.0 to 0.5, 4.5 to 1.0, 5.0 to 12.0)) {
            withClue("rate $rate, semitones $semitones") {
                val got = ratiosOf(vibrato(rate = c(rate), semitones = c(semitones)))
                val replaced = DoubleArray(frames)
                val inc = TWO_PI * rate / sampleRate
                var phase = 0.0

                for (i in 0 until frames) {
                    replaced[i] = fastExp2(fastSin(phase) * (semitones / 12.0))
                    phase = (phase + inc).wrapPhase(TWO_PI)
                }

                worstRelative(got) { replaced[it] } shouldBeLessThan 5e-16
                withClue("engaged: the pitch moved") { got.maxOf { abs(it - 1.0) } shouldBeGreaterThan 1e-5 }
            }
        }
    }

    // ── A signal depth: per sample ──────────────────────────────────────────────────────────────────────────

    "a signal depth is read per sample: every frame reads 2^(lfo * depth / 12) at that frame's depth" {
        // The depth swings from 1 to 2 and down to about 0.1 semitones over the render (20 Hz, 0.64 cycles).
        val rate = 5.0
        val depth = lfo(20.0).plus(1.0)
        val lfoValues = render(lfo(rate))
        val depthValues = render(depth)
        val got = ratiosOf(vibrato(rate = c(rate), semitones = depth))

        withClue("the oracle at each frame's depth") {
            worstRelative(got) { 2.0.pow(lfoValues[it] * depthValues[it] / 12.0) } shouldBeLessThan 1e-9
        }

        withClue("the row can tell per sample from per block: the block's first depth leaves the stream") {
            worstRelative(got) { 2.0.pow(lfoValues[it] * depthValues[it - it % blockFrames] / 12.0) } shouldBeGreaterThan 1e-6
        }
    }

    "a depth sample at or below 0 is no vibrato on that frame, exactly 1.0; above 0 the law holds" {
        // Two semitones at 30 Hz: the depth is below 0 for half of each cycle, crossing 0 inside blocks.
        val rate = 5.0
        val depth = lfo(30.0).mul(2.0)
        val lfoValues = render(lfo(rate))
        val depthValues = render(depth)
        val got = ratiosOf(vibrato(rate = c(rate), semitones = depth))
        val off = (0 until frames).filter { depthValues[it] <= 0.0 }
        val on = (0 until frames).filter { depthValues[it] > 0.0 && lfoValues[it] != 0.0 }

        off.size shouldBeGreaterThan 100
        on.size shouldBeGreaterThan 100

        withClue("frames at or below 0 read exactly 1.0 (no LFO inversion)") {
            off.count { got[it] != 1.0 } shouldBe 0
        }

        withClue("frames above 0 follow the law") {
            on.maxOf { abs(got[it] / 2.0.pow(lfoValues[it] * depthValues[it] / 12.0) - 1.0) } shouldBeLessThan 1e-9
        }
    }

    // ── Non-finite depth and rate samples (a signal; a literal reads the default, see the KDoc) ──────────────

    "a NaN or -Infinity depth sample is no vibrato: the floor reads it as 0, every frame exactly 1.0" {
        for (bad in listOf(Double.NaN, Double.NEGATIVE_INFINITY)) {
            // As a per-sample signal (an LFO plus the value, so every sample is the value) and as a block constant.
            for ((form, depth) in listOf("signal" to lfo(3.0).plus(nonLeaf(bad)), "block constant" to nonLeaf(bad))) {
                withClue("$bad as a $form") {
                    ratiosOf(vibrato(rate = c(100.0), semitones = depth)).count { it != 1.0 } shouldBe 0
                }
            }
        }
    }

    "a +Infinity depth sample passes the floor unguarded: SAFE_MAX where the LFO is positive, 0 where it is negative" {
        // No per-sample guard (the non-finite task, section 2): the multiply clamps the product at SAFE_MAX, the semitone
        // primitive reads 2^(SAFE_MAX / 12) as SAFE_MAX and 2^(-SAFE_MAX / 12) as 0. At the LFO's exact 0 (frame 0) the
        // product is a NaN the multiply scrubs to 0: the ratio 1.0.
        val rate = 100.0
        val lfoValues = render(lfo(rate))
        val got = ratiosOf(vibrato(rate = c(rate), semitones = lfo(3.0).plus(nonLeaf(Double.POSITIVE_INFINITY))))

        lfoValues.count { it > 0.0 } shouldBeGreaterThan 100
        lfoValues.count { it < 0.0 } shouldBeGreaterThan 100

        for (i in 0 until frames) {
            val expected = when {
                lfoValues[i] > 0.0 -> SAFE_MAX
                lfoValues[i] < 0.0 -> 0.0
                else -> 1.0
            }

            withClue("frame $i, lfo ${lfoValues[i]}") { got[i] shouldBe expected }
        }
    }

    "a non-finite rate for the whole voice is no vibrato: the oscillator's wrap holds the phase at 0 from the first frame" {
        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            withClue("rate $bad") {
                ratiosOf(vibrato(rate = nonLeaf(bad), semitones = c(0.5))).count { it != 1.0 } shouldBe 0
            }
        }
    }

    "a non-finite rate mid-voice: the block's first frame plays the stale phase, the rest 1.0, then the LFO restarts at 0" {
        // The rate is 50 Hz for two blocks, NaN for the third, 50 Hz after. The sine writes a frame before it steps
        // the phase, so the NaN block's first frame still reads the phase the LFO had reached; the NaN increment then
        // makes the wrap scrub the phase to 0, so the block's other frames are exactly 1.0, and the next block starts
        // the LFO again from phase 0 (a phase jump).
        class SwitchingRate(private val nanBlock: Int) : Ignitor {
            var block = 0

            override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
                val v = if (block == nanBlock) Double.NaN else 50.0

                for (i in ctx.offset until ctx.windowEnd) {
                    buffer[i] = v
                }

                block++
            }
        }

        val semitones = 1.0
        val renderBlocks = 5
        val mod = vibratoModIgnitor(rate = SwitchingRate(nanBlock = 2), semitones = ConstantIgnitor(semitones))
        val ctx = IgniteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = frames,
            gateEndFrame = frames,
            scratchBuffers = ScratchBuffers(blockFrames),
            random = Random(7),
        )
        val buffer = AudioBuffer(blockFrames)
        val got = DoubleArray(renderBlocks * blockFrames)

        for (b in 0 until renderBlocks) {
            ctx.updateOffsetAndLength(offset = 0, length = blockFrames)
            ctx.voiceElapsedFrames = b * blockFrames
            mod.generate(buffer, noteHz, ctx)
            buffer.copyInto(destination = got, destinationOffset = b * blockFrames, startIndex = 0, endIndex = blockFrames)
        }

        fun law(phaseFrames: Int) = 2.0.pow(sin(TWO_PI * 50.0 * phaseFrames / sampleRate) * semitones / 12.0)

        val nanStart = 2 * blockFrames

        withClue("blocks 0 and 1 follow the law at 50 Hz") {
            (0 until nanStart).maxOf { abs(got[it] / law(it) - 1.0) } shouldBeLessThan 1e-9
        }

        withClue("the NaN block's first frame plays the stale phase, and is not 1.0") {
            abs(got[nanStart] / law(nanStart) - 1.0) shouldBeLessThan 1e-9
            abs(got[nanStart] - 1.0) shouldBeGreaterThan 1e-3
        }

        withClue("the NaN block's other frames are exactly 1.0") {
            (nanStart + 1 until nanStart + blockFrames).count { got[it] != 1.0 } shouldBe 0
        }

        withClue("from the next block the LFO restarts at phase 0") {
            (nanStart + blockFrames until renderBlocks * blockFrames).maxOf { abs(got[it] / law(it - nanStart - blockFrames) - 1.0) } shouldBeLessThan 1e-9
        }
    }

    // ── The LFO's shield ────────────────────────────────────────────────────────────────────────────────────

    "the LFO reads no pitch mod: a vibrato rendered inside another source's modulated scope keeps its rate" {
        // The vibrato'd probe sits in the FREQUENCY knob of a sine an octave up (`pitchModSemitones(12)`, the ratio exactly
        // 2.0), so the vibrato's mod renders while that sine's ratio is on the context. The probe reads both (its own mod
        // times the scope's 2.0, `ModApplyingIgnitor`), but the LFO must not run at twice its rate: the node 7b
        // replaced read no `phaseMod`, and the composed LFO is shielded (`ModBlockingIgnitor`).
        val rate = 5.0
        val semitones = 1.0
        val inScope = IgnitorDsl.PitchModSemitones(
            inner = IgnitorDsl.Sine(freq = IgnitorDsl.Freq.plus(vibrato(rate = c(rate), semitones = c(semitones))), analog = c(0.0)),
            mod = c(12.0),
        )
        val got = ratiosOf(inScope)

        worstRelative(got) { 2.0 * 2.0.pow(sin(TWO_PI * rate * it / sampleRate) * semitones / 12.0) } shouldBeLessThan 1e-9
    }

    // ── The rate: read once per block ───────────────────────────────────────────────────────────────────────

    "the rate is read once per block, at the block's first frame, and the phase steps by it through the block" {
        // A rate swinging 6 +- 4 Hz at 40 Hz, so it moves inside every block. The oracle steps the phase by the rate at
        // each block's first frame, as the sine oscillator reads its frequency.
        val semitones = 3.0
        val rate = lfo(40.0).mul(4.0).plus(6.0)
        val rateValues = render(rate)
        val got = ratiosOf(vibrato(rate = rate, semitones = c(semitones)))

        fun stream(rateAt: (Int) -> Double): DoubleArray {
            val out = DoubleArray(frames)
            var phase = 0.0

            for (i in 0 until frames) {
                out[i] = 2.0.pow(sin(phase) * semitones / 12.0)
                phase = (phase + TWO_PI * rateAt(i) / sampleRate).wrapPhase(TWO_PI)
            }

            return out
        }

        val perBlock = stream { rateValues[it - it % blockFrames] }
        val perSample = stream { rateValues[it] }

        withClue("the block-rate oracle") { worstRelative(got) { perBlock[it] } shouldBeLessThan 1e-9 }
        withClue("the row can tell: a per-sample rate leaves the stream") { worstRelative(got) { perSample[it] } shouldBeGreaterThan 1e-6 }
    }
})
