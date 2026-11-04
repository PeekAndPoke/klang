/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.DistortionCore
import io.peekandpoke.klang.audio_be.Oversampler
import io.peekandpoke.klang.audio_be.roundTrip
import io.peekandpoke.klang.audio_bridge.DistortionShapes
import io.peekandpoke.klang.audio_bridge.Ign
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.adsr
import io.peekandpoke.klang.audio_bridge.coarse
import io.peekandpoke.klang.audio_bridge.fm
import io.peekandpoke.klang.audio_bridge.ign
import io.peekandpoke.klang.audio_bridge.lowpass
import io.peekandpoke.klang.audio_bridge.mul
import io.peekandpoke.klang.audio_bridge.oversampleAtLeast
import io.peekandpoke.klang.audio_bridge.oversampleBy
import io.peekandpoke.klang.audio_bridge.parallel
import io.peekandpoke.klang.audio_bridge.pitchMod
import io.peekandpoke.klang.audio_bridge.plus
import io.peekandpoke.klang.audio_bridge.shimmer
import io.peekandpoke.klang.audio_bridge.vibrato
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The Ignitor's oversampling regions, `oversampleBy` and `oversampleAtLeast` (KLANG-00214 (oversampling regions)
 * step 1): the body at the region's rate, the input converted up once and the result down once, a factor read once and
 * rounded down to a power of two, at most 16x, nesting that steps on from the rate it is in, sources that stay at 1x,
 * and the stages inside that read the region's rate.
 *
 * **The oracles are bare [Oversampler] round trips** ([twin]) of the signal rendered without any region: an identity
 * body is exactly a round trip, and a source without draws renders the same samples in every build.
 */
class OversampleRegionSpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128
    val blocks = 12
    val noteHz = 220.0

    fun build(dsl: IgnitorDsl): BuiltIgnitor = dsl.buildExciter(random = Random(7), freqHz = noteHz, sampleRate = sampleRate)

    fun render(dsl: IgnitorDsl, gateEnd: Int = sampleRate, count: Int = blocks): DoubleArray {
        val ctx = IgniteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = gateEnd,
            gateEndFrame = gateEnd,
            scratchBuffers = ScratchBuffers(blockFrames),
            random = Random(7),
        )
        val ignitor = dsl.buildExciter(random = ctx.random, freqHz = noteHz, sampleRate = sampleRate).ignitor
        val buffer = AudioBuffer(blockFrames)
        val out = DoubleArray(count * blockFrames)

        for (b in 0 until count) {
            ctx.updateOffsetAndLength(offset = 0, length = blockFrames)
            ignitor.generate(buffer, noteHz, ctx)

            for (i in 0 until blockFrames) {
                out[b * blockFrames + i] = buffer[i]
            }

            ctx.voiceElapsedFrames += blockFrames
        }

        return out
    }

    /** As [render], every block in two windows, `[0, at)` and `[at, 128)`, as a voice renders around an event. */
    fun renderSplit(dsl: IgnitorDsl, at: Int): DoubleArray {
        val ctx = IgniteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = sampleRate,
            gateEndFrame = sampleRate,
            scratchBuffers = ScratchBuffers(blockFrames),
            random = Random(7),
        )
        val ignitor = dsl.buildExciter(random = ctx.random, freqHz = noteHz, sampleRate = sampleRate).ignitor
        val buffer = AudioBuffer(blockFrames)
        val out = DoubleArray(blocks * blockFrames)

        for (b in 0 until blocks) {
            ctx.updateOffsetAndLength(offset = 0, length = at)
            ignitor.generate(buffer, noteHz, ctx)
            ctx.voiceElapsedFrames += at
            ctx.updateOffsetAndLength(offset = at, length = blockFrames - at)
            ignitor.generate(buffer, noteHz, ctx)
            ctx.voiceElapsedFrames += blockFrames - at

            for (i in 0 until blockFrames) {
                out[b * blockFrames + i] = buffer[i]
            }
        }

        return out
    }

    /** [signal] through an unshaped round trip of a bare [Oversampler] of [stages] 2x stages, block by block. */
    fun twin(signal: DoubleArray, stages: Int): DoubleArray {
        val out = signal.copyOf()
        val scratch = ScratchBuffers(blockFrames)
        val os = Oversampler(stages)

        for (b in 0 until signal.size / blockFrames) {
            val block = AudioBuffer(blockFrames) { out[b * blockFrames + it] }

            os.roundTrip(buffer = block, offset = 0, length = blockFrames, scratch = scratch) { _, _ -> }
            block.copyInto(destination = out, destinationOffset = b * blockFrames)
        }

        return out
    }

    /** [actual] against [expected] sample by sample; [label] names the row in the failure, for the mutation checks. */
    fun shouldMatch(actual: DoubleArray, expected: DoubleArray, tolerance: Double = 0.0, label: String = "") {
        actual.size shouldBe expected.size

        for (k in actual.indices) {
            withClue("$label sample $k") {
                if (tolerance == 0.0) {
                    actual[k] shouldBe expected[k]
                } else {
                    actual[k] shouldBe (expected[k] plusOrMinus tolerance)
                }
            }
        }
    }

    fun rms(signal: DoubleArray, from: Int = 0): Double {
        var sum = 0.0

        for (k in from until signal.size) {
            sum += signal[k] * signal[k]
        }

        return sqrt(sum / (signal.size - from))
    }

    fun rmsOfDifference(a: DoubleArray, b: DoubleArray, from: Int): Double = rms(DoubleArray(a.size) { a[it] - b[it] }, from)

    val saw = IgnitorDsl.Saw()
    val tube = IgnitorDsl.Constant(DistortionShapes.indexOf("tube").toDouble())

    fun shaped(inner: IgnitorDsl) = IgnitorDsl.Shape(inner = inner, shape = tube)

    // ── No step to take: no region ──────────────────────────────────────────────────────────────

    "a factor of 1 or less, or one that is not a number, is no region: the body on its input, bit for bit" {
        val plain = render(saw.lowpass(800.ign))

        for (factor in listOf(1.0, 0.0, -4.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            withClue("factor $factor") {
                shouldMatch(render(saw.oversampleBy(factor.ign) { it.lowpass(800.ign) }), plain, label = "no region")
                shouldMatch(render(saw.oversampleAtLeast(factor.ign) { it.lowpass(800.ign) }), plain, label = "no region")
            }
        }
    }

    "a factor that moves (a signal, not a leaf) is no region, read once at the build" {
        shouldMatch(render(saw.oversampleBy(Ign.sine(2.ign)) { it.lowpass(800.ign) }), render(saw.lowpass(800.ign)))
    }

    "oversampleAtLeast at or below the rate it is in converts nothing: the same bits as its body written there" {
        val direct = render(saw.oversampleBy(4.ign) { shaped(it) })

        shouldMatch(render(saw.oversampleBy(4.ign) { y -> y.oversampleAtLeast(4.ign) { shaped(it) } }), direct, label = "floor reached")
        shouldMatch(render(saw.oversampleBy(4.ign) { y -> y.oversampleAtLeast(2.ign) { shaped(it) } }), direct, label = "floor reached")
    }

    // ── The trip up and down ────────────────────────────────────────────────────────────────────

    "an identity body is one round trip of the oversampler at the factor, rounded down to a power of two" {
        val dry = render(saw)

        for ((factor, stages) in listOf(2 to 1, 3 to 1, 4 to 2, 5 to 2, 7 to 2, 8 to 3, 16 to 4)) {
            withClue("factor $factor") {
                shouldMatch(render(saw.oversampleBy(factor.ign) { it }), twin(dry, stages), tolerance = 1e-12, label = "one trip")
                shouldMatch(render(saw.oversampleAtLeast(factor.ign) { it }), twin(dry, stages), tolerance = 1e-12, label = "one trip")
            }
        }
    }

    "the rate stops at 16x: a larger factor, and a nested one past it, are 16x, without an error" {
        val sixteen = twin(render(saw), 4)

        shouldMatch(render(saw.oversampleBy(64.ign) { it }), sixteen, tolerance = 1e-12, label = "16x cap")
        shouldMatch(render(saw.oversampleBy(8.ign) { y -> y.oversampleBy(8.ign) { it } }), sixteen, tolerance = 1e-12, label = "16x cap")
    }

    "nesting steps on from the rate it is in: oversampleBy(2) inside oversampleBy(2) is 4x, the same bits as one 4x region" {
        val four = render(saw.oversampleBy(4.ign) { shaped(it) })

        shouldMatch(render(saw.oversampleBy(2.ign) { y -> y.oversampleBy(2.ign) { shaped(it) } }), four, tolerance = 1e-12, label = "nested")
        shouldMatch(render(saw.oversampleBy(2.ign) { y -> y.oversampleAtLeast(4.ign) { shaped(it) } }), four, tolerance = 1e-12, label = "nested")
    }

    "a region renders the same with its blocks split, as a voice renders around an event" {
        val dsl = saw.oversampleBy(4.ign) { shaped(it) }

        shouldMatch(renderSplit(dsl, at = 37), render(dsl), tolerance = 1e-12, label = "split")
    }

    "the region reports its round trip's delay, and a nested one its own in the outer region's samples" {
        // the literals are the centroids of the round trips' impulse responses at 4x and 8x (stages 0 and 1, then 0 to 2),
        // measured outside the engine; a 2x region holding a 2x one runs the same cascade as one 4x region
        build(saw.oversampleBy(4.ign) { it }).latencySamples shouldBe (4.3972203914046455 plusOrMinus 1e-9)
        build(saw.oversampleBy(2.ign) { y -> y.oversampleBy(2.ign) { it } }).latencySamples shouldBe (4.3972203914046455 plusOrMinus 1e-9)
        build(saw.oversampleBy(2.ign) { y -> y.oversampleBy(4.ign) { it } }).latencySamples shouldBe (5.062178167431685 plusOrMinus 1e-9)
    }

    // ── Beside a region ─────────────────────────────────────────────────────────────────────────

    "a parallel beside a region twins it exactly: the clean branch and an identity region sum to twice the round trip" {
        val out = render(saw.parallel({ it }, { y -> y.oversampleBy(4.ign) { it } }))
        val trip = twin(render(saw), 2)

        shouldMatch(out, DoubleArray(trip.size) { 2.0 * trip[it] }, tolerance = 1e-12, label = "twin beside")
    }

    "a parallel inside a region twins with the region's stages: a clean branch beside a nested identity region" {
        // inside a 2x region the nested region's trip is a stage-1 oversampler, and so must the clean branch's twin be
        val out = render(saw.oversampleBy(2.ign) { y -> y.parallel({ it }, { z -> z.oversampleBy(2.ign) { it } }) })
        val nested = render(saw.oversampleBy(4.ign) { it })

        shouldMatch(out, DoubleArray(nested.size) { 2.0 * nested[it] }, tolerance = 1e-12, label = "twin inside")
    }

    /**
     * The lowest level, over a sweep of 250 Hz to 20 kHz in 250 Hz steps, of [voice] at each frequency against a plain
     * sine of it times [sum]: where two copies of one sine meet in a `parallel` (the second at a gain, so the sum is
     * `1 + gain`), a misaligned branch notches the sum.
     */
    fun lowestSumLevel(sum: Double, voice: (Double) -> IgnitorDsl): Pair<Double, Double> {
        var lowest = Double.MAX_VALUE
        var at = 0.0

        for (step in 1..80) {
            val hz = 250.0 * step
            val level = rms(render(voice(hz), count = 40), from = 1024) / (sum * rms(render(Ign.sine(hz.ign), count = 40), from = 1024))

            if (level < lowest) {
                lowest = level
                at = hz
            }
        }

        return lowest to at
    }

    "a parallel inside a region lines up a source beside its input, when the input came in late" {
        // the input ran through an 8x trip before the region; the second saw enters converted up from the base rate,
        // after a phase twin of that trip, so the two branches are the same signal and sum to twice it, exactly
        val late = saw.oversampleBy(8.ign) { it }
        val once = render(late.oversampleBy(2.ign) { it })

        shouldMatch(
            render(late.oversampleBy(2.ign) { y -> y.parallel({ it }, { IgnitorDsl.Saw() }) }),
            DoubleArray(once.size) { 2.0 * once[it] },
            tolerance = 1e-12,
            label = "source beside late input",
        )

        // nested, the source takes the same twin: the outermost region's history is inherited
        val twice = render(late.oversampleBy(2.ign) { y -> y.oversampleBy(2.ign) { it } })

        shouldMatch(
            render(late.oversampleBy(2.ign) { y -> y.oversampleBy(2.ign) { z -> z.parallel({ it }, { IgnitorDsl.Saw() }) } }),
            DoubleArray(twice.size) { 2.0 * twice[it] },
            tolerance = 1e-12,
            label = "nested source beside late input",
        )
    }

    "a parallel inside a nested region lines up a source beside its input, by whole samples" {
        // nested, the source takes no twin of the 16x trip inside the outer body and is padded by whole samples: here
        // within 0.3 dB to 20 kHz (the upper stages disperse little in the audible band). The gain's constant has no
        // time: the branch keeps the source's latency, earlier than the input, negative.
        val (lowest, at) = lowestSumLevel(sum = 1.5) { hz ->
            Ign.sine(hz.ign).oversampleBy(2.ign) { y ->
                y.oversampleBy(8.ign) { it }.oversampleBy(2.ign) { z -> z.parallel({ it }, { Ign.sine(hz.ign).mul(0.5.ign) }) }
            }
        }

        withClue("lowest $lowest at $at Hz") {
            (lowest > 0.95) shouldBe true
        }
    }

    "a parallel inside a nested region lines up an outer input beside its own, when its own came in late" {
        // the outer input at a gain, as the source above
        val (lowest, at) = lowestSumLevel(sum = 1.5) { hz ->
            Ign.sine(hz.ign).oversampleBy(2.ign) { y ->
                y.oversampleBy(2.ign) { it }.oversampleBy(2.ign) { z -> z.parallel({ it }, { y.mul(0.5.ign) }) }
            }
        }

        withClue("lowest $lowest at $at Hz") {
            (lowest > 0.95) shouldBe true
        }
    }

    // ── What runs inside ────────────────────────────────────────────────────────────────────────

    "a source inside a region stays at 1x and is converted up where it enters" {
        val dry = render(saw)

        shouldMatch(render(Ign.silence().oversampleBy(4.ign) { it.plus(IgnitorDsl.Saw()) }), twin(dry, 2), tolerance = 1e-12, label = "1x source")
    }

    "a pitch modulation inside a region stays at 1x with its source: fm, pitchMod and a vibrato over silence" {
        val fm = Ign.sine().fm(modulator = Ign.sine(), ratio = 2.ign, depth = 300.ign)
        val pm = Ign.sine().pitchMod(Ign.sine(5.ign).mul(0.5.ign))
        val vib = Ign.sine().plus(Ign.silence()).vibrato(rate = 5.ign, semitones = 1.ign)

        for ((name, voice) in listOf("fm" to fm, "pitchMod" to pm, "vibrato over silence" to vib)) {
            shouldMatch(render(Ign.silence().oversampleBy(4.ign) { it.plus(voice) }), twin(render(voice), 2), tolerance = 1e-12, label = "1x mod $name")
        }
    }

    "a body lambda's parameter keeps its meaning in a nested body: the outer signal, converted up" {
        // the nested region's own input is silence, so what comes out is the outer y through both trips: one 4x trip
        val expected = render(saw.oversampleBy(4.ign) { it })

        shouldMatch(render(saw.oversampleBy(2.ign) { y -> Ign.silence().oversampleBy(2.ign) { z -> z.plus(y) } }), expected, tolerance = 1e-12, label = "lexical")
    }

    "an outer input is converted up by every step between: two steps in one region, and two regions out" {
        // either way the outer y runs the trip of one 8x region
        val expected = render(saw.oversampleBy(8.ign) { it })

        shouldMatch(
            render(saw.oversampleBy(2.ign) { y -> Ign.silence().oversampleBy(4.ign) { z -> z.plus(y) } }),
            expected,
            tolerance = 1e-12,
            label = "two steps",
        )
        shouldMatch(
            render(saw.oversampleBy(2.ign) { y -> Ign.silence().oversampleBy(2.ign) { Ign.silence().oversampleBy(2.ign) { w -> w.plus(y) } } }),
            expected,
            tolerance = 1e-12,
            label = "two out",
        )
    }

    "a body's input used as a pitch modulation is silence: the modulation is built with its source, outside the region" {
        val unmodulated = twin(render(Ign.sine().pitchMod(Ign.silence())), 1)

        shouldMatch(render(saw.oversampleBy(2.ign) { y -> Ign.sine().pitchMod(y) }), unmodulated, tolerance = 1e-12, label = "input as mod")
    }

    "a region with no step to take shares the input it puts in place: every reference at one depth is the same node" {
        // an input that reads a region around it, so placing it one region deeper rewrites it
        val input = IgnitorDsl.RegionInput(up = 0).lowpass(800.ign)
        val nested = IgnitorDsl.OversampleBy(
            inner = Ign.silence(),
            body = IgnitorDsl.Plus(left = IgnitorDsl.RegionInput(up = 1), right = IgnitorDsl.RegionInput(up = 1)),
            factor = 2.ign,
        )
        val placed = nested.withRegionInput(input) as IgnitorDsl.OversampleBy
        val sum = placed.body as IgnitorDsl.Plus

        (sum.left === sum.right) shouldBe true
    }

    "a region with no step to take keeps the meaning of the inputs around it" {
        // inside 4x, oversampleAtLeast(2) is no region: z is y, so z + y is twice y
        shouldMatch(
            render(saw.oversampleBy(4.ign) { y -> y.oversampleAtLeast(2.ign) { z -> z.plus(y) } }),
            render(saw.oversampleBy(4.ign) { it.plus(it) }),
            tolerance = 1e-12,
            label = "no step outer",
        )
        // and an input it hands into a nested region still points at the region it came from
        shouldMatch(
            render(saw.oversampleBy(2.ign) { y -> y.oversampleAtLeast(2.ign) { z -> Ign.silence().oversampleBy(2.ign) { w -> w.plus(z) } } }),
            render(saw.oversampleBy(4.ign) { it }),
            tolerance = 1e-12,
            label = "no step shifted",
        )
    }

    "a region with no step to take ends the voice in an envelope as its body would" {
        val bare = build(saw.adsr(0.01.ign, 0.1.ign, 0.5.ign, 0.2.ign)).endsInEnvelope

        bare shouldBe true
        build(saw.oversampleBy(1.ign) { it.adsr(0.01.ign, 0.1.ign, 0.5.ign, 0.2.ign) }).endsInEnvelope shouldBe bare
    }

    "a filter inside a region keeps its cutoff in Hz" {
        val tone = Ign.sine(3000.ign)
        val outside = rms(render(tone.lowpass(1000.ign)), from = 512)
        val inside = rms(render(tone.oversampleBy(4.ign) { it.lowpass(1000.ign) }), from = 512)

        // 3 kHz an octave and a half over a 1 kHz cutoff: read at the base rate, the region's filter would sit at 4 kHz
        withClue("outside $outside, inside $inside") {
            abs(inside / outside - 1.0) shouldBeLessThan 0.05
        }
    }

    "an envelope inside a region keeps its times, and a held gate stays held" {
        val one = Ign.constant(1.0)
        val outside = render(one.adsr(0.01.ign, 0.05.ign, 0.5.ign, 0.1.ign), gateEnd = Int.MAX_VALUE, count = 40)
        val inside = render(one.oversampleBy(4.ign) { it.adsr(0.01.ign, 0.05.ign, 0.5.ign, 0.1.ign) }, gateEnd = Int.MAX_VALUE, count = 40)
        val delay = Oversampler.groupDelaySamples(2).toInt()

        // in the attack (sample 240 of 480), in the decay (sample 1500) and held at the sustain (the last block)
        for (k in listOf(240, 1500, 40 * blockFrames - 1 - delay)) {
            withClue("envelope sample $k") {
                inside[k + delay] shouldBe (outside[k] plusOrMinus 0.01)
            }
        }
    }

    "the DC blocker's pole is mapped to the region's rate, so its knee stays in Hz" {
        DistortionCore.dcBlockCoefficientAt(coefficient = 0.995, stages = 0) shouldBe 0.995
        val atFour = DistortionCore.dcBlockCoefficientAt(coefficient = 0.995, stages = 2)

        (atFour * atFour * atFour * atFour) shouldBe (0.995 plusOrMinus 1e-15)

        // a constant into a shaper: the blocker takes it away as fast inside a 4x region as outside
        val dc = Ign.constant(0.1)
        val outside = render(shaped(dc))
        val inside = render(dc.oversampleBy(4.ign) { shaped(it) })

        for (k in listOf(100, 300, 600)) {
            withClue("dc sample $k") {
                inside[k] shouldBe (outside[k] plusOrMinus 0.003)
            }
        }
    }

    "coarse holds the same time inside a region" {
        val tone = Ign.sine(2000.ign)
        val heldOutside = twin(render(tone.coarse(4.ign)), 2)
        val unheld = twin(render(tone), 2)
        val inside = render(tone.oversampleBy(4.ign) { it.coarse(4.ign) })

        // nearer to the signal held for 4 samples at 48 kHz than to the signal not held at all
        val toHeld = rmsOfDifference(inside, heldOutside, from = 256)
        val toUnheld = rmsOfDifference(inside, unheld, from = 256)

        withClue("to held $toHeld, to unheld $toUnheld") {
            toHeld shouldBeLessThan toUnheld
        }
    }

    "a shimmer inside a 16x region has the ring it needs: its grains still reach back as far as outside" {
        // The input stops at 0.25 s. An octave grain looks back 0.3 s (twice its 0.15 s length), so after the input
        // stops the grains still play what came before; a ring sized for the base rate holds only 0.125 s at 16x, its
        // lookback wraps to a recent stretch of the ring, and from about 0.42 s the grains read silence.
        val stopped = saw.adsr(0.001.ign, 0.0.ign, 1.0.ign, 0.001.ign)
        val gateEnd = 12000
        val outside = render(stopped.shimmer(wet = 1.0.ign, feedback = 0.0.ign), gateEnd = gateEnd, count = 190)
        val inside = render(stopped.oversampleBy(16.ign) { it.shimmer(wet = 1.0.ign, feedback = 0.0.ign) }, gateEnd = gateEnd, count = 190)
        val from = 20160
        val outsideLate = rms(outside, from = from)
        val insideLate = rms(inside, from = from)

        withClue("shimmer after the input stopped: outside $outsideLate, inside $insideLate") {
            inside.all { it.isFinite() } shouldBe true
            (outsideLate > 1e-3) shouldBe true
            (insideLate > 0.5 * outsideLate) shouldBe true
        }
    }

    "the region input means nothing outside a region: silence" {
        render(IgnitorDsl.RegionInput(up = 0)).all { it == 0.0 } shouldBe true
    }

    "what follows a region reads its result: half of an identity region is half the round trip" {
        val trip = twin(render(saw), 2)

        shouldMatch(render(saw.oversampleBy(4.ign) { it }.mul(0.5.ign)), DoubleArray(trip.size) { 0.5 * trip[it] }, tolerance = 1e-12, label = "follows")
    }
})
