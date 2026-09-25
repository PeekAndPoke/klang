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
import io.peekandpoke.klang.audio_be.Oversampler
import io.peekandpoke.klang.audio_bridge.DistortionShapes
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.LfoShapes
import io.peekandpoke.klang.audio_bridge.lowpass
import io.peekandpoke.klang.audio_bridge.tremolo
import kotlin.random.Random

/**
 * The knobs phase 3 step 3b (2026-09-25) put on the waveshaper and the tremolo, and how the runtime
 * reads them. The tremolo's LAW is `TremoloNodeStripParitySpec`'s; the catalogues are
 * `ShapeCatalogueSpec`'s. This file is the plumbing between a knob on a node and the stage it builds:
 *
 *  - the `Shape` and `Distort` nodes' `shape` INDEX and `oversample` FACTOR, read once at voice build
 *    (decision D7 for the factor), against the hand-authoring API that takes a name and a stage count;
 *  - the bad values: a non-finite, negative, past-the-end or fractional index, a non-finite or
 *    fractional factor, and a knob that is not a leaf;
 *  - the RNG STREAM: a build-time knob that is not a leaf is NOT BUILT, so a drawing node there moves
 *    no draw (the `filterEnvKnob` rule);
 *  - the tremolo's skew, read per BLOCK, which is why an expression works there and not in the two
 *    build-time knobs;
 *  - `BuiltIgnitor.gatesOutput`, the build's report that the tree carries a tremolo.
 */
class WaveshaperKnobsSpec : StringSpec({

    val blockFrames = 128
    val blocks = 6
    val freqHz = 220.0
    val sampleRate = 48000

    fun seed() = Random(11)

    // A loud saw, so every shaper is well into its curve and two shapes cannot look alike.
    val saw: IgnitorDsl = IgnitorDsl.Times(IgnitorDsl.Sawtooth(freq = IgnitorDsl.Freq), IgnitorDsl.Constant(3.0))

    fun ctx(rng: Random): IgniteContext = IgniteContext(
        sampleRate = sampleRate,
        voiceDurationFrames = blockFrames * blocks,
        gateEndFrame = blockFrames * blocks,
        releaseFrames = 0,
        scratchBuffers = ScratchBuffers(blockFrames),
        random = rng,
    ).apply {
        updateOffsetAndLength(0, blockFrames)
        voiceElapsedFrames = 0
    }

    /** [blocks] blocks of an already-built [ignitor]; [rng] must be the one the build drew from. */
    fun renderBuilt(ignitor: Ignitor, rng: Random): DoubleArray {
        val out = DoubleArray(blockFrames * blocks)
        val buffer = AudioBuffer(blockFrames)
        val context = ctx(rng)

        for (b in 0 until blocks) {
            ignitor.generate(buffer, freqHz, context)

            for (i in 0 until blockFrames) {
                out[b * blockFrames + i] = buffer[i]
            }

            context.voiceElapsedFrames += blockFrames
        }

        return out
    }

    /** [dsl] through the DSL runtime, ONE `Random` for the build and the render, as a voice has. */
    fun render(dsl: IgnitorDsl, params: Map<String, Double>? = null): DoubleArray {
        val rng = seed()
        val ignitor = dsl.buildExciter(oscParams = params, random = rng, freqHz = freqHz, sampleRate = sampleRate).ignitor

        return renderBuilt(ignitor, rng)
    }

    /** The hand-authoring API over the same source: a shape NAME and a STAGE count, no knob in sight. */
    fun renderHand(shapeName: String, stages: Int, drive: Double? = null): DoubleArray {
        val rng = seed()
        val source = saw.buildExciter(random = rng, freqHz = freqHz, sampleRate = sampleRate).ignitor
        val driven = if (drive != null) source.drive(drive) else source

        return renderBuilt(driven.shape(shapeName, stages), rng)
    }

    fun DoubleArray.bits(): List<Long> = map { it.toRawBits() }

    fun idx(name: String) = IgnitorDsl.Constant(DistortionShapes.indexOf(name))

    // ── The shape index ──────────────────────────────────────────────────────────────────────────

    "the Shape node's index selects the shaper its name selects, for every shape" {
        for (name in DistortionShapes.names) {
            withClue(name) {
                render(IgnitorDsl.Shape(inner = saw, shape = idx(name))).bits() shouldBe renderHand(name, 0).bits()
            }
        }

        // Not vacuous: two shapes are two sounds.
        renderHand("hard", 0).bits() shouldNotBe renderHand("soft", 0).bits()
    }

    "a shape index arriving through the bag is read at build, and the default is soft" {
        val slotted = IgnitorDsl.Shape(inner = saw, shape = IgnitorDsl.Param("drive.shape", DistortionShapes.indexOf("tube")))

        render(slotted, mapOf("drive.shape" to DistortionShapes.indexOf("fold"))).bits() shouldBe renderHand("fold", 0).bits()
        render(slotted).bits() shouldBe renderHand("tube", 0).bits()
        render(IgnitorDsl.Shape(inner = saw)).bits() shouldBe renderHand("soft", 0).bits()
    }

    "a bad index is a defined shaper: non-finite, negative or past the end is soft, a fraction rounds" {
        val soft = renderHand("soft", 0).bits()

        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -3.0, 16.0, 1e9)) {
            withClue(bad) { render(IgnitorDsl.Shape(inner = saw, shape = IgnitorDsl.Constant(bad))).bits() shouldBe soft }
        }

        // 1.6 is position 2, gentle; on a node, not only in the catalogue function.
        render(IgnitorDsl.Shape(inner = saw, shape = IgnitorDsl.Constant(1.6))).bits() shouldBe renderHand("gentle", 0).bits()
    }

    "a shape knob that is not a leaf is soft, even when it would evaluate to another index" {
        // `2 + 8` = 10, tube, if anything evaluated it; the build-time read is leaf-only.
        val expression = IgnitorDsl.Plus(IgnitorDsl.Constant(2.0), IgnitorDsl.Constant(8.0))

        render(IgnitorDsl.Shape(inner = saw, shape = expression)).bits() shouldBe renderHand("soft", 0).bits()
    }

    // ── The oversample factor (D7) ───────────────────────────────────────────────────────────────

    "the oversample knob is the factor the hand API's stage count means, read at build" {
        for ((factor, stages) in listOf(0.0 to 0, 1.0 to 0, 2.0 to 1, 3.0 to 1, 4.0 to 2, 8.0 to 3)) {
            withClue(factor) {
                render(IgnitorDsl.Shape(inner = saw, shape = idx("hard"), oversample = IgnitorDsl.Constant(factor))).bits() shouldBe
                        renderHand("hard", stages).bits()
            }
        }

        // Not vacuous: the oversampler changes the sound.
        renderHand("hard", 2).bits() shouldNotBe renderHand("hard", 0).bits()
    }

    "a factor from the bag is read at build; a fraction truncates, as the pattern door's asIntOrNull does" {
        val slotted = IgnitorDsl.Shape(inner = saw, shape = idx("hard"), oversample = IgnitorDsl.Param("distort.oversample", 0.0))

        render(slotted, mapOf("distort.oversample" to 4.0)).bits() shouldBe renderHand("hard", 2).bits()
        render(slotted).bits() shouldBe renderHand("hard", 0).bits()

        // 3.6 truncates to 3 (one stage, 2x); rounding would give 4 (two stages).
        render(slotted, mapOf("distort.oversample" to 3.6)).bits() shouldBe renderHand("hard", 1).bits()
    }

    "a non-finite, negative or non-leaf factor is no oversampler at all" {
        val plain = renderHand("hard", 0).bits()

        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -4.0)) {
            withClue(bad) {
                render(IgnitorDsl.Shape(inner = saw, shape = idx("hard"), oversample = IgnitorDsl.Constant(bad))).bits() shouldBe plain
            }
        }

        val expression = IgnitorDsl.Plus(IgnitorDsl.Constant(2.0), IgnitorDsl.Constant(2.0))

        render(IgnitorDsl.Shape(inner = saw, shape = idx("hard"), oversample = expression)).bits() shouldBe plain
    }

    "factorOf: the one conversion, non-finite is off, a fraction truncates, huge saturates unclamped" {
        Oversampler.factorOf(Double.NaN) shouldBe 0
        Oversampler.factorOf(Double.POSITIVE_INFINITY) shouldBe 0
        Oversampler.factorOf(Double.NEGATIVE_INFINITY) shouldBe 0
        Oversampler.factorOf(2.9) shouldBe 2
        Oversampler.factorOf(-2.9) shouldBe -2
        Oversampler.factorOf(8.0) shouldBe 8
        Oversampler.factorOf(1e12) shouldBe Int.MAX_VALUE
    }

    // ── The legacy Distort node carries the same knobs ───────────────────────────────────────────

    "the Distort node reads its shape and factor as the Shape node does, and is drive into shape" {
        val distort = IgnitorDsl.Distort(
            inner = saw,
            amount = IgnitorDsl.Constant(0.4),
            shape = IgnitorDsl.Param("d.shape", 0.0),
            oversample = IgnitorDsl.Param("d.os", 0.0),
        )
        val bag = mapOf("d.shape" to DistortionShapes.indexOf("asym"), "d.os" to 4.0)

        render(distort, bag).bits() shouldBe renderHand("asym", 2, drive = 0.4).bits()
        render(distort).bits() shouldBe renderHand("soft", 0, drive = 0.4).bits()
        render(distort, mapOf("d.shape" to Double.NaN, "d.os" to Double.NaN)).bits() shouldBe renderHand("soft", 0, drive = 0.4).bits()
    }

    // ── The rng stream ───────────────────────────────────────────────────────────────────────────

    "a build-time knob that is not a leaf is not built, so it moves no draw" {
        // `perlin` builds a permutation table and a start position AT CONSTRUCTION, off the voice's
        // stream; the white noise draws AT GENERATE, off the same stream. A perlin that got built
        // would shift every white-noise sample after it.
        val noise = IgnitorDsl.WhiteNoise()
        val reference = render(IgnitorDsl.Shape(inner = noise)).bits()

        render(IgnitorDsl.Shape(inner = noise, shape = IgnitorDsl.PerlinNoise())).bits() shouldBe reference
        render(IgnitorDsl.Shape(inner = noise, oversample = IgnitorDsl.PerlinNoise())).bits() shouldBe reference

        val tremolo = IgnitorDsl.Tremolo(inner = noise, rate = IgnitorDsl.Constant(37.3), depth = IgnitorDsl.Constant(0.8))

        render(tremolo.copy(shape = IgnitorDsl.PerlinNoise(), phase = IgnitorDsl.PerlinNoise())).bits() shouldBe render(tremolo).bits()

        // Engagement: the same perlin in a knob the runtime DOES build (the skew, read per block)
        // changes the render, so the rows above are not passing because the knob position is inert.
        render(tremolo.copy(skew = IgnitorDsl.PerlinNoise())).bits() shouldNotBe render(tremolo).bits()
    }

    // ── The tremolo's skew is read per block ─────────────────────────────────────────────────────

    "the tremolo's skew is read per block, so an expression reaches it (the two build-time knobs ignore one)" {
        val source = IgnitorDsl.Sawtooth(freq = IgnitorDsl.Freq)
        val base = source.tremolo(37.3, 1.0, shape = "square") as IgnitorDsl.Tremolo
        val sum = IgnitorDsl.Plus(IgnitorDsl.Constant(0.25), IgnitorDsl.Constant(0.25))

        render(base.copy(skew = sum)).bits() shouldBe render(base.copy(skew = IgnitorDsl.Constant(0.5))).bits()
        render(base.copy(skew = sum)).bits() shouldNotBe render(base).bits()
    }

    "the tremolo's shape and phase from the bag reach the stage, and their defaults are the sine at 0" {
        val source = IgnitorDsl.Sawtooth(freq = IgnitorDsl.Freq)
        val slotted = IgnitorDsl.Tremolo(
            inner = source,
            rate = IgnitorDsl.Constant(37.3),
            depth = IgnitorDsl.Constant(1.0),
            shape = IgnitorDsl.Param("t.shape", LfoShapes.SINE_INDEX.toDouble()),
            phase = IgnitorDsl.Param("t.phase", 0.0),
        )

        render(slotted).bits() shouldBe render(source.tremolo(37.3, 1.0)).bits()
        render(slotted, mapOf("t.shape" to LfoShapes.indexOf("square"))).bits() shouldBe
                render(source.tremolo(37.3, 1.0, shape = "square")).bits()
        render(slotted, mapOf("t.phase" to 0.25)).bits() shouldBe render(source.tremolo(37.3, 1.0, phase = 0.25)).bits()
        render(slotted, mapOf("t.phase" to 0.25)).bits() shouldNotBe render(slotted).bits()
    }

    // ── gatesOutput ──────────────────────────────────────────────────────────────────────────────

    "gatesOutput: a built tremolo on the spine reports it; a gated one, a parameter-position one and none do not" {
        fun IgnitorDsl.gates(params: Map<String, Double>? = null): Boolean =
            buildExciter(oscParams = params, random = seed(), freqHz = freqHz, sampleRate = sampleRate).gatesOutput

        val source = IgnitorDsl.Sawtooth(freq = IgnitorDsl.Freq)

        withClue("a plain source") { source.gates() shouldBe false }
        withClue("a built tremolo") { source.tremolo(4.0, 1.0, shape = "square").gates() shouldBe true }
        withClue("a sine tremolo too: the strip's rule, depth above 0 whatever the shape") {
            source.tremolo(4.0, 0.2).gates() shouldBe true
        }
        withClue("under a filter: absorbed along the spine") {
            source.tremolo(4.0, 1.0).lowpass(2000.0).gates() shouldBe true
        }
        withClue("gated off at depth 0: no stage, nothing to report") { source.tremolo(4.0, 0.0).gates() shouldBe false }

        val slotted = IgnitorDsl.Tremolo(inner = source, depth = IgnitorDsl.Param("t.depth", 0.0))

        withClue("a depth slot, unwritten: gated") { slotted.gates() shouldBe false }
        withClue("a depth slot, written") { slotted.gates(mapOf("t.depth" to 0.5)) shouldBe true }

        withClue("a tremolo in a PARAMETER position silences nothing") {
            val lfo = IgnitorDsl.Sine(freq = IgnitorDsl.Constant(2.0)).tremolo(4.0, 1.0, shape = "square")

            IgnitorDsl.Lowpass(inner = source, freq = IgnitorDsl.Plus(lfo, IgnitorDsl.Constant(1000.0))).gates() shouldBe false
        }
    }
})
