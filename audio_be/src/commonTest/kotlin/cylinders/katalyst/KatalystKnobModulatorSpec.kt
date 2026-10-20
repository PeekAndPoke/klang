/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.ignitor.ConstantIgnitor
import io.peekandpoke.klang.audio_be.ignitor.IgniteContext
import io.peekandpoke.klang.audio_be.ignitor.Ignitor
import io.peekandpoke.klang.audio_be.ignitor.Ignitors
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystKnobDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.common.infra.klangMapOf
import io.peekandpoke.klang.common.math.PerlinNoise
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/**
 * The Katalyst's own modulators (step 5a of KLANG-00302 (katalyst knob expressions), D3, Q3, Q10 to Q12):
 * the modulator instructions of [KatalystKnobProgram], their clocks and their laws.
 *
 * **Two oracles.** The hand laws below (the shapes written out from their definitions, `kotlin.math.sin` for the sine)
 * at hand-computed phases; and the Ignitor's own oscillators ([Ignitors]), rendered with the same frequency at the
 * same phase (a phase input set to it), whose first sample is the Ignitor's value there. The Ignitor is built from its
 * factories with their defaults, which are the defaults of the nodes the Ignitor's doors build (`saw`, `ramp`, `tri`,
 * and `square`, which builds a `Pulze`). So a wrong law, a wrong edge or a wrong phase turns a row red, and the
 * evaluator's law functions are never their own oracle. The noises cannot be set to a position from outside, so their
 * oracle is the Ignitor's walk rendered sample by sample from the same seed.
 *
 * The clock: 46.875 Hz at 48 kHz is `dt = 2^-10` per sample, an eighth of a cycle per 128-frame block, every phase
 * exact in binary; 468.75 and 937.5 Hz step 1.25 and 2.5 cycles per block (the wrap's modulo branch).
 */
class KatalystKnobModulatorSpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128

    /** An eighth of a cycle per block: `dt = 2^-10`. */
    val eighth = EIGHTH_HZ

    /** One sample's phase at [eighth]: the saw's flyback, half the pulse's minimum flank. */
    val dt = EIGHTH_DT

    fun c(v: Double) = KatalystKnobDsl.Constant(v)

    fun program(node: KatalystKnobDsl, noises: KatalystNoiseOrdinals = KatalystNoiseOrdinals()): KatalystKnobProgram =
        KatalystKnobProgram.compile(node = node, sampleRate = sampleRate, blockFrames = blockFrames, noises = noises)

    fun KatalystKnobProgram.value(): Double = registers[resultRegister]

    /** The values of [n] ticks, each read after its tick (the value of that block). */
    fun KatalystKnobProgram.ticks(n: Int): DoubleArray = DoubleArray(n) {
        tick()
        value()
    }

    /** The hand clock of tick [k] at [eighth], plus [offset], one wrap back into `[0, 1)`. */
    fun handPhase(k: Int, offset: Double): Double {
        val p = (k % 8) * 0.125 + offset

        return if (p >= 1.0) p - 1.0 else p
    }

    // The hand laws (`handSine`, `handSaw`, `handTri`, `handSquare`) live in `_katalyst_modulator_hand_laws.kt`, shared
    // with the chain rows of step 5b.

    // ── The Ignitor oracle ───────────────────────────────────────────────────────────────────────────────────────

    fun ctx(): IgniteContext = IgniteContext(
        sampleRate = sampleRate,
        voiceDurationFrames = sampleRate,
        gateEndFrame = sampleRate,
        scratchBuffers = ScratchBuffers(blockFrames),
        random = Random(1),
    ).apply {
        updateOffsetAndLength(offset = 0, length = blockFrames)
        voiceElapsedFrames = 0
    }

    /** The shapes the periodic rows run, with how to build the Katalyst node and the Ignitor at a phase. */
    data class Shape(
        val name: String,
        val node: (freq: KatalystKnobDsl, phase: KatalystKnobDsl) -> KatalystKnobDsl,
        val ignitor: (freq: Double, phase: Double) -> Ignitor,
        val hand: (p: Double) -> Double,
    )

    val shapes = listOf(
        Shape(
            name = "sine",
            node = { f, p -> KatalystKnobDsl.Sine(freq = f, phase = p) },
            ignitor = { f, p -> Ignitors.sine(freq = ConstantIgnitor(f), phase = ConstantIgnitor(p)) },
            hand = { p -> handSine(p) },
        ),
        Shape(
            name = "saw",
            node = { f, p -> KatalystKnobDsl.Saw(freq = f, phase = p) },
            ignitor = { f, p -> Ignitors.saw(freq = ConstantIgnitor(f), phase = ConstantIgnitor(p)) },
            hand = { p -> handSaw(p) },
        ),
        Shape(
            name = "ramp",
            node = { f, p -> KatalystKnobDsl.Ramp(freq = f, phase = p) },
            ignitor = { f, p -> Ignitors.ramp(freq = ConstantIgnitor(f), phase = ConstantIgnitor(p)) },
            hand = { p -> -handSaw(p) },
        ),
        Shape(
            name = "tri",
            node = { f, p -> KatalystKnobDsl.Tri(freq = f, phase = p) },
            ignitor = { f, p -> Ignitors.tri(freq = ConstantIgnitor(f), phase = ConstantIgnitor(p)) },
            hand = { p -> handTri(p) },
        ),
        Shape(
            name = "square, duty 0.4",
            node = { f, p -> KatalystKnobDsl.Square(freq = f, duty = c(0.4), phase = p) },
            ignitor = { f, p ->
                Ignitors.pulze(freq = ConstantIgnitor(f), duty = ConstantIgnitor(0.4), phase = ConstantIgnitor(p))
            },
            hand = { p -> handSquare(p, duty = 0.4) },
        ),
    )

    /** The Ignitor's value at phase [phase]: its first sample with the phase input set to it. */
    fun ignitorAt(shape: Shape, freq: Double, phase: Double): Double {
        val buffer = AudioBuffer(blockFrames)

        shape.ignitor(freq, phase).generate(buffer, 0.0, ctx())

        return buffer[0]
    }

    fun shouldBeSameBits(actual: Double, expected: Double, clue: String) {
        withClue("$clue: expected $expected (${expected.toRawBits()}), got $actual (${actual.toRawBits()})") {
            actual.toRawBits() shouldBe expected.toRawBits()
        }
    }

    /** The offsets of the periodic rows: none, a plain one, the middle of the rising edge, the saw's flyback. */
    val offsets = doubleArrayOf(0.0, 0.3, 0.2, dt, 1.0 - dt / 2.0)

    // ── The laws at the hand clock ───────────────────────────────────────────────────────────────────────────────

    "each periodic shape reads its law at the hand clock plus its phase, through ticks 0, 1 and past the wrap" {
        // Ticks 0 to 17: the clock wraps at tick 8 (eight eighths), and with an offset the READ wraps earlier.
        for (shape in shapes) {
            for (offset in offsets) {
                val values = program(shape.node(c(eighth), c(offset))).ticks(18)

                for (k in values.indices) {
                    val p = handPhase(k, offset)

                    withClue("${shape.name}, offset $offset, tick $k at phase $p") {
                        values[k] shouldBe (shape.hand(p) plusOrMinus 1e-9)
                    }
                }
            }
        }
    }

    "hand values that pin the edges: the square's rise, its duty, the saw's flyback, the triangle's peak" {
        // Written out, not computed: the middle of a two-sample rise is 0, the middle of a one-sample flyback is 0.
        fun at(node: KatalystKnobDsl, tick: Int): Double = program(node).ticks(tick + 1)[tick]

        at(KatalystKnobDsl.Square(freq = c(eighth), duty = c(0.4), phase = c(dt)), tick = 0) shouldBe 0.0
        at(KatalystKnobDsl.Square(freq = c(eighth), duty = c(0.4), phase = c(0.2)), tick = 1) shouldBe 1.0
        // Tick 2 is phase 0.45: past a duty of 0.4, inside one of 0.5.
        at(KatalystKnobDsl.Square(freq = c(eighth), duty = c(0.4), phase = c(0.2)), tick = 2) shouldBe -1.0
        at(KatalystKnobDsl.Square(freq = c(eighth), duty = c(0.5), phase = c(0.2)), tick = 2) shouldBe 1.0
        // The duty's default is 0.5.
        at(KatalystKnobDsl.Square(freq = c(eighth), phase = c(0.2)), tick = 2) shouldBe 1.0
        at(KatalystKnobDsl.Saw(freq = c(eighth), phase = c(1.0 - dt / 2.0)), tick = 0) shouldBe 0.0
        // The saw at the bottom, the ramp at the top, the triangle at its peak: tick 4 is phase 0.5.
        at(KatalystKnobDsl.Saw(freq = c(eighth)), tick = 0) shouldBe -1.0
        at(KatalystKnobDsl.Ramp(freq = c(eighth)), tick = 0) shouldBe 1.0
        at(KatalystKnobDsl.Tri(freq = c(eighth)), tick = 4) shouldBe 1.0
        at(KatalystKnobDsl.Tri(freq = c(eighth), phase = c(0.25)), tick = 0) shouldBe 0.0
    }

    // ── The Ignitor at the same phase ────────────────────────────────────────────────────────────────────────────

    "each periodic shape is the Ignitor's value at the same phase, bit for bit, at every tick and offset" {
        for (shape in shapes) {
            for (offset in offsets) {
                val values = program(shape.node(c(eighth), c(offset))).ticks(18)

                for (k in values.indices) {
                    val p = handPhase(k, offset)

                    shouldBeSameBits(
                        actual = values[k],
                        expected = ignitorAt(shape = shape, freq = eighth, phase = p),
                        clue = "${shape.name}, offset $offset, tick $k at phase $p",
                    )
                }
            }
        }
    }

    "each periodic shape samples the Ignitor's own stream at every block start, one or more cycles per block included" {
        // The Ignitor renders sample by sample with no phase offset; tick k must be its sample k * 128. At these
        // frequencies its per-sample phase is exact in binary, so the waves match bit for bit; the sine's accumulator
        // runs in radians, so it matches to rounding. 1.25 and 2.5 cycles per block: the clock wraps every block, by
        // the modulo branch at 2.5.
        for (shape in shapes) {
            for (freq in doubleArrayOf(eighth, 468.75, 937.5)) {
                val values = program(shape.node(c(freq), c(0.0))).ticks(24)
                val ignitor = shape.ignitor(freq, 0.0)
                val ctx = ctx()
                val buffer = AudioBuffer(blockFrames)

                for (k in values.indices) {
                    ignitor.generate(buffer, 0.0, ctx)

                    val clue = "${shape.name} at $freq Hz, tick $k"

                    if (shape.name == "sine") {
                        withClue(clue) { values[k] shouldBe (buffer[0] plusOrMinus 1e-10) }
                    } else {
                        shouldBeSameBits(actual = values[k], expected = buffer[0], clue = clue)
                    }
                }
            }
        }
    }

    // ── A frequency from a slot ──────────────────────────────────────────────────────────────────────────────────

    "a freq from a slot: a changed slot sets the advance from the tick after the change" {
        // `Kat.sine(Kat.param("r", 0.5))` built by hand. The caller (`KatalystKnob`) loads the slot register; here the
        // test writes it. Ticks 0 and 1 at an eighth per block; the slot then doubles: tick 2 still reads phase 0.25
        // (the clock moved by the old rate), and its advance is the new one, so tick 3 reads 0.5, not 0.375.
        val p = program(KatalystKnobDsl.Sine(freq = KatalystKnobDsl.Param("r", 0.5)))
        val slot = p.slotRegisters[0]

        p.registers[slot] = eighth
        p.tick()
        p.value() shouldBe (handSine(0.0) plusOrMinus 1e-9)
        p.tick()
        p.value() shouldBe (handSine(0.125) plusOrMinus 1e-9)

        p.registers[slot] = 2.0 * eighth
        p.tick()
        p.value() shouldBe (handSine(0.25) plusOrMinus 1e-9)
        p.tick()
        p.value() shouldBe (handSine(0.5) plusOrMinus 1e-9)
        p.tick()
        p.value() shouldBe (handSine(0.75) plusOrMinus 1e-9)
    }

    "a moving freq or duty rebakes the wave's edges, as the Ignitor's wave oscillator does" {
        // The saw read one sample's half before its cycle ends (phase 1 - 2^-11), at 375 Hz (one cycle per block, so
        // the clock stays at 0) and then at 750 Hz (two). The flyback is one sample: 2^-7 of a cycle, then 2^-6, so
        // the same phase sits 15/16 and then 31/32 of the way down: -0.875, then -0.9375.
        val saw = program(KatalystKnobDsl.Saw(freq = KatalystKnobDsl.Param("f", 0.0), phase = c(1.0 - dt / 2.0)))
        val f = saw.slotRegisters[0]

        saw.registers[f] = 375.0
        saw.tick()
        saw.value() shouldBe -0.875
        saw.registers[f] = 750.0
        saw.tick()
        saw.value() shouldBe -0.9375
        shouldBeSameBits(
            actual = saw.value(),
            expected = ignitorAt(shape = shapes[1], freq = 750.0, phase = 1.0 - dt / 2.0),
            clue = "the saw at 750 Hz",
        )

        // The square read on its rising edge (phase 2^-8), at 375 Hz and then 750 Hz (the clock stays at 0 again). The
        // edge is two samples: 2^-6 of a cycle, then 2^-5, so the same phase is a quarter and then an eighth of the way
        // up: -0.5, then -0.75.
        val rising = program(
            KatalystKnobDsl.Square(freq = KatalystKnobDsl.Param("f", 0.0), duty = c(0.4), phase = c(1.0 / 256.0)),
        )
        val rf = rising.slotRegisters[0]

        rising.registers[rf] = 375.0
        rising.tick()
        rising.value() shouldBe -0.5
        rising.registers[rf] = 750.0
        rising.tick()
        rising.value() shouldBe -0.75
        shouldBeSameBits(
            actual = rising.value(),
            expected = ignitorAt(shape = shapes[4], freq = 750.0, phase = 1.0 / 256.0),
            clue = "the square at 750 Hz",
        )

        // The square at phase 0.45 on ticks 2 and 10: low at a duty of 0.4, high once the slot says 0.5.
        val square = program(
            KatalystKnobDsl.Square(freq = c(eighth), duty = KatalystKnobDsl.Param("d", 0.4), phase = c(0.2)),
        )
        val d = square.slotRegisters[0]
        val values = DoubleArray(11)

        for (k in 0..10) {
            square.registers[d] = if (k < 10) 0.4 else 0.5
            square.tick()
            values[k] = square.value()
        }

        values[2] shouldBe -1.0
        values[10] shouldBe 1.0
    }

    "run moves no clock: evaluating twice without a tick gives the same value" {
        val p = program(KatalystKnobDsl.Tri(freq = c(eighth), phase = c(0.3)))

        p.tick()
        p.run()

        val first = p.value()

        p.run()
        p.value() shouldBe first
        first shouldBe (handTri(0.425) plusOrMinus 1e-12)
    }

    // ── The noises ───────────────────────────────────────────────────────────────────────────────────────────────

    data class Noise(
        val name: String,
        val node: (rate: KatalystKnobDsl) -> KatalystKnobDsl,
        val ignitor: (seed: Int) -> Ignitor,
    )

    val noises = listOf(
        Noise(
            name = "perlin",
            node = { r -> KatalystKnobDsl.PerlinNoise(rate = r, octaves = c(3.7), persistence = c(0.6)) },
            ignitor = { seed ->
                Ignitors.perlinNoise(
                    rng = Random(seed),
                    rate = ConstantIgnitor(2.0),
                    octaves = ConstantIgnitor(3.7),
                    persistence = ConstantIgnitor(0.6),
                )
            },
        ),
        Noise(
            name = "berlin",
            node = { r -> KatalystKnobDsl.BerlinNoise(rate = r, octaves = c(3.7), persistence = c(0.6)) },
            ignitor = { seed ->
                Ignitors.berlinNoise(
                    rng = Random(seed),
                    rate = ConstantIgnitor(2.0),
                    octaves = ConstantIgnitor(3.7),
                    persistence = ConstantIgnitor(0.6),
                )
            },
        ),
    )

    "perlin and berlin walk the same sequence on two builds: one fixed seed" {
        for (noise in noises) {
            val a = program(noise.node(c(2.0))).ticks(64)
            val b = program(noise.node(c(2.0))).ticks(64)

            withClue(noise.name) {
                a.map { it.toRawBits() } shouldBe b.map { it.toRawBits() }
                // Not a constant walk: the sequence moves.
                (a.toSet().size > 1).shouldBeTrue()
            }
        }
    }

    "perlin and berlin sample the Ignitor's walk at each block start: rate * PERLIN_STEP per sample, a block a tick" {
        // Block 0 is the Ignitor's first sample bit for bit (the same seed, the same construction). Later blocks: the
        // Ignitor adds its step 128 times per block where the knob adds 128 steps once, so they agree to rounding (the
        // walk position differs by a few ulps of about 300 over 64 blocks).
        for (noise in noises) {
            val values = program(noise.node(c(2.0))).ticks(64)
            val ignitor = noise.ignitor(KATALYST_KNOB_NOISE_SEED)
            val ctx = ctx()
            val buffer = AudioBuffer(blockFrames)

            for (k in values.indices) {
                ignitor.generate(buffer, 0.0, ctx)

                if (k == 0) {
                    shouldBeSameBits(actual = values[0], expected = buffer[0], clue = "${noise.name}, block 0")
                } else {
                    withClue("${noise.name}, tick $k") { values[k] shouldBe (buffer[0] plusOrMinus 1e-8) }
                }
            }
        }
    }

    "the n-th noise of a counter is seeded base + n: two noises in one knob, or two knobs on one counter, differ" {
        // The maintainer's rule (2026-10-12): fixed per position. Ordinal 0 is the base seed (the oracle rows above);
        // ordinal n is the Ignitor's walk from `Random(base + n)`, block 0 bit for bit.
        fun ignitorFirst(noise: Noise, seed: Int): Double {
            val buffer = AudioBuffer(blockFrames)

            noise.ignitor(seed).generate(buffer, 0.0, ctx())

            return buffer[0]
        }

        for (noise in noises) {
            val n0 = ignitorFirst(noise, KATALYST_KNOB_NOISE_SEED)
            val n1 = ignitorFirst(noise, KATALYST_KNOB_NOISE_SEED + 1)
            val n2 = ignitorFirst(noise, KATALYST_KNOB_NOISE_SEED + 2)

            withClue(noise.name) {
                (n0 != n1).shouldBeTrue()

                // Two noises in one knob: `perlin.minus(perlin)` is not 0, it is walk 0 minus walk 1.
                val difference = program(KatalystKnobDsl.Minus(left = noise.node(c(2.0)), right = noise.node(c(2.0))))

                difference.tick()
                shouldBeSameBits(actual = difference.value(), expected = n0 - n1, clue = "${noise.name} minus itself")

                // Two knobs on one counter: the second knob's noise is walk 2, after the first knob's two.
                val counter = KatalystNoiseOrdinals()
                val first = program(
                    node = KatalystKnobDsl.Plus(left = noise.node(c(2.0)), right = noise.node(c(2.0))),
                    noises = counter,
                )
                val second = program(noise.node(c(2.0)), counter)

                first.tick()
                second.tick()
                shouldBeSameBits(actual = first.value(), expected = n0 + n1, clue = "${noise.name}, the first knob")
                shouldBeSameBits(actual = second.value(), expected = n2, clue = "${noise.name}, the second knob")
                counter.next shouldBe 3
            }
        }
    }

    "a noise whose modulator index differs from its instruction index reads its own walk into its own register" {
        // Engine follow-up 39 moved the noise arms into `samplePerlin(i, m)` and `sampleBerlin(i, m)`. Every row above
        // has the n-th noise as the n-th instruction, so the two indices could be swapped unseen. Here a slot
        // instruction comes first: the two noises are instructions 1 and 2 and modulators 0 and 1.
        fun ignitorFirst(noise: Noise, seed: Int): Double {
            val buffer = AudioBuffer(blockFrames)

            noise.ignitor(seed).generate(buffer, 0.0, ctx())

            return buffer[0]
        }

        for (noise in noises) {
            val n0 = ignitorFirst(noise, KATALYST_KNOB_NOISE_SEED)
            val n1 = ignitorFirst(noise, KATALYST_KNOB_NOISE_SEED + 1)
            val p = program(
                KatalystKnobDsl.Plus(
                    left = KatalystKnobDsl.Plus(left = KatalystKnobDsl.Param("a", 1.0), right = c(2.0)),
                    right = KatalystKnobDsl.Minus(left = noise.node(c(2.0)), right = noise.node(c(2.0))),
                )
            )

            withClue(noise.name) {
                p.instructionCount shouldBe 5
                p.registers[p.slotRegisters[0]] = 1.0
                p.tick()
                shouldBeSameBits(actual = p.value(), expected = (1.0 + 2.0) + (n0 - n1), clue = "${noise.name}, tick 0")
            }
        }
    }

    "a perlin clock wrapped by 256 reads the same value as the unwrapped one, bit for bit" {
        // The law's periodicity, on which the clock's wrap rests: the permutation repeats every 256 cells and each fBm
        // octave scales by a power of two, and both `x - 256 k` and the fractional parts are exact in doubles.
        val noise = PerlinNoise(Random(KATALYST_KNOB_NOISE_SEED))
        val positions = doubleArrayOf(256.0, 300.125, 511.999, 1000.37, 65_536.5, 123_456.789, 999_999.001)

        for (x in positions) {
            val wrapped = x - 256.0 * floor(x / 256.0)

            for (octaves in 1..8) {
                shouldBeSameBits(
                    actual = noise.fbm(x = wrapped, octaves = octaves, persistence = 0.6),
                    expected = noise.fbm(x = x, octaves = octaves, persistence = 0.6),
                    clue = "x $x, octaves $octaves",
                )
            }
        }
    }

    "a fast perlin wraps its clock every few blocks and still samples the Ignitor's unwrapped walk" {
        // Rate 100: 38.4 cells a block, so the clock passes 256 every seventh block. The Ignitor's walk is never
        // wrapped; the two differ only by the rounding of their positions (about 1e-13 here) times the walk's slope
        // (at most about 60 per cell with three octaves), well inside 1e-7. A wrong period is off by order 1.
        val values = program(
            KatalystKnobDsl.PerlinNoise(rate = c(100.0), octaves = c(3.0), persistence = c(0.6)),
        ).ticks(64)
        val ignitor = Ignitors.perlinNoise(
            rng = Random(KATALYST_KNOB_NOISE_SEED),
            rate = ConstantIgnitor(100.0),
            octaves = ConstantIgnitor(3.0),
            persistence = ConstantIgnitor(0.6),
        )
        val ctx = ctx()
        val buffer = AudioBuffer(blockFrames)

        for (k in values.indices) {
            ignitor.generate(buffer, 0.0, ctx)

            withClue("tick $k") { values[k] shouldBe (buffer[0] plusOrMinus 1e-7) }
        }
    }

    "a berlin clock far past 2^31 cells of its top octave stays in [-1, 1]: its clock wraps at 2^23" {
        // Rate 1e6: 384,000 cells a block, the clock passes 2^23 every 22 blocks. Unwrapped, the top octave (128 times
        // the clock) passes 2^31 by block 44, `toInt()` saturates and the value leaves [-1, 1] without bound.
        val values = program(
            KatalystKnobDsl.BerlinNoise(rate = c(1e6), octaves = c(8.0), persistence = c(0.9)),
        ).ticks(300)

        for (k in values.indices) {
            withClue("tick $k: ${values[k]}") { (values[k] >= -1.0 && values[k] <= 1.0).shouldBeTrue() }
        }
    }

    "a perlin driven far past 2^24 cells reads the law at its position modulo 256, bit for bit: its clock wraps" {
        // Rate 1e6: 384,000 cells a block, past 2^24 by block 44 and past 1e8 by block 300. The oracle is the law
        // itself at the position kept modulo 256 here: the generator and the start drawn from one `Random(seed)` in the
        // Ignitor's order, the per-block step written out (1e6 * 0.003 per sample, 128 samples). Both the knob's wrap
        // and `%` are exact for a positive position, so they agree bit for bit. Unwrapped, the position loses its
        // fractional bits to its magnitude and the top octave's `toInt()` saturates.
        val values = program(
            KatalystKnobDsl.PerlinNoise(rate = c(1e6), octaves = c(8.0), persistence = c(0.6)),
        ).ticks(300)
        val rng = Random(KATALYST_KNOB_NOISE_SEED)
        val noise = PerlinNoise(rng)
        var position = rng.nextDouble() * 256.0

        for (k in values.indices) {
            shouldBeSameBits(
                actual = values[k],
                expected = noise.fbm(x = position, octaves = 8, persistence = 0.6),
                clue = "tick $k at position $position",
            )

            position = (position + 1e6 * 0.003 * 128.0) % 256.0
        }
    }

    "a non-finite noise rate holds the walk where it is, and a finite one moves it on from there" {
        for (noise in noises) {
            val reference = program(noise.node(c(2.0))).ticks(8)
            val p = program(noise.node(KatalystKnobDsl.Param("r", 2.0)))
            val slot = p.slotRegisters[0]
            val rates = doubleArrayOf(2.0, 2.0, 2.0, Double.NaN, Double.POSITIVE_INFINITY, 2.0, 2.0)
            val values = DoubleArray(rates.size)

            for (k in rates.indices) {
                p.registers[slot] = rates[k]
                p.tick()
                values[k] = p.value()
            }

            withClue(noise.name) {
                // Ticks 3 and 4 do not advance, so ticks 3, 4 and 5 read the walk where tick 3 found it.
                values.take(4).map { it.toRawBits() } shouldBe reference.take(4).map { it.toRawBits() }
                values[4].toRawBits() shouldBe reference[3].toRawBits()
                values[5].toRawBits() shouldBe reference[3].toRawBits()
                values[6].toRawBits() shouldBe reference[4].toRawBits()
            }
        }
    }

    // ── Arithmetic over a modulator, and the fold ────────────────────────────────────────────────────────────────

    "an arithmetic tree over a modulator runs through the step 4 ops: Kat.sine(1).range(400, 1600)" {
        val p = program(
            KatalystKnobDsl.Range(inner = KatalystKnobDsl.Sine(freq = c(1.0)), from = c(400.0), to = c(1600.0)),
        )

        p.instructionCount shouldBe 2

        val values = p.ticks(401)

        // 1 Hz at 48 kHz in 128-frame blocks: 128 / 48000 of a cycle per block; tick 400 is past one cycle.
        for (k in intArrayOf(0, 1, 2, 100, 375, 400)) {
            val phase = k * 128.0 / 48000.0

            withClue("tick $k at phase $phase") {
                values[k] shouldBe ((1000.0 + 600.0 * sin(2.0 * PI * phase)) plusOrMinus 1e-6)
            }
        }
    }

    "a modulator is never folded: isModulated holds iff the tree has one, and a constant tree still folds" {
        val modulators = listOf(
            KatalystKnobDsl.Sine(freq = c(1.0)),
            KatalystKnobDsl.Saw(freq = c(1.0)),
            KatalystKnobDsl.Ramp(freq = c(1.0)),
            KatalystKnobDsl.Tri(freq = c(1.0)),
            KatalystKnobDsl.Square(freq = c(1.0)),
            KatalystKnobDsl.PerlinNoise(),
            KatalystKnobDsl.BerlinNoise(),
        )

        for (m in modulators) {
            val bare = program(m)
            val above = program(KatalystKnobDsl.Abs(inner = KatalystKnobDsl.Times(left = c(2.0), right = m)))

            withClue(m.toString()) {
                bare.isModulated.shouldBeTrue()
                bare.isConstant.shouldBeFalse()
                bare.instructionCount shouldBe 1
                above.isModulated.shouldBeTrue()
                above.isConstant.shouldBeFalse()
                withClue("the modulator, the times and the abs") { above.instructionCount shouldBe 3 }
            }
        }

        // A constant subtree inside a modulator's operand still folds: `2 * 0.5` is one constant register.
        val folded = program(KatalystKnobDsl.Sine(freq = KatalystKnobDsl.Times(left = c(2.0), right = c(0.5))))

        folded.instructionCount shouldBe 1

        val slotOnly = program(KatalystKnobDsl.Plus(left = KatalystKnobDsl.Param("a", 1.0), right = c(2.0)))
        val constant = program(KatalystKnobDsl.Times(left = c(2.0), right = c(3.0)))

        slotOnly.isModulated.shouldBeFalse()
        constant.isModulated.shouldBeFalse()
        constant.isConstant.shouldBeTrue()
    }

    // ── Noise seeds by position in a chain ───────────────────────────────────────────────────────────────────────

    "the chain builder counts the noises of the whole chain: two noise knobs differ, a parallel branch counts too" {
        // Two faders, each a perlin ranged to [0.5, 1.5]; the first block reads each walk at its start (the tick runs,
        // then advances). A fader's first factor snaps, so a block of ones comes out as the product of the two. The
        // expected factors are the two knobs built on one counter (ordinals 0 and 1); a builder that seeded every knob
        // alike would give the first one squared.
        val knob = KatalystKnobDsl.Range(inner = KatalystKnobDsl.PerlinNoise(), from = c(0.5), to = c(1.5))
        val counter = KatalystNoiseOrdinals()
        val g0 = testKnob(knob, counter).value
        val g1 = testKnob(knob, counter).value

        withClue("the two positions give two factors") { (g0 != g1).shouldBeTrue() }

        fun render(dsl: KatalystDsl): Double {
            val chain = KatalystChainBuilder.build(
                dsl = dsl,
                sampleRate = sampleRate,
                blockFrames = blockFrames,
                rings = SizedBuffers.forRings(sampleRate),
                reverbs = ReverbUnits(sampleRate),
            )
            val ctx = KatalystContext(blockFrames = blockFrames, mixBuffer = StereoBuffer(blockFrames))

            ctx.mixBuffer.left.fill(1.0)
            ctx.mixBuffer.right.fill(1.0)
            chain.applyParams(null)
            chain.process(ctx)

            return ctx.mixBuffer.left[0]
        }

        val serial = KatalystDsl.of(KatalystStageDsl.Gain(gain = knob), KatalystStageDsl.Gain(gain = knob))
        // One branch is the bus through that branch: the parallel's sum of one.
        val branched = KatalystDsl.of(
            KatalystStageDsl.Parallel(branches = listOf(KatalystDsl.of(KatalystStageDsl.Gain(gain = knob)))),
            KatalystStageDsl.Gain(gain = knob),
        )

        render(serial) shouldBe (g0 * g1 plusOrMinus 1e-15)
        render(branched) shouldBe (g0 * g1 plusOrMinus 1e-15)
        withClue("the same chain built twice sounds the same") {
            render(serial).toRawBits() shouldBe render(serial).toRawBits()
        }
    }

    // ── A modulated knob: resolve reads, tick moves (step 5b) ────────────────────────────────────────────────────

    "a modulated knob moves only by its tick: a resolve evaluates at the clocks as they stand and advances nothing" {
        // The value is the tree at each clock, re-evaluated with the slots on a changed map; a tick runs at the clock
        // and then moves it one block on, so the next resolve or tick reads the next block.
        val knob = testKnob(
            KatalystKnobDsl.Range(
                inner = KatalystKnobDsl.Sine(freq = c(5.0), phase = c(0.125)),
                from = KatalystKnobDsl.Param("lo", 400.0),
                to = c(1600.0),
            ),
            KatalystNoiseOrdinals(),
        )
        // 5 Hz at 48 kHz in 128-frame blocks: 5 * 128 / 48000 of a cycle per block.
        val step = 5.0 * 128.0 / 48000.0

        // The range law by hand, at the sine's phase `p`.
        fun ranged(from: Double, p: Double) = from + (handSine(p) + 1.0) * 0.5 * (1600.0 - from)

        knob.isModulated.shouldBeTrue()
        knob.isModulating.shouldBeTrue()
        knob.value shouldBe (ranged(400.0, 0.125) plusOrMinus 1e-6)
        knob.written.shouldBeFalse()

        repeat(5) { knob.resolve(klangMapOf("other" to it.toDouble())) }

        withClue("five resolves moved no clock") { knob.value shouldBe (ranged(400.0, 0.125) plusOrMinus 1e-6) }

        knob.tick()

        withClue("the first tick reads the clock where it stood") {
            knob.value shouldBe (ranged(400.0, 0.125) plusOrMinus 1e-6)
        }

        knob.resolve(klangMapOf("lo" to 1000.0))

        withClue("the resolve after one tick reads block 1, with the new slot") {
            knob.value shouldBe (ranged(1000.0, 0.125 + step) plusOrMinus 1e-6)
            knob.written.shouldBeTrue()
        }

        knob.tick()
        knob.tick()

        withClue("block 2, not 3: the resolve advanced nothing") {
            knob.value shouldBe (ranged(1000.0, 0.125 + 2.0 * step) plusOrMinus 1e-6)
        }
        withClue("once at build, once per resolve, once per tick") { knob.evaluations shouldBe 10 }

        // A knob without a modulator: a tick does nothing at all.
        val slotOnly = testKnob(
            KatalystKnobDsl.Times(left = KatalystKnobDsl.Param("a", 2.0), right = c(3.0)),
            KatalystNoiseOrdinals(),
        )

        slotOnly.isModulated.shouldBeFalse()
        slotOnly.tick()
        slotOnly.evaluations shouldBe 1
        slotOnly.value shouldBe 6.0
    }
})
