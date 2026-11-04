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
import io.peekandpoke.klang.audio_be.parseDistortionShape
import io.peekandpoke.klang.audio_bridge.DistortionShapes
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.LfoShapes
import io.peekandpoke.klang.audio_bridge.ign
import io.peekandpoke.klang.audio_bridge.lowpass
import io.peekandpoke.klang.audio_bridge.tremolo
import io.peekandpoke.klang.common.infra.toKlangMap
import kotlin.random.Random

/**
 * The knobs phase 3 step 3b (2026-09-25) put on the waveshaper and the tremolo, and how the runtime
 * reads them. The tremolo's LAW is `TremoloCompositionSpec`'s; the catalogues are
 * `ShapeCatalogueSpec`'s. This file is the plumbing between a knob on a node and the stage it builds:
 *
 *  - the `Shape` and `Distort` nodes' `shape` INDEX, read once at voice build, against the hand-authoring API
 *    that takes a name (their own oversampling factor, decision D7's stopgap, retired with KLANG-00214 step 4);
 *  - the bad values: a non-finite, negative, past-the-end or fractional index, and a knob that is not a leaf;
 *  - the factor conversion a region's factor knob goes through ([Oversampler.factorOf]);
 *  - the RNG STREAM: a build-time knob that is not a leaf is NOT BUILT, so a drawing node there moves
 *    no draw (the `filterEnvKnob` rule);
 *  - the tremolo's rate, read per BLOCK, which is why an expression works there and not in the
 *    build-time shape knob;
 *  - `BuiltIgnitor.gatesOutput`, the build's report that the tree carries a tremolo.
 */
class WaveshaperKnobsSpec : StringSpec({

    val blockFrames = 128
    val blocks = 6
    val freqHz = 220.0
    val sampleRate = 48000

    fun seed() = Random(11)

    // A loud saw, so every shaper is well into its curve and two shapes cannot look alike.
    val saw: IgnitorDsl = IgnitorDsl.Times(left = IgnitorDsl.Saw(freq = IgnitorDsl.Freq), right = IgnitorDsl.Constant(3.0))

    fun ctx(rng: Random): IgniteContext = IgniteContext(
        sampleRate = sampleRate,
        voiceDurationFrames = blockFrames * blocks,
        gateEndFrame = blockFrames * blocks,
        scratchBuffers = ScratchBuffers(blockFrames),
        random = rng,
    ).apply {
        updateOffsetAndLength(offset = 0, length = blockFrames)
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
        val ignitor = dsl.buildExciter(ignitorParams = params?.toKlangMap(), random = rng, freqHz = freqHz, sampleRate = sampleRate).ignitor

        return renderBuilt(ignitor, rng)
    }

    /** The hand-authoring API over the same source: a shape NAME, no knob in sight. */
    fun renderHand(shapeName: String, drive: Double? = null): DoubleArray {
        val rng = seed()
        val source = saw.buildExciter(random = rng, freqHz = freqHz, sampleRate = sampleRate).ignitor
        val driven = if (drive != null) source.drive(drive) else source

        return renderBuilt(driven.shape(shapeName), rng)
    }

    /**
     * The same source through the runtime `fusedDistort` built by hand (the shape by name): the
     * law (`DistortionCore`) the fused `Distort` node renders since phase 3 step 4 (decision D2), the voice
     * strip's distort until the strip retired (step 9). The law's oracle is `StripLawCoresSpec`'s.
     */
    fun renderRuntime(shapeName: String, amount: Double): DoubleArray {
        val rng = seed()
        val source = saw.buildExciter(random = rng, freqHz = freqHz, sampleRate = sampleRate).ignitor

        return renderBuilt(source.fusedDistort(ConstantIgnitor(amount), parseDistortionShape(shapeName)), rng)
    }

    fun DoubleArray.bits(): List<Long> = map { it.toRawBits() }

    fun idx(name: String) = IgnitorDsl.Constant(DistortionShapes.indexOf(name))

    // ── The shape index ──────────────────────────────────────────────────────────────────────────

    "the Shape node's index selects the shaper its name selects, for every shape" {
        for (name in DistortionShapes.names) {
            withClue(name) {
                render(IgnitorDsl.Shape(inner = saw, shape = idx(name))).bits() shouldBe renderHand(name).bits()
            }
        }

        // Not vacuous: two shapes are two sounds.
        renderHand("hard").bits() shouldNotBe renderHand("soft").bits()
    }

    "a shape index arriving through the bag is read at build, and the default is soft" {
        val slotted = IgnitorDsl.Shape(inner = saw, shape = IgnitorDsl.Param("drive.shape", DistortionShapes.indexOf("tube")))

        render(slotted, mapOf("drive.shape" to DistortionShapes.indexOf("fold"))).bits() shouldBe renderHand("fold").bits()
        render(slotted).bits() shouldBe renderHand("tube").bits()
        render(IgnitorDsl.Shape(inner = saw)).bits() shouldBe renderHand("soft").bits()
    }

    "a bad index is a defined shaper: non-finite, negative or past the end is soft, a fraction rounds" {
        val soft = renderHand("soft").bits()

        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -3.0, 16.0, 1e9)) {
            withClue(bad) { render(IgnitorDsl.Shape(inner = saw, shape = IgnitorDsl.Constant(bad))).bits() shouldBe soft }
        }

        // 1.6 is position 2, gentle; on a node, not only in the catalogue function.
        render(IgnitorDsl.Shape(inner = saw, shape = IgnitorDsl.Constant(1.6))).bits() shouldBe renderHand("gentle").bits()
    }

    "a shape knob that is not a leaf is soft, even when it would evaluate to another index" {
        // `2 + 8` = 10, tube, if anything evaluated it; the build-time read is leaf-only.
        val expression = IgnitorDsl.Plus(left = IgnitorDsl.Constant(2.0), right = IgnitorDsl.Constant(8.0))

        render(IgnitorDsl.Shape(inner = saw, shape = expression)).bits() shouldBe renderHand("soft").bits()
    }

    // ── The factor conversion (a region's factor knob) ─────────────────────────────────────────────

    "factorOf: the one conversion, non-finite is off, a fraction truncates, huge saturates unclamped" {
        Oversampler.factorOf(Double.NaN) shouldBe 0
        Oversampler.factorOf(Double.POSITIVE_INFINITY) shouldBe 0
        Oversampler.factorOf(Double.NEGATIVE_INFINITY) shouldBe 0
        Oversampler.factorOf(2.9) shouldBe 2
        Oversampler.factorOf(-2.9) shouldBe -2
        Oversampler.factorOf(8.0) shouldBe 8
        Oversampler.factorOf(1e12) shouldBe Int.MAX_VALUE
    }

    // ── The fused Distort node (classic()'s distort stage, the strip's law since step 4) ─────────

    "the Distort node reads its shape as the Shape node does, and renders the strip's law" {
        // CHANGED in phase 3 step 4 (decision D2, option A): this row used to pin the node as
        // `drive(amount).shape(...)`, the doors' capped chain. The node now renders the voice strip's
        // loop (DistortionCore: no soft cap), so the reference is the runtime `fusedDistort` at the same
        // shape. The knob plumbing this row is about (the index read at build, NaN is soft) is unchanged.
        val distort = IgnitorDsl.Distort(
            inner = saw,
            amount = IgnitorDsl.Constant(0.4),
            shape = IgnitorDsl.Param("d.shape", 0.0),
        )
        val bag = mapOf("d.shape" to DistortionShapes.indexOf("asym"))

        render(distort, bag).bits() shouldBe renderRuntime("asym", amount = 0.4).bits()
        render(distort).bits() shouldBe renderRuntime("soft", amount = 0.4).bits()
        render(distort, mapOf("d.shape" to Double.NaN)).bits() shouldBe renderRuntime("soft", amount = 0.4).bits()

        // Not the doors' law any more: the loud saw drives the shaper past the doors' soft cap.
        render(distort).bits() shouldNotBe renderHand("soft", drive = 0.4).bits()
    }

    // ── The rng stream ───────────────────────────────────────────────────────────────────────────

    "a build-time knob that is not a leaf is not built, so it moves no draw" {
        // `perlin` builds a permutation table and a start position AT CONSTRUCTION, off the voice's
        // stream; the white noise draws AT GENERATE, off the same stream. A perlin that got built
        // would shift every white-noise sample after it.
        val noise = IgnitorDsl.WhiteNoise()
        val reference = render(IgnitorDsl.Shape(inner = noise)).bits()

        render(IgnitorDsl.Shape(inner = noise, shape = IgnitorDsl.PerlinNoise())).bits() shouldBe reference

        val tremolo = IgnitorDsl.Tremolo(inner = noise, rate = IgnitorDsl.Constant(37.3), depth = IgnitorDsl.Constant(0.8))

        render(tremolo.copy(shape = IgnitorDsl.PerlinNoise())).bits() shouldBe render(tremolo).bits()

        // Engagement: the same perlin in a knob the runtime DOES build (the rate, read per block)
        // changes the render, so the rows above are not passing because the knob position is inert.
        render(tremolo.copy(rate = IgnitorDsl.PerlinNoise())).bits() shouldNotBe render(tremolo).bits()
    }

    // ── The tremolo's rate is read per block ─────────────────────────────────────────────────────

    "the tremolo's rate is read per block, so an expression reaches it (the build-time shape knob ignores one)" {
        val source = IgnitorDsl.Saw(freq = IgnitorDsl.Freq)
        val base = source.tremolo(rate = 37.3.ign, depth = 1.0.ign) { it.shape("square") } as IgnitorDsl.Tremolo
        val sum = IgnitorDsl.Plus(left = IgnitorDsl.Constant(18.65), right = IgnitorDsl.Constant(18.65))

        render(base.copy(rate = sum)).bits() shouldBe render(base).bits()
        render(base.copy(rate = sum)).bits() shouldNotBe render(base.copy(rate = IgnitorDsl.Constant(18.65))).bits()
    }

    "the tremolo's shape from the bag reaches the stage, and its default is the sine" {
        val source = IgnitorDsl.Saw(freq = IgnitorDsl.Freq)
        val slotted = IgnitorDsl.Tremolo(
            inner = source,
            rate = IgnitorDsl.Constant(37.3),
            depth = IgnitorDsl.Constant(1.0),
            shape = IgnitorDsl.Param("t.shape", LfoShapes.SINE_INDEX.toDouble()),
        )

        render(slotted).bits() shouldBe render(source.tremolo(rate = 37.3.ign, depth = 1.0.ign)).bits()
        render(slotted, mapOf("t.shape" to LfoShapes.indexOf("square"))).bits() shouldBe
                render(source.tremolo(rate = 37.3.ign, depth = 1.0.ign) { it.shape("square") }).bits()
        render(slotted, mapOf("t.shape" to LfoShapes.indexOf("square"))).bits() shouldNotBe render(slotted).bits()
    }

    // ── gatesOutput ──────────────────────────────────────────────────────────────────────────────

    "gatesOutput: a built tremolo on the spine reports it; a gated one, a parameter-position one and none do not" {
        fun IgnitorDsl.gates(params: Map<String, Double>? = null): Boolean =
            buildExciter(ignitorParams = params?.toKlangMap(), random = seed(), freqHz = freqHz, sampleRate = sampleRate).gatesOutput

        val source = IgnitorDsl.Saw(freq = IgnitorDsl.Freq)

        withClue("a plain source") { source.gates() shouldBe false }
        withClue("a built tremolo") { source.tremolo(rate = 4.0.ign, depth = 1.0.ign) { it.shape("square") }.gates() shouldBe true }
        withClue("a sine tremolo too: the strip's rule, depth above 0 whatever the shape") {
            source.tremolo(rate = 4.0.ign, depth = 0.2.ign).gates() shouldBe true
        }
        withClue("under a filter: absorbed along the spine") {
            source.tremolo(rate = 4.0.ign, depth = 1.0.ign).lowpass(2000.0.ign).gates() shouldBe true
        }
        withClue("gated off at depth 0: no stage, nothing to report") { source.tremolo(rate = 4.0.ign, depth = 0.0.ign).gates() shouldBe false }

        val slotted = IgnitorDsl.Tremolo(inner = source, depth = IgnitorDsl.Param("t.depth", 0.0))

        withClue("a depth slot, unwritten: gated") { slotted.gates() shouldBe false }
        withClue("a depth slot, written") { slotted.gates(mapOf("t.depth" to 0.5)) shouldBe true }

        withClue("a tremolo in a PARAMETER position silences nothing") {
            val lfo = IgnitorDsl.Sine(freq = IgnitorDsl.Constant(2.0)).tremolo(rate = 4.0.ign, depth = 1.0.ign) { it.shape("square") }

            IgnitorDsl.Lowpass(inner = source, freq = IgnitorDsl.Plus(left = lfo, right = IgnitorDsl.Constant(1000.0))).gates() shouldBe false
        }
    }
})
