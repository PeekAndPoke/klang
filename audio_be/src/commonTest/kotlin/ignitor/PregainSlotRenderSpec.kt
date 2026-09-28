/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.distort
import io.peekandpoke.klang.audio_bridge.lowpass
import io.peekandpoke.klang.audio_bridge.mul
import io.peekandpoke.klang.audio_bridge.pregain
import io.peekandpoke.klang.audio_bridge.tanh
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * What `pregain` does to SAMPLES, at the level where it is decided: the ignitor tree.
 *
 * Three claims, and each is checked against something the pregain code did not produce.
 *
 * **Inert.** An instrument that never places the slot renders bit for bit the same whatever
 * `pregain` says. That is the whole design of "an ordinary slot" (the signal-flow plan, section 6,
 * and the paragraph about what was deleted): no unconsumed rule, no level applied behind the
 * author's back. The engagement control is the SAME instrument with the slot placed, which must
 * change, so the row cannot pass because nothing renders.
 *
 * **Placed, linear tree.** The slot is then exactly a level, and the oracle says so without
 * consulting the engine's pregain path at all: the slotless render TIMES the number. 0.5 is a
 * power of two, so that scaling is exact in binary and the comparison can be by raw bits.
 *
 * **Placed, nonlinear tree.** The tone claim, and the only reason the word exists: with a
 * waveshaper after it, the slot changes the SHAPE and not just the scale, so `render(p)` differs
 * from `render(1) * p` by much more than rounding. The same multiply written AFTER the shaper is
 * a pure scale, bit for bit, which is what the channel's `gain` is positionally. Both rows on the
 * same tree, so the difference is the POSITION of the multiply and nothing else.
 *
 * The non-finite guard and the whole-voice path are `VoicePregainWireSpec`'s.
 */
class PregainSlotRenderSpec : StringSpec({

    val blockFrames = 128
    val blocks = 4
    val freqHz = 220.0

    /** The seed every build shares, so two trees draw the same numbers at construction. */
    fun seed() = Random(7)

    fun ctx(): IgniteContext = IgniteContext(
        sampleRate = 44100,
        voiceDurationFrames = blockFrames * blocks,
        gateEndFrame = blockFrames * blocks,
        releaseFrames = 0,
        scratchBuffers = ScratchBuffers(blockFrames),
    ).apply {
        updateOffsetAndLength(0, blockFrames)
        voiceElapsedFrames = 0
    }

    /** [blocks] blocks of [dsl], built with [params] and rendered end to end. */
    fun render(dsl: IgnitorDsl, params: Map<String, Double>? = null): DoubleArray {
        val ignitor = dsl.buildExciter(oscParams = params, random = seed()).ignitor
        val out = DoubleArray(blockFrames * blocks)
        val buffer = AudioBuffer(blockFrames)
        val context = ctx()

        for (b in 0 until blocks) {
            ignitor.generate(buffer, freqHz, context)

            for (i in 0 until blockFrames) {
                out[b * blockFrames + i] = buffer[i]
            }

            context.voiceElapsedFrames += blockFrames
        }

        return out
    }

    fun DoubleArray.bits(): List<Long> = map { it.toRawBits() }

    fun DoubleArray.peak(): Double = maxOf { abs(it) }

    // The two instruments: one that never places the slot, one that does, otherwise identical.
    fun plain(): IgnitorDsl = IgnitorDsl.Sine()

    fun driven(): IgnitorDsl = IgnitorDsl.Sine().pregain()

    "an instrument that never places the slot is bit-identical whatever pregain says" {
        val unset = render(plain())
        val written = render(plain(), mapOf("pregain" to 0.3))

        withClue("not-silence floor: there is a signal to be changed") {
            unset.peak() shouldBeGreaterThan 0.5
        }

        written.bits() shouldBe unset.bits()

        withClue("engagement: the same slot on an instrument that PLACES it does change") {
            render(driven(), mapOf("pregain" to 0.3)).bits() shouldNotBe render(driven()).bits()
        }
    }

    "an unwritten slot renders exactly what the instrument renders without it" {
        // The other half of "ordinary": placing the slot costs nothing until a pattern writes it,
        // because the default is unity. `x * 1.0` is exact, so this is a bit comparison.
        render(driven()).bits() shouldBe render(plain()).bits()
    }

    "on a linear tree the slot IS a level: the slotless render times the number, exactly" {
        // A SCALING LAW, not an identity: both sides are engine renders, so what this pins is
        // that the output is LINEAR in the knob, not that any particular sample is right. The
        // independent anchor for the samples themselves is the row above, which compares this instrument against a structurally
        // DIFFERENT one (the same tree with no slot placed).
        val reference = render(plain())
        val driven = render(driven(), mapOf("pregain" to 0.5))

        withClue("not-silence floor") { reference.peak() shouldBeGreaterThan 0.5 }

        // The oracle is the reference render scaled by hand. Nothing about the pregain path
        // produced it, and 0.5 is a power of two, so the scaling is exact.
        for (i in reference.indices) {
            withClue("sample $i") {
                driven[i].toRawBits() shouldBe (reference[i] * 0.5).toRawBits()
            }
        }
    }

    "with a waveshaper AFTER it the slot changes the shape, not only the scale" {
        // The tone claim. `tanh` is the plainest nonlinearity in the tree vocabulary: it
        // compresses peaks, so half the drive is more than half the output near the peaks.
        val shaped = IgnitorDsl.Sine().pregain().tanh()

        val atUnity = render(shaped)
        val atHalf = render(shaped, mapOf("pregain" to 0.5))

        withClue("not-silence floor") { atUnity.peak() shouldBeGreaterThan 0.5 }

        // The scale reading would be `atUnity * 0.5`. How far the real render is from it is the
        // measure of the tone change; rounding would be ~1e-16, so 0.05 is orders above noise.
        var maxDeviation = 0.0

        for (i in atUnity.indices) {
            maxDeviation = maxOf(maxDeviation, abs(atHalf[i] - atUnity[i] * 0.5))
        }

        withClue("a pure scale would be 0 here, and rounding would be ~1e-16") {
            maxDeviation shouldBeGreaterThan 0.05
        }
    }

    "the same multiply written AFTER the shaper is a pure scale, bit for bit" {
        // A SCALING LAW, not an identity: both sides are engine renders, so what this pins is
        // that the output is LINEAR in the knob, not that any particular sample is right. The
        // independent anchor for the samples themselves is the shape row above, whose claim is a DISTANCE (0.05 and more) and
        // which therefore cannot be satisfied by any scaling.
        // The control for the row above, and the positional statement about the channel fader:
        // a level after the nonlinearity is tone-neutral BY POSITION, not by being a different
        // kind of multiply. Same slot, same number, same tree except for where the multiply sits.
        val after = IgnitorDsl.Sine().tanh().pregain()
        val reference = render(IgnitorDsl.Sine().tanh())
        val scaled = render(after, mapOf("pregain" to 0.5))

        withClue("not-silence floor") { reference.peak() shouldBeGreaterThan 0.5 }

        for (i in reference.indices) {
            withClue("sample $i") {
                scaled[i].toRawBits() shouldBe (reference[i] * 0.5).toRawBits()
            }
        }
    }

    "the instrument the KDoc examples use really changes SHAPE, not just level, at pregain 0.4" {
        // The doc examples claim "play harder, get dirtier" on `Osc.saw().pregain().distort(0.5)
        // .lowpass(2500)`. An example that compiles and demonstrates nothing is the documented
        // failure of the accessor sweep, and this one had exactly that shape until 2026-09-19: at
        // `distort(2)` the drive is about 250x, so pregain 1.0 and 0.4 are both hard-saturated and
        // the normalised difference was 0.006, a rounding-scale no-op dressed as a tone example.
        // (The level did not move either, 0.999: a clipper holds both, which is why the door's
        // KDoc now tells an author to reach for `gain` on a heavily driven sound.)
        //
        // The oracle is level-blind on purpose: both renders are normalised to unit RMS first, so
        // a pure level change reads as ZERO and only a change of SHAPE survives. The drive amount
        // was picked by sweeping this number (0.1 -> 0.082, 0.3 -> 0.181, 0.5 -> 0.223,
        // 1.0 -> 0.117, 2.0 -> 0.006 through the same lowpass).
        val example = IgnitorDsl.Sawtooth().pregain().distort(0.5).lowpass(2500.0)

        val hard = render(example)
        val soft = render(example, mapOf("pregain" to 0.4))

        withClue("not-silence floor") { hard.peak() shouldBeGreaterThan 0.5 }

        fun rms(a: DoubleArray): Double = sqrt(a.sumOf { it * it } / a.size)

        val hardRms = rms(hard)
        val softRms = rms(soft)

        withClue("both renders carry signal to normalise") {
            hardRms shouldBeGreaterThan 0.1
            softRms shouldBeGreaterThan 0.05
        }

        var sum = 0.0

        for (i in hard.indices) {
            val d = hard[i] / hardRms - soft[i] / softRms

            sum += d * d
        }

        val shapeDistance = sqrt(sum / hard.size)

        withClue("a level-only change would be 0 here; the measured example is 0.223") {
            shapeDistance shouldBeGreaterThan 0.15
        }
    }

    "a WAVEFOLDER never saturates: at the drive where `soft` goes deaf, `fold` is wide awake" {
        // The paragraph the doc KDocs carry ("in hard saturation pregain does almost nothing")
        // generalised from ONE shape and was false for a whole family (round 3, 2026-09-19). A
        // clipper runs out of room; a wavefolder never does, because it keeps folding. Pinned as a
        // CONTRAST at the same drive, so the row is about the shape and not about the drive.
        //
        // Same level-blind measure as the doc-example row: both renders normalised to unit RMS, so
        // a pure level change reads zero.
        fun shapeDistance(shape: String, drive: Double): Double {
            val dsl = IgnitorDsl.Sawtooth().pregain().distort(drive, shape).lowpass(2500.0)
            val loud = render(dsl)
            val soft = render(dsl, mapOf("pregain" to 0.4))
            val loudRms = sqrt(loud.sumOf { it * it } / loud.size)
            val softRms = sqrt(soft.sumOf { it * it } / soft.size)

            withClue("not-silence floor, $shape at $drive") {
                loudRms shouldBeGreaterThan 0.01
                softRms shouldBeGreaterThan 0.01
            }

            var sum = 0.0

            for (i in loud.indices) {
                val d = loud[i] / loudRms - soft[i] / softRms

                sum += d * d
            }

            return sqrt(sum / loud.size)
        }

        // Measured 2026-09-19 at drive 2: soft 0.006, hard 0.004, cubic 0.005, diode 0.009,
        // tube 0.015, gentle 0.021, rectify 0.054 (between the families), against fold 1.358,
        // linearfold 1.675, sineshaper 1.757.
        withClue("a clipper at drive 2 is deaf to the slot") {
            shapeDistance("soft", 2.0) shouldBeLessThan 0.05
        }

        for (folder in listOf("fold", "linearfold", "sineshaper")) {
            withClue("$folder at the SAME drive is not: it is still folding") {
                shapeDistance(folder, 2.0) shouldBeGreaterThan 1.0
            }
        }
    }

    "the helper and the spelled-out mul render the same samples" {
        // `0.5` for the reason the doc-example row gives: nobody should copy a drive out of a
        // spec at which the knob under test is inaudible. This row is a tree comparison and
        // would pass at any drive; the choice is about what a reader takes away.
        val helper = render(IgnitorDsl.Sine().pregain().distort(0.5), mapOf("pregain" to 0.7))
        val spelled = render(
            IgnitorDsl.Sine().mul(IgnitorDsl.Slots.pregain).distort(0.5),
            mapOf("pregain" to 0.7),
        )

        helper.bits() shouldBe spelled.bits()
    }
})
