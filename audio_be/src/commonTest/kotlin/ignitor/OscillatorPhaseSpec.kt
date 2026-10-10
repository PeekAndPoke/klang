/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.childNodes
import io.peekandpoke.klang.audio_bridge.range
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/** TEST ONLY. A block-constant input whose value the test sets between blocks (a slot a pattern changes per block). */
private class SteppedConstant(var value: Double) : Ignitor {
    override val isBlockConstant: Boolean get() = true

    override fun controlRateValue(freqHz: Double): Double = value

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        buffer.fill(value, ctx.offset, ctx.windowEnd)
    }
}

/**
 * The `phase` knob of every periodic oscillator (`docs/tasks-archive/2026-10/20261006-oscillator-phase-knob.md`): an offset in cycles, added to
 * the running phase every sample, wrapping. Guards:
 *
 *  - DEFAULT: an oscillator with a phase input of 0 (a slot, so the input is built and read) renders bit for bit what
 *    the oscillator without one renders, with and without drift, under a pitch modulation, at a mid-block start. A
 *    moving phase that is 0 everywhere takes the phased loops and renders the same bits too (they advance exactly as
 *    the plain loops do).
 *  - WRAP: 1.25 is 0.25 and -0.25 is 0.75, bit for bit.
 *  - DELAYED START: a constant offset of a quarter cycle is the default oscillator with its first quarter cycle cut.
 *  - PHASE MODULATION: a moving phase is read per sample: a linear phase ramp is a frequency shift (bit for bit on the
 *    exact trapezoid clock), and a sine LFO on the phase of a sine is `sin(wt + 2 pi m(t))`.
 *  - THE STACK MOVES AS ONE: half a cycle on a supersine or a supertri negates it, with a spread, so every voice moved
 *    by the same half cycle and kept its own start phase. The same for a sine with partial banks.
 *  - THE IMPULSE spikes where the shifted phase passes 0 going forward, never on a step back.
 */
class OscillatorPhaseSpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128

    // 750 Hz at 48 kHz is 64 samples per cycle: the normalised increment 1/64 and every phase on the trapezoid clock
    // are exact binary fractions, so a shifted start and a cut start are the same doubles.
    val exactHz = 750.0
    val quarter = 16

    /** (offset, length) per block: a mid-block start, full blocks, a short last one. */
    val windows: List<Pair<Int, Int>> = buildList {
        add(37 to blockFrames - 37)
        repeat(24) { add(0 to blockFrames) }
        add(0 to 55)
    }

    fun ctx(phaseMod: DoubleArray? = null, seed: Int = 7): IgniteContext = IgniteContext(
        sampleRate = sampleRate,
        voiceDurationFrames = sampleRate,
        gateEndFrame = sampleRate,
        scratchBuffers = ScratchBuffers(blockFrames),
        random = Random(seed),
        phaseMod = phaseMod,
    )

    fun render(ignitor: Ignitor, freqHz: Double, blocks: List<Pair<Int, Int>> = windows, phaseMod: DoubleArray? = null): DoubleArray {
        val c = ctx(phaseMod)
        val buffer = AudioBuffer(blockFrames)
        val out = ArrayList<Double>()

        for ((offset, length) in blocks) {
            c.updateOffsetAndLength(offset = offset, length = length)
            ignitor.generate(buffer, freqHz, c)

            for (i in offset until offset + length) {
                out.add(buffer[i])
            }

            c.voiceElapsedFrames += length
        }

        return out.toDoubleArray()
    }

    fun full(n: Int): List<Pair<Int, Int>> = List(n) { 0 to blockFrames }

    fun build(dsl: IgnitorDsl, freqHz: Double, seed: Int = 7): Ignitor =
        dsl.buildExciter(ignitorParams = null, random = Random(seed), freqHz = freqHz, sampleRate = sampleRate).ignitor

    fun renderNode(dsl: IgnitorDsl, freqHz: Double, blocks: List<Pair<Int, Int>> = windows, seed: Int = 7): DoubleArray {
        val c = ctx(seed = seed)
        val ignitor = dsl.buildExciter(ignitorParams = null, random = c.random, freqHz = freqHz, sampleRate = sampleRate).ignitor
        val buffer = AudioBuffer(blockFrames)
        val out = ArrayList<Double>()

        for ((offset, length) in blocks) {
            c.updateOffsetAndLength(offset = offset, length = length)
            ignitor.generate(buffer, freqHz, c)

            for (i in offset until offset + length) {
                out.add(buffer[i])
            }

            c.voiceElapsedFrames += length
        }

        return out.toDoubleArray()
    }

    fun maxDiff(a: DoubleArray, b: DoubleArray): Double {
        a.size shouldBe b.size

        var m = 0.0

        for (i in a.indices) {
            m = maxOf(m, abs(a[i] - b[i]))
        }

        return m
    }

    fun bits(a: DoubleArray): List<Long> = a.map { it.toRawBits() }

    fun c(v: Double) = IgnitorDsl.Constant(v)

    /** Every periodic oscillator node, by name, with [phase] in its phase input and [analog] in its drift. */
    fun nodes(phase: IgnitorDsl, analog: IgnitorDsl = c(0.0)): Map<String, IgnitorDsl> = linkedMapOf(
        "sine" to IgnitorDsl.Sine(analog = analog, phase = phase),
        "sine with banks" to IgnitorDsl.Sine(analog = analog, harmonics = c(3.0), suboctaves = c(1.0), phase = phase),
        "sawtooth" to IgnitorDsl.Saw(analog = analog, phase = phase),
        "ramp" to IgnitorDsl.Ramp(analog = analog, phase = phase),
        "square (Pulze)" to IgnitorDsl.Pulze(analog = analog, phase = phase),
        "square (PWM)" to IgnitorDsl.Pulze(duty = IgnitorDsl.Sine(freq = c(3.0)).range(from = c(0.3), to = c(0.7)), analog = analog, phase = phase),
        "pulze" to IgnitorDsl.RawPulze(analog = analog, phase = phase),
        "square (internal)" to IgnitorDsl.Square(analog = analog, phase = phase),
        "tri" to IgnitorDsl.Tri(analog = analog, phase = phase),
        "zawtooth" to IgnitorDsl.Zawtooth(analog = analog, phase = phase),
        "zamp" to IgnitorDsl.Zamp(analog = analog, phase = phase),
        "impulse" to IgnitorDsl.Impulse(analog = analog, phase = phase),
        "supersaw" to IgnitorDsl.SuperSaw(voices = c(5.0), analog = analog, phase = phase),
        "supersine" to IgnitorDsl.SuperSine(voices = c(5.0), analog = analog, phase = phase),
        "supersquare" to IgnitorDsl.SuperSquare(voices = c(5.0), analog = analog, phase = phase),
        "supertri" to IgnitorDsl.SuperTri(voices = c(5.0), analog = analog, phase = phase),
        "superramp" to IgnitorDsl.SuperRamp(voices = c(5.0), analog = analog, phase = phase),
    )

    // ── DEFAULT: bit for bit ─────────────────────────────────────────────────────────────────────

    "the default phase is the literal 0 on every oscillator node, built without naming it" {
        // every periodic node as its constructor's defaults, `phase` NOT passed, and the field read by name
        val defaults: Map<String, Pair<IgnitorDsl, IgnitorDsl>> = linkedMapOf(
            "sine" to IgnitorDsl.Sine().let { it to it.phase },
            "sawtooth" to IgnitorDsl.Saw().let { it to it.phase },
            "ramp" to IgnitorDsl.Ramp().let { it to it.phase },
            "square (Pulze)" to IgnitorDsl.Pulze().let { it to it.phase },
            "pulze" to IgnitorDsl.RawPulze().let { it to it.phase },
            "square (internal)" to IgnitorDsl.Square().let { it to it.phase },
            "tri" to IgnitorDsl.Tri().let { it to it.phase },
            "zawtooth" to IgnitorDsl.Zawtooth().let { it to it.phase },
            "zamp" to IgnitorDsl.Zamp().let { it to it.phase },
            "impulse" to IgnitorDsl.Impulse().let { it to it.phase },
            "supersaw" to IgnitorDsl.SuperSaw().let { it to it.phase },
            "supersine" to IgnitorDsl.SuperSine().let { it to it.phase },
            "supersquare" to IgnitorDsl.SuperSquare().let { it to it.phase },
            "supertri" to IgnitorDsl.SuperTri().let { it to it.phase },
            "superramp" to IgnitorDsl.SuperRamp().let { it to it.phase },
        )

        defaults.size shouldBe nodes(c(0.0)).size - 2 // the bank and the PWM square are configurations, not nodes

        for ((name, pair) in defaults) {
            withClue(name) {
                pair.second shouldBe c(0.0)
                pair.first.childNodes().last() shouldBe c(0.0)
            }
        }
    }

    // ── THE ZERO POINTS: where phase 0 is, per shape (each builder's KDoc names it) ─────────────

    "every shape starts where its KDoc says: the first samples at phase 0" {
        // 750 Hz: 64 samples per cycle, no drift
        fun first(node: IgnitorDsl): DoubleArray = renderNode(node, exactHz, full(1)).copyOfRange(0, 64)

        withClue("sine: sin(0), rising") {
            val x = first(IgnitorDsl.Sine(analog = c(0.0)))

            abs(x[0]) shouldBeLessThan 1e-12
            (x[1] > 0.0) shouldBe true
            abs(x[16] - 1.0) shouldBeLessThan 1e-10 // the peak a quarter cycle in
        }

        for ((name, node) in listOf(
            "sawtooth" to IgnitorDsl.Saw(analog = c(0.0)),
            "zawtooth" to IgnitorDsl.Zawtooth(analog = c(0.0)),
        )) {
            withClue("$name: -1, the bottom of the rise") {
                val x = first(node)

                x[0] shouldBe -1.0
                (x[1] > x[0]) shouldBe true
            }
        }

        for ((name, node) in listOf(
            "ramp" to IgnitorDsl.Ramp(analog = c(0.0)),
            "zamp" to IgnitorDsl.Zamp(analog = c(0.0)),
        )) {
            withClue("$name: +1, the top of the fall") {
                val x = first(node)

                x[0] shouldBe 1.0
                (x[1] < x[0]) shouldBe true
            }
        }

        for ((name, node) in listOf(
            "square (Pulze)" to IgnitorDsl.Pulze(analog = c(0.0)),
            "square (internal)" to IgnitorDsl.Square(analog = c(0.0)),
        )) {
            withClue("$name: -1, the foot of the rising edge, then high") {
                val x = first(node)

                x[0] shouldBe -1.0
                (x[1] > x[0]) shouldBe true
                x[8] shouldBe 1.0
                x[40] shouldBe -1.0
            }
        }

        withClue("pulze: +1, the start of the high plateau (the instant edge sits at the wrap)") {
            val x = first(IgnitorDsl.RawPulze(analog = c(0.0)))

            x[0] shouldBe 1.0
            x[31] shouldBe 1.0
            x[32] shouldBe -1.0
            x[63] shouldBe -1.0
        }

        withClue("triangle: -1, its lowest point, +1 at half a cycle") {
            val x = first(IgnitorDsl.Tri(analog = c(0.0)))

            x[0] shouldBe -1.0
            x[32] shouldBe 1.0
        }

        withClue("impulse: the note starts on the spike, the next one a cycle later") {
            val x = renderNode(IgnitorDsl.Impulse(analog = c(0.0)), exactHz, full(1))

            val spikes = x.indices.filter { x[it] == 1.0 }

            x[0] shouldBe 1.0
            spikes.size shouldBe 2
            // the radian clock lands on 2 pi to rounding: the second spike on sample 64 or the one after
            (spikes[1] in 64..65) shouldBe true
        }
    }

    "a phase input of 0 renders bit for bit what the oscillator without one renders, with and without drift" {
        for (analog in listOf(c(0.0), c(2.0))) {
            val plain = nodes(phase = c(0.0), analog = analog)
            val slotted = nodes(phase = IgnitorDsl.Param("ph", 0.0), analog = analog)
            // a moving phase that is 0 everywhere (the product of an oscillator and 0 is no block constant)
            val zeroSignal = nodes(phase = IgnitorDsl.Times(left = IgnitorDsl.Sine(freq = c(3.0)), right = c(0.0)), analog = analog)

            for (name in plain.keys) {
                withClue("$name, analog $analog") {
                    val expected = bits(renderNode(plain.getValue(name), 220.0))

                    bits(renderNode(slotted.getValue(name), 220.0)) shouldBe expected
                    bits(renderNode(zeroSignal.getValue(name), 220.0)) shouldBe expected
                }
            }
        }
    }

    "a phase input of 0 stays bit for bit under a pitch modulation (the phaseMod paths)" {
        val pm = DoubleArray(blockFrames) { 1.0 + 0.01 * sin(it * 0.05) }
        val factories: Map<String, (Ignitor?) -> Ignitor> = linkedMapOf(
            "sine" to { p -> Ignitors.sine(phase = p) },
            "sawtooth" to { p -> Ignitors.saw(phase = p) },
            "tri" to { p -> Ignitors.tri(phase = p) },
            "impulse" to { p -> Ignitors.impulse(phase = p) },
            "supersaw" to { p -> Ignitors.superSaw(voices = ConstantIgnitor(3.0), rng = Random(3), phase = p) },
            "supersine" to { p -> Ignitors.superSine(voices = ConstantIgnitor(3.0), rng = Random(3), phase = p) },
        )

        for ((name, make) in factories) {
            withClue(name) {
                val expected = bits(render(make(null), 220.0, full(12), pm))

                bits(render(make(ConstantIgnitor(0.0)), 220.0, full(12), pm)) shouldBe expected
                bits(render(make(ArrayIgnitor(DoubleArray(12 * blockFrames))), 220.0, full(12), pm)) shouldBe expected
            }
        }
    }

    // ── WRAP ────────────────────────────────────────────────────────────────────────────────────

    "the phase wraps: 1.25 is 0.25 and -0.25 is 0.75, bit for bit, and a whole cycle is no shift at all" {
        val q = nodes(c(0.25))
        val qWrapped = nodes(c(1.25))
        val tq = nodes(c(0.75))
        val tqWrapped = nodes(c(-0.25))
        val plain = nodes(c(0.0))
        val whole = nodes(c(2.0))

        for (name in q.keys) {
            withClue(name) {
                val shifted = renderNode(q.getValue(name), 220.0)

                bits(renderNode(qWrapped.getValue(name), 220.0)) shouldBe bits(shifted)
                bits(renderNode(tqWrapped.getValue(name), 220.0)) shouldBe bits(renderNode(tq.getValue(name), 220.0))
                bits(renderNode(whole.getValue(name), 220.0)) shouldBe bits(renderNode(plain.getValue(name), 220.0))

                // not vacuous: a quarter cycle moved the samples
                bits(shifted) shouldNotBe bits(renderNode(plain.getValue(name), 220.0))
            }
        }
    }

    "a moving phase wraps the same way: a signal at 1.25 is a signal at 0.25, -0.25 is 0.75, bit for bit" {
        val factories: Map<String, (Ignitor?) -> Ignitor> = linkedMapOf(
            "sine" to { p -> Ignitors.sine(phase = p) },
            "sawtooth" to { p -> Ignitors.saw(phase = p) },
            "tri" to { p -> Ignitors.tri(phase = p) },
            "impulse" to { p -> Ignitors.impulse(phase = p) },
            "sine with banks" to { p -> Ignitors.sinePartials(harmonics = ConstantIgnitor(3.0), phase = p) },
            "supersaw" to { p -> Ignitors.superSaw(voices = ConstantIgnitor(3.0), rng = Random(3), phase = p) },
            "supersine" to { p -> Ignitors.superSine(voices = ConstantIgnitor(3.0), rng = Random(3), phase = p) },
        )

        fun signal(v: Double) = ArrayIgnitor(DoubleArray(4000) { v })

        for ((name, make) in factories) {
            withClue(name) {
                bits(render(make(signal(1.25)), 220.0)) shouldBe bits(render(make(signal(0.25)), 220.0))
                bits(render(make(signal(-0.25)), 220.0)) shouldBe bits(render(make(signal(0.75)), 220.0))
            }
        }
    }

    "a non-finite phase reads as unset, no shift" {
        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            withClue("phase $bad") {
                bits(render(Ignitors.saw(phase = ConstantIgnitor(bad)), 220.0)) shouldBe bits(render(Ignitors.saw(), 220.0))
                bits(render(Ignitors.sine(phase = ConstantIgnitor(bad)), 220.0)) shouldBe bits(render(Ignitors.sine(), 220.0))
            }
        }
    }

    // ── DELAYED START ───────────────────────────────────────────────────────────────────────────

    "a constant quarter cycle is the default oscillator with its first quarter cycle cut" {
        val n = 10 * blockFrames
        val single: Map<String, (Ignitor?) -> Ignitor> = linkedMapOf(
            "sawtooth" to { p -> Ignitors.saw(phase = p) },
            "ramp" to { p -> Ignitors.ramp(phase = p) },
            "square" to { p -> Ignitors.square(phase = p) },
            "pulze" to { p -> Ignitors.rawPulze(phase = p) },
            "tri" to { p -> Ignitors.tri(phase = p) },
            "zawtooth" to { p -> Ignitors.zawtooth(phase = p) },
            "zamp" to { p -> Ignitors.zamp(phase = p) },
        )

        // The trapezoids run on the exact clock: the same doubles. The radian sine sums its increments in another
        // order, so it agrees to rounding.
        for ((name, make) in single) {
            withClue(name) {
                val shifted = render(make(ConstantIgnitor(0.25)), exactHz, full(10))
                val cut = render(make(null), exactHz, full(11)).copyOfRange(quarter, quarter + n)

                bits(shifted) shouldBe bits(cut)
            }
        }

        val sineShifted = render(Ignitors.sine(phase = ConstantIgnitor(0.25)), exactHz, full(10))
        val sineCut = render(Ignitors.sine(), exactHz, full(11)).copyOfRange(quarter, quarter + n)

        maxDiff(a = sineShifted, b = sineCut) shouldBeLessThan 1e-12
        // a sine a quarter cycle on starts at its peak (the polynomial sine is within about 1.3e-11 of 1 there)
        abs(sineShifted[0] - 1.0) shouldBeLessThan 1e-10
    }

    "the stacks: a constant quarter cycle is the stack with its first quarter cycle cut (spread 0, every voice on one clock)" {
        val n = 10 * blockFrames
        val stacks: Map<String, (Ignitor?) -> Ignitor> = linkedMapOf(
            "supersaw" to { p -> Ignitors.superSaw(voices = ConstantIgnitor(5.0), detune = ConstantIgnitor(0.0), rng = Random(3), phase = p) },
            "superramp" to { p -> Ignitors.superRamp(voices = ConstantIgnitor(5.0), detune = ConstantIgnitor(0.0), rng = Random(3), phase = p) },
            "supersquare" to { p -> Ignitors.superSquare(voices = ConstantIgnitor(5.0), detune = ConstantIgnitor(0.0), rng = Random(3), phase = p) },
            "supertri" to { p -> Ignitors.superTri(voices = ConstantIgnitor(5.0), detune = ConstantIgnitor(0.0), rng = Random(3), phase = p) },
            "supersine" to { p -> Ignitors.superSine(voices = ConstantIgnitor(5.0), detune = ConstantIgnitor(0.0), rng = Random(3), phase = p) },
        )

        for ((name, make) in stacks) {
            withClue(name) {
                val shifted = render(make(ConstantIgnitor(0.25)), exactHz, full(10))
                val cut = render(make(null), exactHz, full(11)).copyOfRange(quarter, quarter + n)

                // the random start phases are not on the exact clock: agreement to rounding
                maxDiff(a = shifted, b = cut) shouldBeLessThan 1e-12
                maxDiff(a = shifted, b = render(make(null), exactHz, full(10))) shouldNotBe 0.0
            }
        }
    }

    // ── PHASE MODULATION ────────────────────────────────────────────────────────────────────────

    "a moving phase is read per sample: a linear phase ramp of 1/128 cycle per sample is the oscillator at 1.5 times its frequency" {
        val n = 12 * blockFrames
        val ramp = DoubleArray(n) { it / 128.0 }
        // The shapes whose edges do not depend on the frequency (the soft edges of the saw, ramp and square are sized
        // from the oscillator's own increment, which a phase does not change), so the faster oscillator is the same
        // waveform, on the exact clock: the same doubles.
        val single: Map<String, (Ignitor?) -> Ignitor> = linkedMapOf(
            "zawtooth" to { p -> Ignitors.zawtooth(phase = p) },
            "zamp" to { p -> Ignitors.zamp(phase = p) },
            "pulze" to { p -> Ignitors.rawPulze(phase = p) },
            "tri" to { p -> Ignitors.tri(phase = p) },
        )

        for ((name, make) in single) {
            withClue(name) {
                bits(render(make(ArrayIgnitor(ramp)), exactHz, full(12))) shouldBe bits(render(make(null), exactHz * 1.5, full(12)))
            }
        }

        val sineModulated = render(Ignitors.sine(phase = ArrayIgnitor(ramp)), exactHz, full(12))

        maxDiff(a = sineModulated, b = render(Ignitors.sine(), exactHz * 1.5, full(12))) shouldBeLessThan 1e-9

        // the stacks read the same offsets on every voice: a ramp shifts the whole stack's frequency (the supertri: its
        // flanks are fully open, so like the triangle its shape does not depend on the frequency)
        val stack = { p: Ignitor? -> Ignitors.superTri(voices = ConstantIgnitor(5.0), detune = ConstantIgnitor(0.0), rng = Random(3), phase = p) }

        maxDiff(a = render(stack(ArrayIgnitor(ramp)), exactHz, full(12)), b = render(stack(null), exactHz * 1.5, full(12))) shouldBeLessThan 1e-9
    }

    "a sine LFO on the phase of a sine is sin(wt + 2 pi m(t)): phase modulation" {
        val n = 12 * blockFrames
        val lfo = render(Ignitors.sine(ConstantIgnitor(30.0)).mul(0.3), 0.0, full(12))
        val modulated = render(Ignitors.sine(phase = Ignitors.sine(ConstantIgnitor(30.0)).mul(0.3)), 440.0, full(12))
        val expected = DoubleArray(n) { i ->
            val m = lfo[i] - floor(lfo[i])

            sin(2.0 * PI * 440.0 * i / sampleRate + 2.0 * PI * m)
        }

        maxDiff(a = modulated, b = expected) shouldBeLessThan 1e-8
        maxDiff(a = modulated, b = render(Ignitors.sine(), 440.0, full(12))) shouldNotBe 0.0
    }

    // ── THE STACK MOVES AS ONE ──────────────────────────────────────────────────────────────────

    "half a cycle negates a supersine and a supertri with a spread: every voice moved by the same half cycle" {
        val stacks: Map<String, (Ignitor?) -> Ignitor> = linkedMapOf(
            "supersine" to { p -> Ignitors.superSine(voices = ConstantIgnitor(7.0), detune = ConstantIgnitor(0.3), rng = Random(5), phase = p) },
            "supertri" to { p -> Ignitors.superTri(voices = ConstantIgnitor(7.0), detune = ConstantIgnitor(0.3), rng = Random(5), phase = p) },
            "sine with banks" to { p -> Ignitors.sinePartials(harmonics = ConstantIgnitor(4.0), octaves = ConstantIgnitor(2.0), phase = p) },
        )

        for ((name, make) in stacks) {
            withClue(name) {
                val plain = render(make(null), 220.0)
                val negated = DoubleArray(plain.size) { -plain[it] }

                maxDiff(a = render(make(ConstantIgnitor(0.5)), 220.0), b = negated) shouldBeLessThan 1e-12
                // the moving path: a constant half cycle as a signal
                maxDiff(a = render(make(ArrayIgnitor(DoubleArray(4000) { 0.5 })), 220.0), b = negated) shouldBeLessThan 1e-12
            }
        }
    }

    "a partial that joins a bank mid-note starts at the phase already applied" {
        val count = SteppedConstant(1.0)
        val phase = SteppedConstant(0.5)
        val bank = Ignitors.sinePartials(harmonics = count, phase = phase)
        val plainCount = SteppedConstant(1.0)
        val plain = Ignitors.sinePartials(harmonics = plainCount)
        val c1 = ctx()
        val c2 = ctx()
        val a = AudioBuffer(blockFrames)
        val b = AudioBuffer(blockFrames)
        var worst = 0.0

        for (block in 0 until 8) {
            if (block == 4) {
                count.value = 3.0
                plainCount.value = 3.0
            }

            c1.updateOffsetAndLength(offset = 0, length = blockFrames)
            c2.updateOffsetAndLength(offset = 0, length = blockFrames)
            bank.generate(a, 220.0, c1)
            plain.generate(b, 220.0, c2)

            for (i in 0 until blockFrames) {
                worst = maxOf(worst, abs(a[i] + b[i]))
            }
        }

        worst shouldBeLessThan 1e-12
    }

    // ── THE IMPULSE ─────────────────────────────────────────────────────────────────────────────

    "the impulse spikes where the shifted phase passes 0 going forward" {
        fun spikes(out: DoubleArray): List<Int> = out.indices.filter { out[it] == 1.0 }

        // The impulse counts its phase in radians, which the exact clock does not reach, so every phase below keeps the
        // crossings off a sample boundary (0.26, not 0.25).

        // a constant start phase: the first spike after the rest of the cycle, then one per cycle; no spike at 0
        spikes(render(Ignitors.impulse(phase = ConstantIgnitor(0.26)), exactHz, full(4))) shouldBe
            listOf(48, 112, 176, 240, 304, 368, 432, 496)

        // block-constant steps. Windows of 96, 128, 96 frames (1.5, 2 and 1.5 cycles):
        //  - block 0, offset 0.26: the spike at 48 (0.26 + 47.36/64 = 1);
        //  - block 1 starts at 96 with the accumulator at 0.76, the offset steps BACK to 0.2 (0.70): no spike at 96,
        //    the next at 96 + 20 = 116, then 180;
        //  - block 2 starts at 224 with the accumulator at 0.70, the offset steps forward to 0.6 (1.10): the step
        //    carries the phase past the wrap, a spike on its first sample, 224, then 224 + 58 = 282.
        val stepped = SteppedConstant(0.26)
        val impulse = Ignitors.impulse(phase = stepped)
        val c = ctx()
        val buffer = AudioBuffer(blockFrames)
        val out = ArrayList<Int>()
        var at = 0

        for ((block, length) in listOf(96, 128, 96).withIndex()) {
            stepped.value = listOf(0.26, 0.2, 0.6)[block]
            c.updateOffsetAndLength(offset = 0, length = length)
            impulse.generate(buffer, exactHz, c)

            for (i in 0 until length) {
                if (buffer[i] == 1.0) {
                    out.add(at + i)
                }
            }

            at += length
        }

        out shouldBe listOf(48, 116, 180, 224, 282)

        fun steppedSpikes(values: List<Double>, lengths: List<Int>): List<Int> {
            val offset = SteppedConstant(values[0])
            val imp = Ignitors.impulse(phase = offset)
            val cc = ctx()
            val spikes = ArrayList<Int>()
            var pos = 0

            for ((block, length) in lengths.withIndex()) {
                offset.value = values[block]
                cc.updateOffsetAndLength(offset = 0, length = length)
                imp.generate(buffer, exactHz, cc)

                for (i in 0 until length) {
                    if (buffer[i] == 1.0) {
                        spikes.add(pos + i)
                    }
                }

                pos += length
            }

            return spikes
        }

        // a small step back ACROSS the offset's wrap (0.1 to -0.1, the wrapped values 0.1 and 0.9) is a step back of
        // 0.2, not a step forward of 0.8: block 1 starts with the accumulator at 0.6, which goes to 0.4, no spike at
        // 96; the next spike 39 samples on (0.4 + 38.4/64 = 1), at 135
        steppedSpikes(listOf(0.1, -0.1), listOf(96, 96)) shouldBe listOf(58, 135)

        // a natural wrap on a block's first sample, cancelled by a step back of more than it: the offset 0.26 brings
        // the accumulator to 1.01 (a wrap) at sample 48, the step back of 0.06 to 0.95, so the spike comes at 52
        steppedSpikes(listOf(0.26, 0.2), listOf(48, 64)) shouldBe listOf(52)

        // a step forward of 0.49 within one increment of half a cycle: the block path judges the block's first sample
        // by the phased loop's rule (a drop of more than half a cycle), so it agrees with the same offsets as a signal.
        // At 32 the accumulator is 0.76 and moves to 0.25, a drop of 0.494 cycles, no spike; the next at 81.
        val forward = steppedSpikes(listOf(0.26, 0.75), listOf(32, 96))
        val signalForward = spikes(
            render(Ignitors.impulse(phase = ArrayIgnitor(DoubleArray(128) { if (it < 32) 0.26 else 0.75 })), exactHz, listOf(0 to 32, 0 to 96)),
        )

        forward shouldBe signalForward
        forward shouldBe listOf(81)

        // a moving phase drifting back slower than the oscillator advances is a lower frequency: one spike per forward
        // crossing (15/1024 of a cycle per sample: 59 in 4096 samples), plus the one at the start
        val n = 32 * blockFrames
        val slower = spikes(render(Ignitors.impulse(phase = ArrayIgnitor(DoubleArray(n) { -it / 1024.0 })), exactHz, full(32)))

        slower.size shouldBe 60
        slower.first() shouldBe 0

        // a moving phase running back faster than the oscillator advances: every step is a small step back, and none
        // of them spikes (only the start does)
        spikes(render(Ignitors.impulse(phase = ArrayIgnitor(DoubleArray(n) { -it / 32.0 })), exactHz, full(32))) shouldBe listOf(0)
    }
})
