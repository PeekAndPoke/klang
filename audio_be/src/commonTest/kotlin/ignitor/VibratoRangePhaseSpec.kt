/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.utils.SAFE_MAX
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.mul
import io.peekandpoke.klang.audio_bridge.plus
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * **The vibrato's `range` and `phase`** (pitch pipeline 7c). The LFO is laid onto `from..to` (its -1 to `from`, its +1
 * to `to`) before the depth scales it, and starts at `phase` (a fraction of one cycle, the oscillators' knob), so every
 * pitched source under the node reads
 *
 * ```
 * ratio(n) = 2^((from + (sin(2 pi (rate n / sr + phase)) + 1) / 2 * (to - from)) * semitones / 12)
 * ```
 *
 * written out here with the library's `sin` and `pow` (the oracle); the engine's `fastSin` and `fastExp2` sit well
 * inside the 1e-9 relative tolerance. **The default builds neither**: a literal `(-1, 1)` and a literal phase 0 (a
 * `Constant` or a `classic()` slot) render the unranged composition of 7b bit for bit, while a BUILT `range(-1, 1)`
 * (behind a non-leaf) is not the identity in floating point.
 *
 * The seam is `VibratoCompositionSpec`'s: the [IgnitorDsl.Sample] leaf over a [RatioProbe] that records the ratio
 * stream it reads. The edge rules have one home, the `vibrato` row of `audio/ref/off-values.md`.
 */
class VibratoRangePhaseSpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128
    val blocks = 72 // 9216 frames: past one whole cycle of the 6 Hz LFO (8000 frames)
    val frames = blocks * blockFrames
    val noteHz = 220.0
    val rate = 6.0
    val semitones = 0.5

    fun c(v: Double) = IgnitorDsl.Constant(v)

    /** [v] as a non-leaf: the build-time literal read cannot answer it, the runtime builds and reads it. */
    fun nonLeaf(v: Double) = IgnitorDsl.OptimizerHint(inner = c(v))

    fun vibrato(
        rangeFrom: IgnitorDsl = c(-1.0),
        rangeTo: IgnitorDsl = c(1.0),
        phase: IgnitorDsl = c(0.0),
    ) = IgnitorDsl.Vibrato(
        inner = IgnitorDsl.Sample,
        rate = c(rate),
        semitones = c(semitones),
        rangeFrom = rangeFrom,
        rangeTo = rangeTo,
        phase = phase,
    )

    /** Records the ratio the [IgnitorDsl.Sample] leaf reads, frame by frame. */
    class RatioProbe(frames: Int) : Ignitor {
        val ratios = DoubleArray(frames)
        var modded = 0

        override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
            val pm = ctx.phaseMod

            for (i in ctx.offset until ctx.windowEnd) {
                val frame = ctx.voiceElapsedFrames + (i - ctx.offset)

                ratios[frame] = pm?.get(i) ?: 1.0
                buffer[i] = 0.0
            }

            if (pm != null) {
                modded++
            }
        }
    }

    /** The ratio stream the probed source under [dsl] reads, built with [bag]; asks for a pitch mod on every block. */
    fun ratiosOf(dsl: IgnitorDsl, bag: Map<String, Double>? = null): DoubleArray {
        val probe = RatioProbe(frames)
        val ctx = IgniteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = frames,
            gateEndFrame = frames,
            scratchBuffers = ScratchBuffers(blockFrames),
            random = Random(7),
        )
        val built = dsl.buildExciter(ignitorParams = bag, freqHz = noteHz, sampleRate = sampleRate, random = ctx.random, sampleSource = probe).ignitor
        val buffer = AudioBuffer(blockFrames)

        for (b in 0 until blocks) {
            ctx.updateOffsetAndLength(offset = 0, length = blockFrames)
            ctx.voiceElapsedFrames = b * blockFrames
            built.generate(buffer, noteHz, ctx)
        }

        withClue("a pitch mod reaches the probed source on every block") {
            probe.modded shouldBe blocks
        }

        return probe.ratios
    }

    /**
     * 7b's unranged composition written as the plain DSL, `pitchModSemitones(sine(rate, analog 0) * semitones)`: at a
     * constant depth above 0 the vibrato's `max(semitones, 0)` and its LFO shield change no bit. The rows that say "the
     * default's bits" compare against THIS, not against another vibrato, so a range or phase input built at the default
     * shows.
     */
    fun unrangedRatios(): List<Double> =
        ratiosOf(IgnitorDsl.PitchModSemitones(inner = IgnitorDsl.Sample, mod = IgnitorDsl.Sine(freq = c(rate), analog = c(0.0)).mul(c(semitones)))).toList()

    /** The law, written out: the LFO laid onto from..to, scaled by the depth, in semitones. */
    fun oracle(n: Int, from: Double, to: Double, phase: Double = 0.0, fromAt: (Int) -> Double = { from }): Double {
        val lfo = sin(2.0 * PI * (rate * n / sampleRate + phase))
        val f = fromAt(n)
        val swing = f + (lfo + 1.0) / 2.0 * (to - f)

        return 2.0.pow(swing * semitones / 12.0)
    }

    /** The first frame where [got] leaves [expected] by more than 1e-9 relative, or null. */
    fun firstOff(got: DoubleArray, expected: (Int) -> Double): Int? =
        got.indices.firstOrNull { abs(got[it] / expected(it) - 1.0) > 1e-9 }

    // ── The default: no range, no phase input, 7b's bits ──────────────────────────────────────────────────────

    "the default builds no range and no phase input: the unranged composition, bit for bit" {
        val unranged = unrangedRatios()

        ratiosOf(vibrato()).toList() shouldBe unranged
        ratiosOf(IgnitorDsl.Vibrato(inner = IgnitorDsl.Sample, rate = c(rate), semitones = c(semitones))).toList() shouldBe unranged
    }

    "a BUILT range(-1, 1) is not the identity: it follows the law but leaves the default's bits" {
        val unranged = unrangedRatios()
        val built = ratiosOf(vibrato(rangeFrom = nonLeaf(-1.0), rangeTo = nonLeaf(1.0)))
        val differing = built.indices.count { built[it] != unranged[it] }

        withClue("frames that differ by a rounding") { differing shouldBeGreaterThan 0 }
        firstOff(built) { oracle(n = it, from = -1.0, to = 1.0) } shouldBe null
    }

    // ── The range: the law ────────────────────────────────────────────────────────────────────────────────────

    for ((from, to, title) in listOf(
        Triple(0.0, 1.0, "range(0, 1): only upward, from the note"),
        Triple(-1.0, 0.0, "range(-1, 0): only downward, to the note"),
        Triple(1.0, -1.0, "range(1, -1), inverted: the LFO upside down"),
        Triple(0.0, 2.0, "range(0, 2): twice the depth upward, no clamp"),
    )) {
        "$title follows the law, frame by frame" {
            val ratios = ratiosOf(vibrato(rangeFrom = c(from), rangeTo = c(to)))

            firstOff(ratios) { oracle(n = it, from = from, to = to) } shouldBe null

            val lo = ratios.min()
            val hi = ratios.max()

            withClue("the swing spans 2^(from * d / 12) .. 2^(to * d / 12)") {
                abs(lo - 2.0.pow(minOf(from, to) * semitones / 12.0)) shouldBeLessThan 1e-4
                abs(hi - 2.0.pow(maxOf(from, to) * semitones / 12.0)) shouldBeLessThan 1e-4
            }
        }
    }

    "range(0, 1) never goes below the note, range(-1, 0) never above it" {
        ratiosOf(vibrato(rangeFrom = c(0.0), rangeTo = c(1.0))).all { it >= 1.0 } shouldBe true
        ratiosOf(vibrato(rangeFrom = c(-1.0), rangeTo = c(0.0))).all { it <= 1.0 } shouldBe true
    }

    "a SIGNAL from (a 2 Hz sine, half deep) is read per sample" {
        val from = IgnitorDsl.Sine(freq = c(2.0), analog = c(0.0)).mul(c(0.5))
        val ratios = ratiosOf(vibrato(rangeFrom = from, rangeTo = c(1.0)))

        firstOff(ratios) { n -> oracle(n = n, from = 0.0, to = 1.0, fromAt = { 0.5 * sin(2.0 * PI * 2.0 * it / sampleRate) }) } shouldBe null
    }

    // ── The phase ─────────────────────────────────────────────────────────────────────────────────────────────

    for (phase in listOf(0.0, 0.25, 0.5, 0.75)) {
        "phase $phase follows the law, frame by frame" {
            val ratios = ratiosOf(vibrato(phase = c(phase)))

            firstOff(ratios) { oracle(n = it, from = -1.0, to = 1.0, phase = phase) } shouldBe null
        }
    }

    "the phase decides the first frames (the default range): 0 on the note rising, 0.25 the top, 0.5 the note falling, 0.75 the bottom" {
        val top = 2.0.pow(semitones / 12.0)
        val bottom = 2.0.pow(-semitones / 12.0)

        val p0 = ratiosOf(vibrato(phase = c(0.0)))
        val p25 = ratiosOf(vibrato(phase = c(0.25)))
        val p50 = ratiosOf(vibrato(phase = c(0.5)))
        val p75 = ratiosOf(vibrato(phase = c(0.75)))

        abs(p0[0] - 1.0) shouldBeLessThan 1e-9
        p0[1] shouldBeGreaterThan 1.0
        abs(p25[0] - top) shouldBeLessThan 1e-9
        p25[1] shouldBeLessThan p25[0]
        abs(p50[0] - 1.0) shouldBeLessThan 1e-9
        p50[1] shouldBeLessThan 1.0
        abs(p75[0] - bottom) shouldBeLessThan 1e-9
        p75[1] shouldBeGreaterThan p75[0]
    }

    "under range(0, 1) phase 0 starts mid-swing, a quarter of the depth sharp; phase 0.75 starts on the note, rising" {
        // Phase 0 is the sine's `sin(0)`, which the range maps to the middle of the swing (review round 1, B1): on the
        // note only for a range centred on 0. The guitar's upward vibrato starts on the note at 0.75.
        val p0 = ratiosOf(vibrato(rangeFrom = c(0.0), rangeTo = c(1.0), phase = c(0.0)))
        val p75 = ratiosOf(vibrato(rangeFrom = c(0.0), rangeTo = c(1.0), phase = c(0.75)))

        withClue("phase 0: +25 cents at 0.5 semitones, rising") {
            abs(p0[0] - 2.0.pow(0.5 * semitones / 12.0)) shouldBeLessThan 1e-9
            p0[1] shouldBeGreaterThan p0[0]
        }
        withClue("phase 0.75: on the note, rising, never below it") {
            abs(p75[0] - 1.0) shouldBeLessThan 1e-9
            p75[1] shouldBeGreaterThan p75[0]
            p75.all { it >= 1.0 - 1e-12 } shouldBe true
        }
        firstOff(p0) { oracle(n = it, from = 0.0, to = 1.0, phase = 0.0) } shouldBe null
        firstOff(p75) { oracle(n = it, from = 0.0, to = 1.0, phase = 0.75) } shouldBe null
    }

    "the phase wraps: 1.25 is 0.25 and -0.25 is 0.75, to the law's tolerance" {
        firstOff(ratiosOf(vibrato(phase = c(1.25)))) { oracle(n = it, from = -1.0, to = 1.0, phase = 0.25) } shouldBe null
        firstOff(ratiosOf(vibrato(phase = c(-0.25)))) { oracle(n = it, from = -1.0, to = 1.0, phase = 0.75) } shouldBe null
    }

    "range and phase together" {
        val ratios = ratiosOf(vibrato(rangeFrom = c(0.0), rangeTo = c(1.0), phase = c(0.25)))

        firstOff(ratios) { oracle(n = it, from = 0.0, to = 1.0, phase = 0.25) } shouldBe null
    }

    // ── The classic() slots ───────────────────────────────────────────────────────────────────────────────────

    "the slots: unwritten (or written at the defaults) they build nothing, written they follow the law" {
        val s = IgnitorDsl.Slots.vibrato
        val slotted = vibrato(rangeFrom = s.rangeFrom, rangeTo = s.rangeTo, phase = s.phase)
        val unranged = unrangedRatios()

        withClue("unwritten") { ratiosOf(slotted).toList() shouldBe unranged }
        withClue("written at the defaults") {
            ratiosOf(slotted, mapOf("vibrato.rangeFrom" to -1.0, "vibrato.rangeTo" to 1.0, "vibrato.phase" to 0.0)).toList() shouldBe unranged
        }
        withClue("written") {
            val ratios = ratiosOf(slotted, mapOf("vibrato.rangeFrom" to 0.0, "vibrato.rangeTo" to 1.0, "vibrato.phase" to 0.25))

            firstOff(ratios) { oracle(n = it, from = 0.0, to = 1.0, phase = 0.25) } shouldBe null
        }
        withClue("only rangeFrom written: rangeTo is the slot's 1") {
            firstOff(ratiosOf(slotted, mapOf("vibrato.rangeFrom" to 0.0))) { oracle(n = it, from = 0.0, to = 1.0) } shouldBe null
        }
    }

    "a non-finite value written raw into a slot reads as unset: the slot's default, nothing built" {
        val s = IgnitorDsl.Slots.vibrato
        val slotted = vibrato(rangeFrom = s.rangeFrom, rangeTo = s.rangeTo, phase = s.phase)
        val unranged = unrangedRatios()

        for (v in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            withClue("$v") {
                ratiosOf(slotted, mapOf("vibrato.rangeFrom" to v, "vibrato.rangeTo" to v, "vibrato.phase" to v)).toList() shouldBe unranged
            }
        }
    }

    // ── Hostile values (checklist (b)) ────────────────────────────────────────────────────────────────────────

    "a non-finite LITERAL bound or phase reads as its default, as the vibrato's rate and depth do" {
        val unranged = unrangedRatios()

        for (v in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            withClue("from $v: the default (-1, 1), nothing built") { ratiosOf(vibrato(rangeFrom = c(v))).toList() shouldBe unranged }
            withClue("to $v: the default (-1, 1), nothing built") { ratiosOf(vibrato(rangeTo = c(v))).toList() shouldBe unranged }
            withClue("phase $v: 0, nothing built") { ratiosOf(vibrato(phase = c(v))).toList() shouldBe unranged }
            withClue("from $v beside to 0.5: range(-1, 0.5)") {
                firstOff(ratiosOf(vibrato(rangeFrom = c(v), rangeTo = c(0.5)))) { oracle(n = it, from = -1.0, to = 0.5) } shouldBe null
            }
        }
    }

    "a non-finite SIGNAL bound: NaN and an infinite from read as no vibrato (1.0), to +Infinity as SAFE_MAX, to -Infinity as 0" {
        // Unguarded, finite: the range turns the bound into NaN or an infinity, the depth multiply's `safeOut` makes that
        // 0 or +-SAFE_MAX, and the ratio is `fastExp2` of it (the open section 2 of the non-finite task).
        for (v in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            withClue("from $v") { ratiosOf(vibrato(rangeFrom = nonLeaf(v))).all { it == 1.0 } shouldBe true }
        }

        withClue("to NaN") { ratiosOf(vibrato(rangeTo = nonLeaf(Double.NaN))).all { it == 1.0 } shouldBe true }
        withClue("to +Infinity: SAFE_MAX wherever the LFO is above -1") {
            ratiosOf(vibrato(rangeTo = nonLeaf(Double.POSITIVE_INFINITY))).all { it == SAFE_MAX } shouldBe true
        }
        withClue("to -Infinity: 0, the source holds still") {
            ratiosOf(vibrato(rangeTo = nonLeaf(Double.NEGATIVE_INFINITY))).all { it == 0.0 } shouldBe true
        }
    }

    "a non-finite SIGNAL phase reads as 0: the default's bits (block-constant and per sample)" {
        val unranged = unrangedRatios()
        val zeroSignal = IgnitorDsl.Sine(freq = c(3.0), analog = c(0.0)).mul(c(0.0))

        for (v in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            withClue("block-constant $v") { ratiosOf(vibrato(phase = nonLeaf(v))).toList() shouldBe unranged }
            withClue("per sample $v") {
                ratiosOf(vibrato(phase = zeroSignal.plus(c(v)))).toList() shouldBe unranged
            }
        }
    }
})
