/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.mul
import io.peekandpoke.klang.audio_bridge.pitchMod
import io.peekandpoke.klang.audio_bridge.pitchModSemitones
import io.peekandpoke.klang.audio_bridge.plus
import io.peekandpoke.klang.audio_bridge.vibrato
import kotlin.math.abs
import kotlin.math.pow
import kotlin.random.Random

/**
 * The law of `pitchModSemitones` (pitch pipeline 7a, decision D8): every pitched source under the node reads the ratio
 * `2^(mod / 12)`, frame by frame. The oracle is computed HERE, with `pow`, never with the engine's `fastExp2`.
 *
 * **The seam.** The pitched source under test is the [IgnitorDsl.Sample] leaf over a [RatioProbe]: it records the
 * ratio stream it reads (`ctx.phaseMod`, 1.0 where none), which is exactly what an oscillator multiplies its phase
 * increment by. So a row reads the ratio itself, not a frequency estimated from a waveform. The probe also counts its
 * calls (one per block, or the mod rendered twice) and records, frame by frame, whether a pitch mod reached it at all
 * (`ctx.phaseMod` non-null), so a row can tell a built node writing 1.0 from no node.
 *
 * **The gate** (review round 1): a LITERAL mod of 0 or non-finite builds no node (`audio/ref/off-values.md`, row
 * `pitchModSemitones`). A signal mod is always built; [zeroSignal] and [nonLeaf] are the same values as signals, a
 * leaf behind an `OptimizerHint`, which dissolves at build but gives the gate no build-time answer.
 *
 * The tolerance is relative 1e-9: `fastExp2` is within 1e-10 of `2^x` in (-32, 32) and exact at every integer. The
 * constants are 7 and -7 semitones, not a whole octave, so the polynomial's fraction is in play (at a whole octave
 * `fastExp2` is exact whatever its polynomial does).
 */
class PitchModSemitonesSpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128
    val blocks = 12
    val frames = blocks * blockFrames
    val noteHz = 220.0

    fun c(v: Double) = IgnitorDsl.Constant(v)

    /** A sine LFO at a fixed rate and no drift: a modulator, not a pitched source. */
    fun lfo(rate: Double) = IgnitorDsl.Sine(freq = c(rate), analog = c(0.0))

    val probed = IgnitorDsl.Sample

    /** [v] as a SIGNAL: the gate cannot read it at build, the runtime reads [v]. */
    fun nonLeaf(v: Double) = IgnitorDsl.OptimizerHint(inner = c(v))

    val zeroSignal = nonLeaf(0.0)

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

    fun context(random: Random) = IgniteContext(
        sampleRate = sampleRate,
        voiceDurationFrames = frames,
        gateEndFrame = frames,
        scratchBuffers = ScratchBuffers(blockFrames),
        random = random,
    )

    /** Renders [dsl] block by block and hands back its output; [sampleSource] serves its [IgnitorDsl.Sample] leaves. */
    fun render(dsl: IgnitorDsl, sampleSource: Ignitor? = null, seed: Int = 7): DoubleArray {
        val ctx = context(Random(seed))
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

    /** The probe under [dsl], rendered; asks that the probed source is rendered once per block. */
    fun probe(dsl: IgnitorDsl): RatioProbe {
        val probe = RatioProbe(frames)

        render(dsl = dsl, sampleSource = probe)

        withClue("the probed source is called once per block") {
            probe.calls shouldBe blocks
        }

        return probe
    }

    /** The ratio stream the probed source under [dsl] reads, every frame through a built node. */
    fun ratiosOf(dsl: IgnitorDsl): DoubleArray {
        val probe = probe(dsl)

        withClue("a pitch mod reaches the probed source on every frame") {
            probe.modded.count { !it } shouldBe 0
        }

        return probe.ratios
    }

    /** Every frame of [got] is [expected] at that frame, to the relative tolerance 1e-9. */
    fun shouldFollow(got: DoubleArray, expected: (Int) -> Double) {
        var worst = 0.0

        for (i in got.indices) {
            worst = maxOf(worst, abs(got[i] / expected(i) - 1.0))
        }

        withClue("worst relative error against the oracle: $worst") {
            worst shouldBeLessThan 1e-9
        }
    }

    // ── The law ──────────────────────────────────────────────────────────────────────────────────────────────

    "a constant 7 semitones: every frame reads 2^(7/12), a fifth up" {
        val got = ratiosOf(probed.pitchModSemitones(7.0))
        val fifth = 2.0.pow(7.0 / 12.0)

        shouldFollow(got) { fifth }
    }

    "a negative amount, -7 semitones: every frame reads 2^(-7/12), a fifth down" {
        val got = ratiosOf(probed.pitchModSemitones(-7.0))

        shouldFollow(got) { 2.0.pow(-7.0 / 12.0) }
    }

    "a signal: the ratio follows 2^(lfo / 12) frame by frame, and the LFO advances once per block" {
        // 40 Hz over 1536 frames at 48 kHz is 1.28 cycles: both signs, three semitones deep.
        val mod = lfo(40.0).mul(3.0)
        // The same LFO rendered alone: its values are the oracle's input. A tree that rendered its mod twice per
        // block would run it at twice the rate and leave this stream.
        val lfoValues = render(mod)
        val got = ratiosOf(probed.pitchModSemitones(mod))

        lfoValues.max() shouldBeGreaterThan 2.9
        lfoValues.min() shouldBeLessThan -2.9
        shouldFollow(got) { 2.0.pow(lfoValues[it] / 12.0) }
    }

    "two semitone nodes nested sum their semitones: 7 inside 5 is the octave" {
        shouldFollow(ratiosOf(probed.pitchModSemitones(7.0).pitchModSemitones(5.0))) { 2.0 }
    }

    // ── The gate and the identity ────────────────────────────────────────────────────────────────────────────

    "a literal 0 builds no node: no pitch mod reaches the source, as a Constant and as a Param" {
        probe(probed.pitchModSemitones(0.0)).modded.count { it } shouldBe 0
        probe(probed.pitchModSemitones(IgnitorDsl.Param("pms", 0.0))).modded.count { it } shouldBe 0
    }

    "a non-zero literal is built: the source reads the mod on every frame" {
        probe(probed.pitchModSemitones(7.0)).modded.count { !it } shouldBe 0
        probe(probed.pitchModSemitones(IgnitorDsl.Param("pms", -0.5))).modded.count { !it } shouldBe 0
    }

    "a signal at 0 is built and writes exactly 1.0 on every frame" {
        ratiosOf(probed.pitchModSemitones(zeroSignal)).count { it != 1.0 } shouldBe 0
    }

    listOf(
        "a sine with its drift" to IgnitorDsl.Sine(),
        "a saw" to IgnitorDsl.Saw(),
        "a supersaw" to IgnitorDsl.SuperSaw(),
        "a pluck" to IgnitorDsl.Pluck(),
    ).forEach { (name, source) ->
        "a signal at 0 renders $name bit for bit as the source without the node" {
            // The probe beside the source, under the same node, proves the node is built on this very tree.
            val bareProbe = RatioProbe(frames)
            val nodeProbe = RatioProbe(frames)
            val bare = render(dsl = source + probed, sampleSource = bareProbe)
            val withNode = render(dsl = (source + probed).pitchModSemitones(zeroSignal), sampleSource = nodeProbe)

            nodeProbe.modded.count { !it } shouldBe 0
            bareProbe.modded.count { it } shouldBe 0
            bare.maxOf { abs(it) } shouldBeGreaterThan 0.01
            withNode.toList() shouldBe bare.toList()
        }
    }

    // ── Nesting: the products compose ────────────────────────────────────────────────────────────────────────

    "under and over pitchMod: the ratio is the product, 2^(7/12) times 1 + 0.25" {
        val expected = 2.0.pow(7.0 / 12.0) * 1.25

        shouldFollow(ratiosOf(probed.pitchModSemitones(7.0).pitchMod(c(0.25)))) { expected }
        shouldFollow(ratiosOf(probed.pitchMod(c(0.25)).pitchModSemitones(7.0))) { expected }
    }

    "under and over a vibrato: the ratio is the vibrato's times 2^(7/12), frame by frame" {
        val vibrato = ratiosOf(probed.vibrato(rate = 6.0, semitones = 0.5))
        val fifth = 2.0.pow(7.0 / 12.0)

        vibrato.max() shouldBeGreaterThan 1.0
        shouldFollow(ratiosOf(probed.pitchModSemitones(7.0).vibrato(rate = 6.0, semitones = 0.5))) { vibrato[it] * fifth }
        shouldFollow(ratiosOf(probed.vibrato(rate = 6.0, semitones = 0.5).pitchModSemitones(7.0))) { vibrato[it] * fifth }
    }

    // ── Non-finite and huge amounts: a literal is gated, a signal's sample meets safeOut (no clamp below SAFE_MAX) ─

    "a non-finite literal builds no node: NaN and both infinities leave the source unmodulated" {
        for (v in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            withClue("literal $v") {
                probe(probed.pitchModSemitones(v)).modded.count { it } shouldBe 0
            }
        }
    }

    "a non-finite literal renders the bare saw, bit for bit" {
        val saw = IgnitorDsl.Saw(analog = c(0.0))

        render(saw.pitchModSemitones(Double.NaN)).toList() shouldBe render(saw).toList()
    }

    "a non-finite signal sample: NaN and -Infinity read as the ratio 0 (the source holds still), +Infinity as 1e15" {
        ratiosOf(probed.pitchModSemitones(nonLeaf(Double.NaN))).count { it != 0.0 } shouldBe 0
        ratiosOf(probed.pitchModSemitones(nonLeaf(Double.NEGATIVE_INFINITY))).count { it != 0.0 } shouldBe 0
        ratiosOf(probed.pitchModSemitones(nonLeaf(Double.POSITIVE_INFINITY))).count { it != 1e15 } shouldBe 0
    }

    "598 semitones is the first whole amount past 1e15: it holds there, while 597 passes raw" {
        // 12 * log2(1e15) is about 597.95.
        ratiosOf(probed.pitchModSemitones(598.0)).count { it != 1e15 } shouldBe 0
        shouldFollow(ratiosOf(probed.pitchModSemitones(597.0))) { 2.0.pow(597.0 / 12.0) }
        (2.0.pow(597.0 / 12.0) < 1e15) shouldBe true
    }

    "a NaN signal sample holds a saw still: one finite value on every frame, the DC of its first phase" {
        val out = render(IgnitorDsl.Saw(analog = c(0.0)).pitchModSemitones(nonLeaf(Double.NaN)))

        out.all { it.isFinite() } shouldBe true
        out.toSet().size shouldBe 1
    }
})
