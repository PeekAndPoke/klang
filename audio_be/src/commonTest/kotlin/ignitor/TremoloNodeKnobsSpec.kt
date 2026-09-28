/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.TWO_PI
import io.peekandpoke.klang.audio_be.lfoDutyOf
import io.peekandpoke.klang.audio_be.lfoNorm
import io.peekandpoke.klang.audio_be.lfoScaleFirst
import io.peekandpoke.klang.audio_be.lfoScaleSecond
import io.peekandpoke.klang.audio_be.lfoShapeAt
import io.peekandpoke.klang.audio_be.parseLfoShape
import io.peekandpoke.klang.audio_be.wrapPhase
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.LfoShapes
import io.peekandpoke.klang.audio_bridge.tremolo
import kotlin.random.Random

/**
 * The Ignitor DSL's `Tremolo` node renders what the runtime tremolo renders at the same knobs, BIT FOR BIT, at
 * every shape, skew and start phase: the node's KNOB PLUMBING (the shape index read at build, the skew read per
 * block, the start phase read at build, the rate turned into an increment) and the name-to-index catalogue.
 * Until phase 3 step 9 the reference was the voice strip's `TremoloRenderer`, which proved the node the strip's
 * tremolo (phase 3 step 3b); the strip retired, and the LAW both rendered through (`TremoloCore`) is pinned by
 * `TremoloLawSpec` against values computed in the test. A mutation inside the core moves both sides here.
 *
 * The windows are a voice's: the first block starts mid-block (offset 37), the rest are full, and the
 * last is short. The input is a sawtooth, rendered once alone and fed to the reference, so both sides
 * multiply the same samples. Rates are fractional on purpose (the increment's rounding is part of the
 * law), and at 48 kHz over 5 000 frames they cover four to twenty-two LFO cycles, so every segment of
 * every shape, skewed or not, is crossed.
 */
class TremoloNodeKnobsSpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128
    val freqHz = 220.0

    /** (offset, length) per block: a mid-block start, full blocks, a short last one. */
    val windows: List<Pair<Int, Int>> = buildList {
        add(37 to blockFrames - 37)
        repeat(38) { add(0 to blockFrames) }
        add(0 to 55)
    }

    val source: IgnitorDsl = IgnitorDsl.Sawtooth(freq = IgnitorDsl.Freq)

    fun igniteCtx(): IgniteContext = IgniteContext(
        sampleRate = sampleRate,
        voiceDurationFrames = blockFrames * windows.size,
        gateEndFrame = blockFrames * windows.size,
        releaseFrames = 0,
        scratchBuffers = ScratchBuffers(blockFrames),
        random = Random(7),
    )

    /** Every window of [dsl], built with [params], concatenated. */
    fun renderNode(dsl: IgnitorDsl, params: Map<String, Double>? = null): DoubleArray {
        val ignitor = dsl.buildExciter(oscParams = params, random = Random(7), freqHz = freqHz, sampleRate = sampleRate).ignitor
        val ctx = igniteCtx()
        val buffer = AudioBuffer(blockFrames)
        val out = ArrayList<Double>()

        for ((offset, length) in windows) {
            ctx.updateOffsetAndLength(offset, length)
            ignitor.generate(buffer, freqHz, ctx)

            for (i in offset until offset + length) {
                out.add(buffer[i])
            }

            ctx.voiceElapsedFrames += length
        }

        return out.toDoubleArray()
    }

    /** The [source] alone over the windows: what both hosts multiply. */
    val dry: DoubleArray by lazy { renderNode(source) }

    /**
     * The runtime tremolo (`Ignitor.tremolo`, the host of `TremoloCore`) over the same windows, fed the [source]'s own
     * samples, its shape named through the catalogue (`parseLfoShape`).
     */
    fun renderRuntime(rate: Double, depth: Double, skew: Double, startPhase: Double, shape: String?): DoubleArray {
        val ignitor = ArrayIgnitor(dry).tremolo(
            rate = ConstantIgnitor(rate), depth = ConstantIgnitor(depth), skew = ConstantIgnitor(skew),
            shape = parseLfoShape(shape), startPhase = startPhase,
        )
        val ctx = igniteCtx()
        val buffer = AudioBuffer(blockFrames)
        val out = ArrayList<Double>()

        for ((offset, length) in windows) {
            ctx.updateOffsetAndLength(offset, length)
            ignitor.generate(buffer, freqHz, ctx)

            for (i in offset until offset + length) {
                out.add(buffer[i])
            }

            ctx.voiceElapsedFrames += length
        }

        return out.toDoubleArray()
    }

    /** The first sample whose raw bits differ, or -1. Raw bits: the claim is identity, not closeness. */
    fun firstMismatch(a: DoubleArray, b: DoubleArray): Int {
        if (a.size != b.size) {
            return 0
        }

        for (i in a.indices) {
            if (a[i].toRawBits() != b[i].toRawBits()) {
                return i
            }
        }

        return -1
    }

    val skews = listOf(-1.0, -0.6, 0.0, 0.3, 1.0, Double.NaN)
    val phases = listOf(0.0, 0.25, 0.7, 3.25, -0.4)
    val rates = listOf(37.3, 211.9)
    val depths = listOf(0.33, 1.0)

    fun checkGrid(shapeName: String) {
        var moved = 0

        for (rate in rates) {
            for (depth in depths) {
                for (skew in skews) {
                    for (phase in phases) {
                        val node = renderNode(source.tremolo(rate, depth, shape = shapeName, skew = skew, phase = phase))
                        val runtime = renderRuntime(rate, depth, skew, phase, shapeName)

                        withClue("shape=$shapeName rate=$rate depth=$depth skew=$skew phase=$phase") {
                            firstMismatch(node, runtime) shouldBe -1
                        }

                        if (firstMismatch(node, dry) != -1) {
                            moved++
                        }
                    }
                }
            }
        }

        // Not vacuous: every case of the grid really modulated the signal.
        moved shouldBe rates.size * depths.size * skews.size * phases.size
    }

    // One row per canonical shape, so a node-side mishandling of ONE shape names itself.
    for (name in LfoShapes.names) {
        "the node renders the runtime's $name tremolo bit for bit across the grid of skews, start phases, rates and depths" {
            checkGrid(name)
        }
    }

    "every alias, an unknown name and the default spell the same tremolo on the node and through the catalogue" {
        // The reference parses the name, the node carries the index the door converted: the catalogue
        // is shared, and these rows pin that the two spellings reach it the same way.
        val spellings = LfoShapes.aliases.keys + listOf("SQUARE", "Tri", "rampup", "")

        for (spelling in spellings) {
            val node = renderNode(source.tremolo(37.3, 0.8, shape = spelling, skew = 0.3, phase = 0.25))
            val runtime = renderRuntime(37.3, 0.8, 0.3, 0.25, spelling)

            withClue(spelling) { firstMismatch(node, runtime) shouldBe -1 }
        }

        // The Kotlin door's default and the catalogue's `null` shape are both the sine.
        firstMismatch(renderNode(source.tremolo(37.3, 0.8)), renderRuntime(37.3, 0.8, 0.0, 0.0, null)) shouldBe -1
    }

    "SLOTS: a shape, skew and phase written through the bag render what the runtime renders for those values" {
        // The path `classic()` will take: every knob a `Param` whose value arrives per note.
        val slotted = IgnitorDsl.Tremolo(
            inner = source,
            rate = IgnitorDsl.Param("tremolo.rate", 1.0),
            depth = IgnitorDsl.Param("tremolo.depth", 0.0),
            shape = IgnitorDsl.Param("tremolo.shape", 0.0),
            skew = IgnitorDsl.Param("tremolo.skew", 0.0),
            phase = IgnitorDsl.Param("tremolo.phase", 0.0),
        )
        val bag = mapOf(
            "tremolo.rate" to 37.3,
            "tremolo.depth" to 0.9,
            "tremolo.shape" to LfoShapes.indexOf("ramp"),
            "tremolo.skew" to -0.6,
            "tremolo.phase" to 0.7,
        )

        firstMismatch(renderNode(slotted, bag), renderRuntime(37.3, 0.9, -0.6, 0.7, "ramp")) shouldBe -1
    }

    "a MOVING skew is held per block: the node renders the law with each block's start value, per sample" {
        // The per-block contract of `Tremolo.skew`, pinned against a reference written from the
        // definition (the LfoShape primitives, not TremoloCore): the skew is the signal's value at the
        // block's first sample, held for the block, and the phase accumulator is never remapped (the
        // W2 beat lock), so the gain steps at each block edge. Node against definition.
        val skewSignal = IgnitorDsl.Times(IgnitorDsl.Sine(freq = IgnitorDsl.Constant(7.0)), IgnitorDsl.Constant(0.9))
        val rate = 37.3
        val depth = 0.8
        val node = renderNode(
            IgnitorDsl.Tremolo(
                inner = source,
                rate = IgnitorDsl.Constant(rate),
                depth = IgnitorDsl.Constant(depth),
                shape = IgnitorDsl.Constant(LfoShapes.indexOf("triangle")),
                skew = skewSignal,
            ),
        )

        val skews = renderNode(skewSignal)
        val shape = lfoShapeAt(LfoShapes.indexOf("triangle"))
        val increment = (rate * TWO_PI) / sampleRate
        val expected = DoubleArray(dry.size)
        var phase = 0.0
        var cursor = 0
        val heldSkews = mutableSetOf<Double>()

        for ((_, length) in windows) {
            val skew = skews[cursor]
            val duty = lfoDutyOf(skew, shape)
            val first = lfoScaleFirst(duty)
            val second = lfoScaleSecond(duty)
            heldSkews.add(skew)

            for (i in cursor until cursor + length) {
                phase = (phase + increment).wrapPhase(TWO_PI)
                val level = lfoNorm(shape, phase, duty, first, second)
                expected[i] = dry[i] * (1.0 - depth * (1.0 - level))
            }

            cursor += length
        }

        // Not vacuous: the skew really moved across the render.
        (heldSkews.size > windows.size / 2) shouldBe true
        firstMismatch(node, expected) shouldBe -1
    }
})
