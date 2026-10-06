/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.LfoShapes
import io.peekandpoke.klang.audio_bridge.tremolo
import kotlin.math.abs
import kotlin.random.Random

/**
 * The tremolo as a COMPOSITION of the oscillators (decided 2026-09-29, `docs/tasks/tremolo-as-composition.md`): the
 * `Tremolo` node builds the oscillator of its shape at its rate (no analog drift; the square, sawtooth and ramp with
 * a 16 ms soft edge), maps it to `range(1 - depth, 1)` and multiplies it into the signal.
 *
 *  - THE LAW, as a relation: the stage's output is the inner signal times `1 - depth * (1 - (osc + 1) / 2)` of the
 *    same oscillator, built here independently, on the same clock (the same windows, a voice's: a mid-block start,
 *    full blocks, a short last one), at every shape, through both spellings of the knobs (constants and slots) and
 *    with a moving rate (read per block, as the oscillator reads its frequency).
 *  - THE EDGES: below about 31 Hz the largest sample-to-sample gain step of the square, sawtooth and ramp is the
 *    16 ms edge's, `depth / (0.016 * sampleRate)`. A step edge is a full-depth jump.
 *  - the block framing does not reach the LFO, and a non-finite rate does not poison the voice.
 *
 * The gate (a depth at or below 0, or unset, builds no stage) is `IgnitorGateSpec`'s tremolo row; the culler
 * (a built tremolo keeps its voice alive in the trough) is `VoiceSchedulerCullingSpec`'s.
 */
class TremoloCompositionSpec : StringSpec({

    val blockFrames = 128
    val freqHz = 220.0

    // The edge the maintainer chose by ear on 2026-09-29. A literal on purpose, not the engine's constant: these rows
    // guard that constant, and an expectation derived from it would follow any change to it.
    val edgeSeconds = 0.016

    /** (offset, length) per block: a mid-block start, full blocks, a short last one. About 5 000 frames. */
    val windows: List<Pair<Int, Int>> = buildList {
        add(37 to blockFrames - 37)
        repeat(38) { add(0 to blockFrames) }
        add(0 to 55)
    }

    fun igniteCtx(sampleRate: Int, frames: Int): IgniteContext = IgniteContext(
        sampleRate = sampleRate,
        voiceDurationFrames = frames,
        gateEndFrame = frames,
        releaseFrames = 0,
        scratchBuffers = ScratchBuffers(blockFrames),
        random = Random(7),
    )

    /** Every window of [ignitor], concatenated. */
    fun renderWindows(ignitor: Ignitor, sampleRate: Int, blocks: List<Pair<Int, Int>> = windows): DoubleArray {
        val ctx = igniteCtx(sampleRate, blocks.sumOf { it.second })
        val buffer = AudioBuffer(blockFrames)
        val out = ArrayList<Double>()

        for ((offset, length) in blocks) {
            ctx.updateOffsetAndLength(offset, length)
            ignitor.generate(buffer, freqHz, ctx)

            for (i in offset until offset + length) {
                out.add(buffer[i])
            }

            ctx.voiceElapsedFrames += length
        }

        return out.toDoubleArray()
    }

    fun build(dsl: IgnitorDsl, sampleRate: Int, params: Map<String, Double>? = null): Ignitor =
        dsl.buildExciter(ignitorParams = params, random = Random(7), freqHz = freqHz, sampleRate = sampleRate).ignitor

    fun renderNode(
        dsl: IgnitorDsl,
        sampleRate: Int,
        params: Map<String, Double>? = null,
        blocks: List<Pair<Int, Int>> = windows,
    ): DoubleArray = renderWindows(build(dsl, sampleRate, params), sampleRate, blocks)

    /**
     * The oscillator of [shape] at [rate], built here the way the listening pairs built the composed tremolo:
     * `Ignitor.<shape>(rate, o => ...edges... .analog(0))` (the shape `triangle` is `Ignitor.tri`). Not the engine's own
     * mapping: a shape that maps to the wrong oscillator there, or a new shape with no oscillator, shows here.
     */
    fun oscillator(shape: String, rate: Ignitor, sampleRate: Int): Ignitor {
        val analog = ConstantIgnitor(0.0)
        val edge = edgeSeconds * sampleRate

        return when (shape) {
            "sine" -> Ignitors.sine(rate, analog)
            "triangle" -> Ignitors.tri(rate, analog)
            "square" -> Ignitors.pulze(rate, ConstantIgnitor(0.5), analog, flankSamples = edge)
            "sawtooth" -> Ignitors.saw(rate, analog, resetSamples = edge)
            "ramp" -> Ignitors.ramp(rate, analog, resetSamples = edge)
            else -> error("a new LFO shape needs its oscillator in this spec: $shape")
        }
    }

    // The input: a saw at the voice's pitch, without drift, so the node and the dry render are the same samples.
    val inner: IgnitorDsl = IgnitorDsl.Saw(freq = IgnitorDsl.Freq, analog = IgnitorDsl.Constant(0.0))

    /** The largest absolute difference between [a] and [b], sample for sample. */
    fun maxDiff(a: DoubleArray, b: DoubleArray): Double {
        a.size shouldBe b.size

        var m = 0.0

        for (i in a.indices) {
            m = maxOf(m, abs(a[i] - b[i]))
        }

        return m
    }

    /** The law, computed here: the dry signal times `1 - depth * (1 - level)`, `level = (osc + 1) / 2`. */
    fun law(dry: DoubleArray, osc: DoubleArray, depth: Double): DoubleArray =
        DoubleArray(dry.size) { i -> dry[i] * (1.0 - depth * (1.0 - (osc[i] + 1.0) / 2.0)) }

    // Floating-point level: `range` computes `from + (x + 1) * halfSpan`, the law `1 - d * (1 - (x + 1) / 2)`.
    val tolerance = 1e-12

    // ── THE LAW, as a relation ───────────────────────────────────────────────────────────────────

    for (shape in LfoShapes.names) {
        "the $shape tremolo is the inner signal times the gain of the $shape oscillator, range(1 - depth, 1)" {
            for (sampleRate in listOf(48000, 44100)) {
                val dry = renderNode(inner, sampleRate)

                for (rate in listOf(4.0, 37.3)) {
                    for (depth in listOf(0.33, 1.0)) {
                        withClue("sr=$sampleRate rate=$rate depth=$depth") {
                            val node = renderNode(inner.tremolo(rate, depth, shape = shape), sampleRate)
                            val osc = renderWindows(oscillator(shape, ConstantIgnitor(rate), sampleRate), sampleRate)

                            (maxDiff(node, law(dry, osc, depth)) < tolerance) shouldBe true

                            // Not vacuous: the tremolo moved the signal by far more than the tolerance.
                            (maxDiff(node, dry) > 0.1) shouldBe true
                        }
                    }
                }
            }
        }
    }

    "the law through SLOTS: rate, depth and shape written through the bag, as classic() writes them" {
        val slotted = IgnitorDsl.Tremolo(
            inner = inner,
            rate = IgnitorDsl.Param("t.rate", 1.0),
            depth = IgnitorDsl.Param("t.depth", 0.0),
            shape = IgnitorDsl.Param("t.shape", LfoShapes.SINE_INDEX.toDouble()),
        )
        val sampleRate = 48000
        val dry = renderNode(inner, sampleRate)

        for (shape in LfoShapes.names) {
            withClue(shape) {
                val bag = mapOf("t.rate" to 6.1, "t.depth" to 0.8, "t.shape" to LfoShapes.indexOf(shape))
                val node = renderNode(slotted, sampleRate, bag)
                val osc = renderWindows(oscillator(shape, ConstantIgnitor(6.1), sampleRate), sampleRate)

                (maxDiff(node, law(dry, osc, 0.8)) < tolerance) shouldBe true
            }
        }
    }

    "the law with a MOVING rate: the oscillator reads it per block, as the tremolo always read its rate" {
        // A pattern-driven or modulated rate: 4 Hz plus a 0.7 Hz sine of 3 Hz. The reference oscillator takes the
        // same rate signal, built on its own, so both read it at each block's first sample.
        val rate = IgnitorDsl.Plus(
            IgnitorDsl.Constant(4.0),
            IgnitorDsl.Times(IgnitorDsl.Sine(freq = IgnitorDsl.Constant(0.7)), IgnitorDsl.Constant(3.0)),
        )
        val sampleRate = 48000
        val dry = renderNode(inner, sampleRate)

        for (shape in LfoShapes.names) {
            withClue(shape) {
                val node = renderNode(
                    IgnitorDsl.Tremolo(
                        inner = inner,
                        rate = rate,
                        depth = IgnitorDsl.Constant(0.9),
                        shape = IgnitorDsl.Constant(LfoShapes.indexOf(shape)),
                    ),
                    sampleRate,
                )
                val osc = renderWindows(oscillator(shape, build(rate, sampleRate), sampleRate), sampleRate)

                (maxDiff(node, law(dry, osc, 0.9)) < tolerance) shouldBe true

                // Not vacuous: the moving rate is not the steady 4 Hz.
                (maxDiff(node, renderNode(inner.tremolo(4.0, 0.9, shape = shape), sampleRate)) > 0.01) shouldBe true
            }
        }
    }

    // ── THE EDGES ────────────────────────────────────────────────────────────────────────────────

    for (shape in listOf("square", "sawtooth", "ramp")) {
        "the $shape tremolo's steepest gain step is its 16 ms edge, not a click" {
            // On a constant 1.0 the output IS the gain. Below about 31 Hz neither the duty nor `shapeMax` caps the
            // edge, so the edge crosses the full `depth` in `0.016 * sampleRate` samples, linearly.
            val steady = IgnitorDsl.Constant(1.0)
            val twoSeconds = List(760) { 0 to blockFrames }

            for (sampleRate in listOf(48000, 44100)) {
                for (rate in listOf(2.5, 4.0, 20.0)) {
                    for (depth in listOf(0.5, 1.0)) {
                        withClue("sr=$sampleRate rate=$rate depth=$depth") {
                            val gain = renderNode(steady.tremolo(rate, depth, shape = shape), sampleRate, blocks = twoSeconds)
                            var steepest = 0.0

                            for (i in 1 until gain.size) {
                                steepest = maxOf(steepest, abs(gain[i] - gain[i - 1]))
                            }

                            val edgeStep = depth / (edgeSeconds * sampleRate)

                            (steepest <= edgeStep * 1.001) shouldBe true
                            // Engagement: the edge is there, and it is the steepest part of the cycle.
                            (steepest >= edgeStep * 0.99) shouldBe true
                        }
                    }
                }
            }
        }
    }

    // ── The clock ────────────────────────────────────────────────────────────────────────────────

    "ragged block splits render the same samples as whole blocks, at every shape" {
        val whole = List(8) { 0 to blockFrames }
        val ragged = listOf(0 to 1, 0 to 127, 0 to 128, 5 to 51, 0 to 128, 0 to 128, 0 to 128, 0 to 128, 0 to 101, 0 to 104)
        val frames = whole.sumOf { it.second }

        ragged.sumOf { it.second } shouldBe frames

        for (shape in LfoShapes.names) {
            withClue(shape) {
                val dsl = inner.tremolo(23.0, 0.8, shape = shape)

                renderNode(dsl, 48000, blocks = ragged).map { it.toRawBits() } shouldBe
                        renderNode(dsl, 48000, blocks = whole).map { it.toRawBits() }
            }
        }
    }

    "a non-finite rate does not poison the gain: the LFO holds a finite value, the sine its phase-0 gain" {
        // The gain rendered on its own: through the multiply, a non-finite gain would be scrubbed to 0 and hide here.
        for (shape in LfoShapes.names) {
            for (rate in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
                withClue("$shape at $rate") {
                    val gain = renderWindows(
                        tremoloGain(ConstantIgnitor(rate), ConstantIgnitor(0.5), LfoShapes.indexOf(shape).toInt(), 48000),
                        48000,
                    )

                    gain.all { it.isFinite() } shouldBe true

                    if (shape == "sine") {
                        // The phase is scrubbed to 0 every sample: sin(0) = 0, level 0.5, gain 1 - 0.5 / 2.
                        gain.all { it == 0.75 } shouldBe true
                    }
                }
            }
        }
    }

    // ── A MOVING depth below 0 ───────────────────────────────────────────────────────────────────

    "a moving depth at or below 0 passes the signal unchanged, as a fixed depth at or below 0 does" {
        // A depth swinging from -0.4 to 0.6 at 3 Hz: per sample, the floor at 0 makes the gain exactly 1 wherever
        // the depth is at or below 0 (no boost), and the tremolo acts wherever it is above.
        val depth = IgnitorDsl.Plus(
            IgnitorDsl.Times(IgnitorDsl.Sine(freq = IgnitorDsl.Constant(3.0), analog = IgnitorDsl.Constant(0.0)), IgnitorDsl.Constant(0.5)),
            IgnitorDsl.Constant(0.1),
        )
        val sampleRate = 48000
        val twoSeconds = List(750) { 0 to blockFrames }
        val depths = renderNode(depth, sampleRate, blocks = twoSeconds)
        val dry = renderNode(inner, sampleRate, blocks = twoSeconds)

        for (shape in LfoShapes.names) {
            withClue(shape) {
                val node = renderNode(
                    IgnitorDsl.Tremolo(inner = inner, rate = IgnitorDsl.Constant(7.0), depth = depth, shape = IgnitorDsl.Constant(LfoShapes.indexOf(shape))),
                    sampleRate,
                    blocks = twoSeconds,
                )
                var off = 0
                var moved = 0

                for (i in node.indices) {
                    if (depths[i] <= 0.0) {
                        off++
                        withClue("sample $i, depth ${depths[i]}") { node[i].toRawBits() shouldBe dry[i].toRawBits() }
                    } else if (node[i] != dry[i]) {
                        moved++
                    }
                }

                // Not vacuous: a good share of the render sits below 0, and the rest is modulated.
                (off > sampleRate / 4) shouldBe true
                (moved > sampleRate / 4) shouldBe true
            }
        }
    }

    "a depth of block-constant ARITHMETIC below 0 passes the signal unchanged: only leaves are gated, so it is floored" {
        // `Ignitor.freq().div(-200).plus(0.8)` at the 220 Hz note is -0.3: block-constant but not a leaf, so the gate
        // cannot read it. Unfloored it would boost (gain 1 to 1.3). The same expression at `plus(1.5)` is 0.4 and acts.
        fun depthOf(offset: Double): IgnitorDsl =
            IgnitorDsl.Plus(IgnitorDsl.Div(IgnitorDsl.Freq, IgnitorDsl.Constant(-200.0)), IgnitorDsl.Constant(offset))

        val sampleRate = 48000
        val dry = renderNode(inner, sampleRate)

        for (shape in LfoShapes.names) {
            withClue(shape) {
                fun tremolo(depth: IgnitorDsl) = IgnitorDsl.Tremolo(
                    inner = inner, rate = IgnitorDsl.Constant(9.0), depth = depth, shape = IgnitorDsl.Constant(LfoShapes.indexOf(shape)),
                )

                renderNode(tremolo(depthOf(0.8)), sampleRate).map { it.toRawBits() } shouldBe dry.map { it.toRawBits() }

                // Engagement: the same expression above 0 modulates.
                (maxDiff(renderNode(tremolo(depthOf(1.5)), sampleRate), dry) > 0.1) shouldBe true
            }
        }
    }

    "a VARIANT or HINT depth below 0 passes the signal unchanged: the gate cannot read them, the floor catches them" {
        // Neither is a leaf for the gate, and both dissolve at build into a bare constant, so only the floor stands
        // between a negative value and a boost. The variant at sound index 1 (0.5) engages.
        val variants = IgnitorDsl.Variants(listOf(IgnitorDsl.Constant(-0.3), IgnitorDsl.Constant(0.5)))
        val hint = IgnitorDsl.OptimizerHint(IgnitorDsl.Constant(-0.3))
        val sampleRate = 48000

        fun render(depth: IgnitorDsl, shape: String, soundIndex: Int): DoubleArray {
            val dsl = IgnitorDsl.Tremolo(
                inner = inner, rate = IgnitorDsl.Constant(9.0), depth = depth, shape = IgnitorDsl.Constant(LfoShapes.indexOf(shape)),
            )
            val ignitor = dsl.buildExciter(soundIndex = soundIndex, random = Random(7), freqHz = freqHz, sampleRate = sampleRate).ignitor

            return renderWindows(ignitor, sampleRate)
        }

        val dry = renderNode(inner, sampleRate).map { it.toRawBits() }

        for (shape in LfoShapes.names) {
            withClue(shape) {
                withClue("variants, sound 0") { render(variants, shape, soundIndex = 0).map { it.toRawBits() } shouldBe dry }
                withClue("optimizer hint") { render(hint, shape, soundIndex = 0).map { it.toRawBits() } shouldBe dry }
                withClue("variants, sound 1 engages") {
                    render(variants, shape, soundIndex = 1).map { it.toRawBits() } shouldNotBe dry
                }
            }
        }
    }

    "the LFO is shielded from the voice's pitch modulation: a phaseMod ramp does not move the tremolo rate" {
        // A pitch envelope or accelerate on the voice reaches the sources as `ctx.phaseMod`. The old tremolo ignored it
        // by construction; the composed one wraps its LFO so it still does.
        val sampleRate = 48000
        val ramp = DoubleArray(blockFrames) { 1.0 + it * 0.02 }

        fun renderUnder(ignitor: Ignitor, phaseMod: DoubleArray?): DoubleArray {
            val ctx = igniteCtx(sampleRate, windows.sumOf { it.second })
            val buffer = AudioBuffer(blockFrames)
            val out = ArrayList<Double>()

            for ((offset, length) in windows) {
                ctx.updateOffsetAndLength(offset, length)
                ctx.phaseMod = phaseMod
                ignitor.generate(buffer, freqHz, ctx)

                for (i in offset until offset + length) {
                    out.add(buffer[i])
                }

                ctx.voiceElapsedFrames += length
            }

            return out.toDoubleArray()
        }

        for (shape in LfoShapes.names) {
            withClue(shape) {
                val index = LfoShapes.indexOf(shape).toInt()
                val plain = renderUnder(tremoloGain(ConstantIgnitor(6.0), ConstantIgnitor(0.8), index, sampleRate), null)
                val modulated = renderUnder(tremoloGain(ConstantIgnitor(6.0), ConstantIgnitor(0.8), index, sampleRate), ramp)

                modulated.map { it.toRawBits() } shouldBe plain.map { it.toRawBits() }

                // Engagement: the same oscillator unshielded does follow the ramp.
                renderUnder(oscillator(shape, ConstantIgnitor(6.0), sampleRate), ramp).toList() shouldNotBe
                        renderUnder(oscillator(shape, ConstantIgnitor(6.0), sampleRate), null).toList()
            }
        }
    }

    // ── THE RANGE: where the swing sits (decision 2, 2026-10-06) ─────────────────────────────────

    /** The ranged law, computed here: the dry signal times `1 + depth * (from + (osc + 1) / 2 * (to - from))`. */
    fun rangedLaw(dry: DoubleArray, osc: DoubleArray, depth: Double, from: DoubleArray, to: DoubleArray): DoubleArray =
        DoubleArray(dry.size) { i -> dry[i] * (1.0 + depth * (from[i] + (osc[i] + 1.0) / 2.0 * (to[i] - from[i]))) }

    fun rangedNode(shape: String, depth: Double, from: IgnitorDsl, to: IgnitorDsl): IgnitorDsl = IgnitorDsl.Tremolo(
        inner = inner,
        rate = IgnitorDsl.Constant(5.3),
        depth = IgnitorDsl.Constant(depth),
        shape = IgnitorDsl.Constant(LfoShapes.indexOf(shape)),
        rangeFrom = from,
        rangeTo = to,
    )

    "the default range (-1, 0) is the classic dip: the general law at (-1, 0), through slots, renders the classic bits" {
        // The slots force the general branch (`1 + depth * from`, `1 + depth * to`); at -1 and 0 it must reduce to the
        // classic `range(1 - depth, 1)` exactly, so a pattern that writes the defaults sounds as one that writes none.
        val sampleRate = 48000
        val slots = mapOf("r.from" to -1.0, "r.to" to 0.0)

        for (shape in LfoShapes.names) {
            for (depth in listOf(0.33, 1.0)) {
                withClue("$shape depth=$depth") {
                    val classic = renderNode(inner.tremolo(5.3, depth, shape = shape), sampleRate)
                    val general = renderNode(
                        rangedNode(shape, depth, IgnitorDsl.Param("r.from", 0.5), IgnitorDsl.Param("r.to", 0.5)),
                        sampleRate,
                        slots,
                    )

                    general.map { it.toRawBits() } shouldBe classic.map { it.toRawBits() }
                    // the default node itself carries (-1, 0)
                    (inner.tremolo(5.3, depth, shape = shape) as IgnitorDsl.Tremolo).rangeFrom shouldBe IgnitorDsl.Constant(-1.0)
                    (inner.tremolo(5.3, depth, shape = shape) as IgnitorDsl.Tremolo).rangeTo shouldBe IgnitorDsl.Constant(0.0)
                }
            }
        }
    }

    "the general law at (-1, 0) with a SIGNAL depth renders the classic bits too (the floor shared by both bounds)" {
        val sampleRate = 48000
        val depth = IgnitorDsl.Plus(
            IgnitorDsl.Times(IgnitorDsl.Sine(freq = IgnitorDsl.Constant(3.0), analog = IgnitorDsl.Constant(0.0)), IgnitorDsl.Constant(0.5)),
            IgnitorDsl.Constant(0.1),
        )
        val slots = mapOf("r.from" to -1.0, "r.to" to 0.0)

        for (shape in LfoShapes.names) {
            withClue(shape) {
                fun node(from: IgnitorDsl, to: IgnitorDsl) = IgnitorDsl.Tremolo(
                    inner = inner, rate = IgnitorDsl.Constant(5.3), depth = depth,
                    shape = IgnitorDsl.Constant(LfoShapes.indexOf(shape)), rangeFrom = from, rangeTo = to,
                )

                // two seconds: the depth swings below 0 (from about 0.13 s on), where only the floor keeps the gain at 1
                val twoSeconds = List(750) { 0 to blockFrames }
                val classic = renderNode(node(IgnitorDsl.Constant(-1.0), IgnitorDsl.Constant(0.0)), sampleRate, blocks = twoSeconds)
                val general = renderNode(
                    node(IgnitorDsl.Param("r.from", 0.5), IgnitorDsl.Param("r.to", 0.5)), sampleRate, slots, twoSeconds,
                )

                general.map { it.toRawBits() } shouldBe classic.map { it.toRawBits() }
            }
        }
    }

    "a range lays the LFO onto 1 + depth * (from..to): upward, both ways, twice the depth, upside down" {
        val sampleRate = 48000
        val dry = renderNode(inner, sampleRate)
        val n = dry.size

        for (shape in LfoShapes.names) {
            val osc = renderWindows(oscillator(shape, ConstantIgnitor(5.3), sampleRate), sampleRate)

            for ((from, to) in listOf(0.0 to 1.0, -1.0 to 1.0, 0.0 to 2.0, 0.5 to -0.5)) {
                withClue("$shape range($from, $to)") {
                    val node = renderNode(rangedNode(shape, 0.4, IgnitorDsl.Constant(from), IgnitorDsl.Constant(to)), sampleRate)
                    val expected = rangedLaw(dry, osc, 0.4, DoubleArray(n) { from }, DoubleArray(n) { to })

                    (maxDiff(node, expected) < tolerance) shouldBe true
                    // Not vacuous: off the classic dip.
                    (maxDiff(node, renderNode(inner.tremolo(5.3, 0.4, shape = shape), sampleRate)) > 0.05) shouldBe true
                }
            }
        }
    }

    "a range takes signals: a moving upper bound is read per sample" {
        val sampleRate = 48000
        val dry = renderNode(inner, sampleRate)
        val to = IgnitorDsl.Sine(freq = IgnitorDsl.Constant(0.9), analog = IgnitorDsl.Constant(0.0))
        val toValues = renderNode(to, sampleRate)

        for (shape in LfoShapes.names) {
            withClue(shape) {
                val osc = renderWindows(oscillator(shape, ConstantIgnitor(5.3), sampleRate), sampleRate)
                val node = renderNode(rangedNode(shape, 0.7, IgnitorDsl.Constant(0.0), to), sampleRate)

                (maxDiff(node, rangedLaw(dry, osc, 0.7, DoubleArray(dry.size) { 0.0 }, toValues)) < tolerance) shouldBe true
            }
        }
    }

    "the depth floor holds off the default range: a depth at or below 0 is no tremolo, whatever the range" {
        val sampleRate = 48000
        val dry = renderNode(inner, sampleRate).map { it.toRawBits() }
        // block-constant arithmetic the gate cannot read, at -0.3: only the floor stops a range(0, 2) from boosting
        val negative = IgnitorDsl.Plus(IgnitorDsl.Div(IgnitorDsl.Freq, IgnitorDsl.Constant(-200.0)), IgnitorDsl.Constant(0.8))

        for (shape in LfoShapes.names) {
            withClue(shape) {
                val node = IgnitorDsl.Tremolo(
                    inner = inner,
                    rate = IgnitorDsl.Constant(5.3),
                    depth = negative,
                    shape = IgnitorDsl.Constant(LfoShapes.indexOf(shape)),
                    rangeFrom = IgnitorDsl.Constant(0.0),
                    rangeTo = IgnitorDsl.Constant(2.0),
                )

                renderNode(node, sampleRate).map { it.toRawBits() } shouldBe dry
            }
        }
    }
})
