/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_be.ignitor.PERLIN_FBM_MAX_OCTAVES
import io.peekandpoke.klang.audio_be.ignitor.PERLIN_STEP
import io.peekandpoke.klang.audio_be.ignitor.WaveVoiceState
import io.peekandpoke.klang.audio_be.ignitor.absLaw
import io.peekandpoke.klang.audio_be.ignitor.divLaw
import io.peekandpoke.klang.audio_be.ignitor.maxLaw
import io.peekandpoke.klang.audio_be.ignitor.minLaw
import io.peekandpoke.klang.audio_be.ignitor.minusLaw
import io.peekandpoke.klang.audio_be.ignitor.plusLaw
import io.peekandpoke.klang.audio_be.ignitor.powLaw
import io.peekandpoke.klang.audio_be.ignitor.rangeLaw
import io.peekandpoke.klang.audio_be.ignitor.timesLaw
import io.peekandpoke.klang.audio_be.ignitor.waveTrapezoid
import io.peekandpoke.klang.audio_be.utils.TWO_PI
import io.peekandpoke.klang.audio_be.utils.fastSin
import io.peekandpoke.klang.audio_be.utils.wrapPhase
import io.peekandpoke.klang.audio_be.utils.wrapToUnitCycle
import io.peekandpoke.klang.audio_bridge.KatalystKnobDsl
import io.peekandpoke.klang.audio_bridge.constants.PULSE_FALL_FLANK
import io.peekandpoke.klang.audio_bridge.constants.PULSE_MIN_FLANK_SAMPLES
import io.peekandpoke.klang.audio_bridge.constants.PULSE_RISE_FLANK
import io.peekandpoke.klang.audio_bridge.constants.RAMP_RESET_SAMPLES
import io.peekandpoke.klang.audio_bridge.constants.RAMP_SHAPE_MAX
import io.peekandpoke.klang.audio_bridge.constants.SAW_RESET_SAMPLES
import io.peekandpoke.klang.audio_bridge.constants.SAW_SHAPE_MAX
import io.peekandpoke.klang.common.math.BerlinNoise
import io.peekandpoke.klang.common.math.PerlinNoise
import kotlin.math.floor
import kotlin.random.Random

/**
 * One instruction kind of a [KatalystKnobProgram]: one per arithmetic node of [KatalystKnobDsl], and [MODULATOR] for
 * every modulator node (its shape is the program's [KatalystKnobModulator] for that instruction).
 */
internal enum class KatalystKnobOp {
    PLUS,
    MINUS,
    TIMES,
    DIV,
    POW,
    MIN,
    MAX,
    NEG,
    ABS,
    RANGE,
    MODULATOR,
}

/** The shape of one modulator instruction of a [KatalystKnobProgram]: one per modulator node of [KatalystKnobDsl]. */
internal enum class KatalystKnobModulator {
    SINE,
    SAW,
    RAMP,
    TRI,
    SQUARE,
    PERLIN,
    BERLIN,
}

/**
 * The base seed of the perlin and berlin modulators of a Katalyst chain (Q12 of
 * `docs/tasks-archive/2026-10/20261012-katalyst-knob-expressions.md`, fixed per position, maintainer 2026-10-12: "the n-th noise in
 * a chain gets seed + n, so two noise knobs move independently but every build sounds the same"). The noise op of
 * ordinal `n` ([KatalystNoiseOrdinals]) seeds a `Random(KATALYST_KNOB_NOISE_SEED + n)` of its own and builds from it
 * in the order the Ignitor's noise sources build from the stream they are handed: the noise first, then the start
 * position (`PerlinNoiseIgnitor`, `BerlinNoiseIgnitor`). The Ignitor's stream is the voice's, drawn on by everything
 * else in the voice; here each op owns a fresh stream, so only the order of the two draws is the Ignitor's.
 *
 * Per CHAIN, not per orbit: two orbits whose chains hold a noise at the same position walk the same noise (a `seed`
 * knob would be a later door decision).
 */
internal const val KATALYST_KNOB_NOISE_SEED: Int = 0x6B6E6F62

/**
 * The perlin walk's period: its permutation repeats every 256 cells and every fBm octave scales the position by a
 * power of two, so the walk wrapped by 256 is the same walk. A clock subtracted down from `[256, 512)` is exact, and
 * the value at it is the unwrapped value bit for bit (`KatalystKnobModulatorSpec`); a backward walk's wrap adds 256 to
 * a negative position and rounds once, by at most half an ulp of 256. Without the wrap the position grows without end
 * and `PerlinNoise.noise`'s `toInt()` saturates (about 32 hours at rate 1 with 8 octaves, 19 minutes at rate 100).
 */
internal const val PERLIN_CLOCK_PERIOD: Double = 256.0

/**
 * Where the berlin walk's clock wraps: 2^23 cells, so its top octave (2^7 times the clock) stays below 2^31, where
 * `BerlinNoise.noise`'s `toInt()` would saturate and its value leave `[-1, 1]` without bound. The berlin hash has no
 * period, so the wrap is one step in the value: once per 2^23 cells either way, about 16 hours at rate 1 at 48 kHz.
 */
internal const val BERLIN_CLOCK_SPAN: Double = 8388608.0

/**
 * The running count of the noise modulators of ONE chain, in build order: the chain builder hands the same counter to
 * every knob it builds, the stages in the order written, a `parallel`'s branches in theirs, the duck's knobs last; a
 * knob's own noises take their ordinals in compile order (post-order: a noise in another's operand comes first). The
 * ordinal sets the noise's seed ([KATALYST_KNOB_NOISE_SEED]).
 *
 * Within a stage the ordinals follow the order the builder lists that stage's knobs, so reordering a writer's knob
 * arguments reseeds a stage that holds two noises; and a noise inserted in an earlier stage reseeds every noise after
 * it (the rebuild restarts every clock anyway).
 */
internal class KatalystNoiseOrdinals {
    /** The ordinal the next noise op takes. */
    var next: Int = 0
        private set

    /** Takes the next ordinal. */
    fun take(): Int = next++
}

/**
 * A Katalyst knob EXPRESSION compiled once, when the chain is built, into flat arrays: the evaluator of decisions D4
 * (step 4) and D3 (step 5a) of `docs/tasks-archive/2026-10/20261012-katalyst-knob-expressions.md`.
 *
 * **The shape.** Every value of the expression lives in one [registers] array: the constants, one register per slot
 * leaf, and one register per instruction for its result. Instruction `i` runs the law of [ops]`[i]` over the registers
 * [inA], [inB] and [inC] name (a unary op reads [inA] only, [KatalystKnobOp.RANGE] all three) and stores into the
 * register [out] names. [run] executes them in order; the last one stores [resultRegister]. A slot leaf's register is
 * loaded by the caller ([KatalystKnob]) from the orbit's `katp` state before [run], one lookup per slot leaf.
 *
 * **The laws are the Ignitor's own** (`ignitor/_arithmetic_laws.kt`, one law, two hosts): the same function the
 * Ignitor node of that operator calls for its scalar, so the same numbers give the same knob as the Ignitor's
 * control-rate value of the same tree, bit for bit, clamps, NaN and -0.0 included. [KatalystKnobOp.NEG] is
 * `timesLaw(x, -1.0)`, which is what the Ignitor's `neg()` builds (`mul(-1.0)`, a `MulConstIgnitor`), so it clamps
 * like a multiply. Guard: `KatalystKnobExpressionSpec`, against the Ignitor runtime.
 *
 * **Constant subtrees fold at compile**, through the same [execute]: a node whose operands are all constants is run
 * once into a new constant register and emits no instruction. A tree with no slot leaf and no modulator folds whole
 * ([isConstant]).
 *
 * **The modulators** (step 5a, D3; [isModulated]): a [KatalystKnobOp.MODULATOR] instruction is never folded, and
 * neither is anything above it. Its operands are registers like any other (`freq` and `phase`, plus `duty` for the
 * square; `rate`, `octaves` and `persistence` for the noises), so a rate may be a slot or an expression. Each modulator
 * has a CLOCK ([clocks]): the phase in cycles, `[0, 1)`, of a periodic shape, or the walk position of a noise.
 * - [run] samples every modulator at its clock as it stands and never moves it.
 * - [tick] runs, then advances every clock by one block: a periodic shape by `dt * blockFrames` with
 *   `dt = freq / sampleRate` (the Ignitor's own per-sample increment), wrapped with [wrapPhase], whose modulo branch
 *   takes a block that steps two cycles or more; a noise by `rate * PERLIN_STEP * blockFrames`, the Ignitor's
 *   per-sample step times the block, wrapped by [PERLIN_CLOCK_PERIOD] (the same walk) or [BERLIN_CLOCK_SPAN] (one step
 *   per many hours). The advance reads the operands of the run just done, so a slot that moved the `freq` before a
 *   tick sets that tick's advance.
 * - The clock starts at 0 when the program is compiled (a noise at its seeded start: the seed of its position in the
 *   chain, [KATALYST_KNOB_NOISE_SEED] and [KatalystNoiseOrdinals]) and moves only by [tick]: over the blocks the chain
 *   processed (Q3), and nothing rewinds it.
 *
 * Each value is the Ignitor's law at the same phase: `fastSin(2π p)` as `SineIgnitor` reads its accumulator, the
 * [waveTrapezoid] in a [WaveVoiceState] with the Ignitor wave oscillator's default edges, their flank in samples at the
 * modulator's own `dt` (rebaked only when `dt` or the duty changes, as `WaveIgnitor` does), and `PerlinNoise.fbm` /
 * `BerlinNoise.fbm` with the noises' octave cap. The `phase` operand is read as the Ignitor's phase input: the shape
 * at `clock + phase` wrapped, so a constant is the start phase and a moving one shifts the reading point. Sampled
 * ONCE per block at the block's start, so a modulator is not bit-identical to an Ignitor LFO's per-sample stream:
 * the same law at block resolution (its clock adds `dt * blockFrames` once where the Ignitor adds `dt` per sample).
 * A rate above about half the block rate aliases; no clamp (the Motor stays raw). Guard: `KatalystKnobModulatorSpec`,
 * against the Ignitor's oscillators rendered at the same phase.
 *
 * **V8** (`audio/ref/performance.md`): [run] and [tick] take no double and return none, [execute] is Kotlin `inline`
 * and its `when` is a statement whose arms store into the registers (a `when` yielding a double boxes inside a loop),
 * and the registers and the clocks are `DoubleArray`s, `Float64Array`s on Kotlin/JS, whose elements never box. No
 * lambda, no allocation: evaluating allocates nothing on the JVM, measured (`KatalystSettingsAllocationSpec`,
 * `KatalystKnobModulatorAllocationSpec`); on V8 measured in step 6 (`E-kat-*`). Two calls cross a double on a moving
 * path: the noises' `fbm` (once per noise op per tick, its argument and result: three heap numbers per noise per
 * block, open as engine follow-up 39) and the wave shape's setter (only when `dt` or the duty changed; `E-kat-sawfm`,
 * a rebake every block, reads the fader's floor), both as the Ignitor makes them.
 */
internal class KatalystKnobProgram private constructor(
    private val ops: Array<KatalystKnobOp>,
    private val inA: IntArray,
    private val inB: IntArray,
    private val inC: IntArray,
    private val out: IntArray,
    /** Every value of the expression; see the class KDoc. Written by [run] and by the caller's slot loads. */
    val registers: DoubleArray,
    /** The name of each slot leaf, in tree order (left before right); a name written twice is two leaves. */
    val slotNames: Array<String>,
    /** The authored default of each slot leaf, what it reads while nothing writes its name. */
    val slotDefaults: DoubleArray,
    /** The register each slot leaf is loaded into. */
    val slotRegisters: IntArray,
    /** The register that holds the expression's value after [run]. */
    val resultRegister: Int,
    /** Per instruction: the index of its modulator, -1 for an arithmetic one. */
    private val modOf: IntArray,
    /** Per modulator: the instruction that samples it. */
    private val modInstr: IntArray,
    /** Per modulator: its shape. */
    private val modKinds: Array<KatalystKnobModulator>,
    /** Per modulator: its clock, a phase in cycles or a noise position; see the class KDoc. */
    private val clocks: DoubleArray,
    /** Per modulator: its wave shape (read by the saw, ramp, triangle and square; idle on the others). */
    private val waves: Array<WaveVoiceState>,
    /** Per modulator: the `dt` its wave shape was baked for, NaN before the first bake. */
    private val bakedDt: DoubleArray,
    /** Per modulator: the duty its pulse shape was baked for, NaN before the first bake. */
    private val bakedDuty: DoubleArray,
    /** Per modulator: its perlin generator, null unless it is a perlin. */
    private val perlins: Array<PerlinNoise?>,
    /** Per modulator: its berlin generator, null unless it is a berlin. */
    private val berlins: Array<BerlinNoise?>,
    /** The chain's sample rate, for `dt = freq / sampleRate`. */
    private val sampleRate: Double,
    /** The frames of one block, the clocks' step per [tick]. */
    private val blockFrames: Double,
) {
    /**
     * True when the whole tree folded at compile: no slot leaf and no modulator, so no instruction either, and the
     * value is [resultRegister]'s. A bare [KatalystKnobDsl.Param] root has no instruction but a slot leaf, so it is
     * not.
     */
    val isConstant: Boolean get() = slotNames.isEmpty() && modKinds.isEmpty()

    /** True when the tree holds a modulator: its value moves with [tick], not only with its slots. Fixed at compile. */
    val isModulated: Boolean get() = modKinds.isNotEmpty()

    /** How many instructions [run] executes: what is left after the fold. */
    val instructionCount: Int get() = ops.size

    /**
     * Runs every instruction once, in order, over the registers as they stand (the slot registers loaded), each
     * modulator sampled at its clock as it stands. Moves no clock: evaluating twice without a [tick] gives the same.
     */
    fun run() {
        val r = registers

        for (i in ops.indices) {
            val op = ops[i]

            if (op == KatalystKnobOp.MODULATOR) {
                sample(i)
            } else {
                execute(op = op, a = inA[i], b = inB[i], c = inC[i], out = out[i], r = r)
            }
        }
    }

    /**
     * One block of a modulated knob: [run] with the slot registers as they stand, then every modulator's clock one
     * block on. The value of this block is in [resultRegister] afterwards; the next tick reads the next block's.
     * Allocation-free; takes and returns no double.
     */
    fun tick() {
        run()
        advance()
    }

    /** Moves every modulator's clock one block on, by the operands the last [run] left in the registers. */
    private fun advance() {
        val r = registers

        for (m in modKinds.indices) {
            val i = modInstr[m]

            when (modKinds[m]) {
                KatalystKnobModulator.SINE, KatalystKnobModulator.SAW, KatalystKnobModulator.RAMP,
                KatalystKnobModulator.TRI, KatalystKnobModulator.SQUARE,
                -> {
                    val dt = r[inA[i]] / sampleRate

                    // NaN-guard: a non-finite `freq` makes the sum non-finite, and `wrapPhase` reads that as 0, a
                    // restart at the cycle's start: the Ignitor's own accumulator does the same.
                    clocks[m] = (clocks[m] + dt * blockFrames).wrapPhase(1.0)
                }

                KatalystKnobModulator.PERLIN, KatalystKnobModulator.BERLIN -> {
                    val next = clocks[m] + r[inA[i]] * PERLIN_STEP * blockFrames

                    // NaN-guard on a value the author can write: a non-finite `rate` holds the walk where it is (a
                    // finite `x - x` is 0, a non-finite one NaN; no `isFinite` call, a stdlib call on Kotlin/JS).
                    // The Ignitor's walk would keep the NaN for good; the knob's holds until the rate is finite.
                    if (next - next == 0.0) {
                        clocks[m] = wrapNoiseClock(m = m, x = next)
                    }
                }
            }
        }
    }

    /**
     * The noise clock [x] of modulator [m] back inside its span: [PERLIN_CLOCK_PERIOD] for a perlin (the same walk),
     * [BERLIN_CLOCK_SPAN] for a berlin (one step); a clock already inside comes back unchanged.
     */
    @Suppress("NOTHING_TO_INLINE")
    private inline fun wrapNoiseClock(m: Int, x: Double): Double {
        val span = if (modKinds[m] == KatalystKnobModulator.PERLIN) PERLIN_CLOCK_PERIOD else BERLIN_CLOCK_SPAN

        if (x >= span || x < 0.0) {
            return x - span * floor(x / span)
        }

        return x
    }

    /** Samples the modulator of instruction [i] at its clock into its output register. */
    private fun sample(i: Int) {
        val r = registers
        val m = modOf[i]
        // The reading phase of a periodic shape, computed once here rather than inlined into each of its five arms (one
        // `wrapToUnitCycle`, whose `isFinite` is a stdlib call on Kotlin/JS). A noise arm does not read it.
        val p = phaseAt(m = m, offset = r[inB[i]])

        when (modKinds[m]) {
            KatalystKnobModulator.SINE -> r[out[i]] = fastSin(TWO_PI * p)

            KatalystKnobModulator.SAW -> {
                val wave = waves[m]

                bakeSaw(m = m, dt = r[inA[i]] / sampleRate, resetSamples = SAW_RESET_SAMPLES, shapeMax = SAW_SHAPE_MAX)
                r[out[i]] = waveTrapezoid(
                    p = p, riseEnd = wave.riseEnd, highEnd = wave.highEnd,
                    fallEnd = wave.fallEnd, riseSlope = wave.riseSlope, fallSlope = wave.fallSlope,
                )
            }

            KatalystKnobModulator.RAMP -> {
                val wave = waves[m]

                bakeSaw(
                    m = m, dt = r[inA[i]] / sampleRate, resetSamples = RAMP_RESET_SAMPLES, shapeMax = RAMP_SHAPE_MAX,
                )
                // The Ignitor's ramp: the saw's shape at polarity -1.
                r[out[i]] = -1.0 * waveTrapezoid(
                    p = p, riseEnd = wave.riseEnd, highEnd = wave.highEnd,
                    fallEnd = wave.fallEnd, riseSlope = wave.riseSlope, fallSlope = wave.fallSlope,
                )
            }

            KatalystKnobModulator.TRI -> {
                val wave = waves[m]

                // The Ignitor's `tri`: the pulse shape at duty 0.5 with both flanks fully open.
                bakePulse(m = m, dt = r[inA[i]] / sampleRate, duty = 0.5, riseFlank = 1.0, fallFlank = 1.0)
                r[out[i]] = waveTrapezoid(
                    p = p, riseEnd = wave.riseEnd, highEnd = wave.highEnd,
                    fallEnd = wave.fallEnd, riseSlope = wave.riseSlope, fallSlope = wave.fallSlope,
                )
            }

            KatalystKnobModulator.SQUARE -> {
                val wave = waves[m]

                bakePulse(
                    m = m, dt = r[inA[i]] / sampleRate, duty = r[inC[i]],
                    riseFlank = PULSE_RISE_FLANK, fallFlank = PULSE_FALL_FLANK,
                )
                r[out[i]] = waveTrapezoid(
                    p = p, riseEnd = wave.riseEnd, highEnd = wave.highEnd,
                    fallEnd = wave.fallEnd, riseSlope = wave.riseSlope, fallSlope = wave.fallSlope,
                )
            }

            KatalystKnobModulator.PERLIN -> {
                val octaves = r[inB[i]].toInt().coerceIn(1, PERLIN_FBM_MAX_OCTAVES)

                r[out[i]] = perlins[m]!!.fbm(x = clocks[m], octaves = octaves, persistence = r[inC[i]])
            }

            KatalystKnobModulator.BERLIN -> {
                val octaves = r[inB[i]].toInt().coerceIn(1, PERLIN_FBM_MAX_OCTAVES)

                // The Ignitor's berlin: the noise's `0..1` scaled to `-1..1`.
                r[out[i]] = berlins[m]!!.fbm(t = clocks[m], octaves = octaves, persistence = r[inC[i]]) * 2.0 - 1.0
            }
        }
    }

    /**
     * The phase modulator [m] reads at: its clock plus the wrapped [offset], one subtract back into `[0, 1)`, the
     * Ignitor's phased read (`WaveIgnitor.renderPhased`). A non-finite offset reads as 0 (`wrapToUnitCycle`).
     */
    @Suppress("NOTHING_TO_INLINE")
    private inline fun phaseAt(m: Int, offset: Double): Double {
        var p = clocks[m] + offset.wrapToUnitCycle()

        if (p >= 1.0) {
            p -= 1.0
        }

        return p
    }

    /** `WaveIgnitor`'s saw bake: the flyback is [resetSamples] samples at [dt], at most [shapeMax] of the cycle. */
    @Suppress("NOTHING_TO_INLINE")
    private inline fun bakeSaw(m: Int, dt: Double, resetSamples: Double, shapeMax: Double) {
        if (dt != bakedDt[m]) {
            bakedDt[m] = dt

            val rf = (resetSamples * dt).coerceAtMost(shapeMax)

            // NaN-guard: the Ignitor's own; a NaN flyback would make every value NaN.
            waves[m].setSawShape(if (rf != rf) shapeMax else rf)
        }
    }

    /** `WaveIgnitor`'s pulse bake: the [duty], the flanks, and the minimum flank of the pulse in samples at [dt]. */
    @Suppress("NOTHING_TO_INLINE")
    private inline fun bakePulse(m: Int, dt: Double, duty: Double, riseFlank: Double, fallFlank: Double) {
        if (duty != bakedDuty[m] || dt != bakedDt[m]) {
            bakedDuty[m] = duty
            bakedDt[m] = dt
            waves[m].setPulseShape(
                duty = duty, riseFlank = riseFlank, fallFlank = fallFlank, floor = PULSE_MIN_FLANK_SAMPLES * dt,
            )
        }
    }

    companion object {
        /**
         * Compiles [node] (cold: the chain build). A [KatalystKnobDsl.Param] root is one slot register and no
         * instruction, never [isConstant]; `KatalystKnob` keeps a plain `Param` on its own path anyway. [sampleRate]
         * and [blockFrames] are the chain's: they set the modulators' clocks and wave edges, and nothing else reads
         * them.
         */
        fun compile(
            node: KatalystKnobDsl,
            sampleRate: Int,
            blockFrames: Int,
            noises: KatalystNoiseOrdinals,
        ): KatalystKnobProgram =
            Compiler(noises).build(node = node, sampleRate = sampleRate, blockFrames = blockFrames)
    }

    /** The one compile pass: a post-order walk that allocates registers and folds constant subtrees. */
    private class Compiler(private val noises: KatalystNoiseOrdinals) {
        private val values = ArrayList<Double>()
        private val constant = ArrayList<Boolean>()
        private val ops = ArrayList<KatalystKnobOp>()
        private val inA = ArrayList<Int>()
        private val inB = ArrayList<Int>()
        private val inC = ArrayList<Int>()
        private val out = ArrayList<Int>()
        private val modOf = ArrayList<Int>()
        private val slotNames = ArrayList<String>()
        private val slotDefaults = ArrayList<Double>()
        private val slotRegisters = ArrayList<Int>()
        private val modInstr = ArrayList<Int>()
        private val modKinds = ArrayList<KatalystKnobModulator>()
        private val clocks = ArrayList<Double>()
        private val perlins = ArrayList<PerlinNoise?>()
        private val berlins = ArrayList<BerlinNoise?>()

        fun build(node: KatalystKnobDsl, sampleRate: Int, blockFrames: Int): KatalystKnobProgram {
            val result = compile(node)
            val modulators = modKinds.size

            return KatalystKnobProgram(
                ops = ops.toTypedArray(),
                inA = inA.toIntArray(),
                inB = inB.toIntArray(),
                inC = inC.toIntArray(),
                out = out.toIntArray(),
                registers = values.toDoubleArray(),
                slotNames = slotNames.toTypedArray(),
                slotDefaults = slotDefaults.toDoubleArray(),
                slotRegisters = slotRegisters.toIntArray(),
                resultRegister = result,
                modOf = modOf.toIntArray(),
                modInstr = modInstr.toIntArray(),
                modKinds = modKinds.toTypedArray(),
                clocks = clocks.toDoubleArray(),
                waves = Array(modulators) { WaveVoiceState() },
                bakedDt = DoubleArray(modulators) { Double.NaN },
                bakedDuty = DoubleArray(modulators) { Double.NaN },
                perlins = perlins.toTypedArray(),
                berlins = berlins.toTypedArray(),
                sampleRate = sampleRate.toDouble(),
                blockFrames = blockFrames.toDouble(),
            )
        }

        private fun register(value: Double, isConstant: Boolean): Int {
            values.add(value)
            constant.add(isConstant)

            return values.size - 1
        }

        /** Returns the register holding [node]'s value. */
        private fun compile(node: KatalystKnobDsl): Int = when (node) {
            is KatalystKnobDsl.Constant -> register(value = node.value, isConstant = true)

            is KatalystKnobDsl.Param -> {
                val reg = register(value = node.default, isConstant = false)

                slotNames.add(node.name)
                slotDefaults.add(node.default)
                slotRegisters.add(reg)

                reg
            }

            // Operands in the node's own order, so the laws see them as the Ignitor's node does (`minLaw` and
            // `maxLaw` are not value-commutative under NaN).
            is KatalystKnobDsl.Plus -> emit(op = KatalystKnobOp.PLUS, a = compile(node.left), b = compile(node.right))
            is KatalystKnobDsl.Minus -> emit(op = KatalystKnobOp.MINUS, a = compile(node.left), b = compile(node.right))
            is KatalystKnobDsl.Times -> emit(op = KatalystKnobOp.TIMES, a = compile(node.left), b = compile(node.right))
            is KatalystKnobDsl.Div -> emit(op = KatalystKnobOp.DIV, a = compile(node.left), b = compile(node.right))
            is KatalystKnobDsl.Pow -> emit(op = KatalystKnobOp.POW, a = compile(node.base), b = compile(node.exp))
            is KatalystKnobDsl.Min -> emit(op = KatalystKnobOp.MIN, a = compile(node.left), b = compile(node.right))
            is KatalystKnobDsl.Max -> emit(op = KatalystKnobOp.MAX, a = compile(node.left), b = compile(node.right))
            is KatalystKnobDsl.Neg -> compile(node.inner).let { emit(op = KatalystKnobOp.NEG, a = it, b = it) }
            is KatalystKnobDsl.Abs -> compile(node.inner).let { emit(op = KatalystKnobOp.ABS, a = it, b = it) }

            is KatalystKnobDsl.Range -> {
                val v = compile(node.inner)
                val from = compile(node.from)
                val to = compile(node.to)

                emit(op = KatalystKnobOp.RANGE, a = v, b = from, c = to)
            }

            // The modulators: operands compiled in field order (the slot leaves' tree order), stored as freq or rate
            // in A, then phase or octaves in B, then duty or persistence in C.
            is KatalystKnobDsl.Sine -> periodic(KatalystKnobModulator.SINE, freq = node.freq, phase = node.phase)
            is KatalystKnobDsl.Saw -> periodic(KatalystKnobModulator.SAW, freq = node.freq, phase = node.phase)
            is KatalystKnobDsl.Ramp -> periodic(KatalystKnobModulator.RAMP, freq = node.freq, phase = node.phase)
            is KatalystKnobDsl.Tri -> periodic(KatalystKnobModulator.TRI, freq = node.freq, phase = node.phase)

            is KatalystKnobDsl.Square -> {
                val freq = compile(node.freq)
                val duty = compile(node.duty)
                val phase = compile(node.phase)

                modulator(kind = KatalystKnobModulator.SQUARE, a = freq, b = phase, c = duty, clock = 0.0)
            }

            is KatalystKnobDsl.PerlinNoise -> {
                val rate = compile(node.rate)
                val octaves = compile(node.octaves)
                val persistence = compile(node.persistence)
                // The Ignitor's construction order (`PerlinNoiseIgnitor`): the noise, then the start from the same rng.
                val rng = Random(KATALYST_KNOB_NOISE_SEED + noises.take())
                val noise = PerlinNoise(rng)

                modulator(
                    kind = KatalystKnobModulator.PERLIN, a = rate, b = octaves, c = persistence,
                    clock = rng.nextDouble() * 256.0, perlin = noise,
                )
            }

            is KatalystKnobDsl.BerlinNoise -> {
                val rate = compile(node.rate)
                val octaves = compile(node.octaves)
                val persistence = compile(node.persistence)
                // The Ignitor's construction order (`BerlinNoiseIgnitor`): the noise, then the start from the same rng.
                val rng = Random(KATALYST_KNOB_NOISE_SEED + noises.take())
                val noise = BerlinNoise(rng)

                modulator(
                    kind = KatalystKnobModulator.BERLIN, a = rate, b = octaves, c = persistence,
                    clock = rng.nextDouble() * 256.0, berlin = noise,
                )
            }
        }

        /**
         * One instruction over registers [a], [b] and [c] (a binary op passes [c] = [b], a unary one [b] = [a]: read by
         * no law, they only keep every index valid). When every operand is a constant, the instruction runs NOW,
         * through the same [execute] the evaluator runs, and its value becomes a constant register: the fold.
         */
        private fun emit(op: KatalystKnobOp, a: Int, b: Int, c: Int = b): Int {
            if (constant[a] && constant[b] && constant[c]) {
                val scratch = doubleArrayOf(values[a], values[b], values[c], 0.0)

                execute(op = op, a = 0, b = 1, c = 2, out = 3, r = scratch)

                return register(value = scratch[3], isConstant = true)
            }

            val reg = register(value = 0.0, isConstant = false)

            ops.add(op)
            inA.add(a)
            inB.add(b)
            inC.add(c)
            out.add(reg)
            modOf.add(-1)

            return reg
        }

        /** A sine, saw, ramp or triangle: `freq` in A, `phase` in B (and in C, read by none). */
        private fun periodic(kind: KatalystKnobModulator, freq: KatalystKnobDsl, phase: KatalystKnobDsl): Int {
            val f = compile(freq)
            val p = compile(phase)

            return modulator(kind = kind, a = f, b = p, c = p, clock = 0.0)
        }

        /**
         * One modulator instruction: never folded, whatever its operands, so its register is never a constant and
         * neither is anything above it. [clock] is its start; [perlin] or [berlin] its generator when it is a noise.
         */
        private fun modulator(
            kind: KatalystKnobModulator,
            a: Int,
            b: Int,
            c: Int,
            clock: Double,
            perlin: PerlinNoise? = null,
            berlin: BerlinNoise? = null,
        ): Int {
            val reg = register(value = 0.0, isConstant = false)

            modOf.add(modKinds.size)
            modInstr.add(ops.size)
            modKinds.add(kind)
            clocks.add(clock)
            perlins.add(perlin)
            berlins.add(berlin)

            ops.add(KatalystKnobOp.MODULATOR)
            inA.add(a)
            inB.add(b)
            inC.add(c)
            out.add(reg)

            return reg
        }
    }
}

/**
 * One instruction: the law of [op] over the registers [a], [b] and [c] of [r], stored into register [out]. The one
 * home of the op-to-law table, for the evaluator and for the fold alike.
 *
 * A statement `when` whose arms store into the array, never an expression yielding a double: on V8 the expression form
 * boxes its result inside a loop (`audio/ref/performance.md`, the exhaustive `when` in a sample loop). Exhaustive over
 * the enum, so a new op cannot be forgotten here.
 */
@Suppress("NOTHING_TO_INLINE")
private inline fun execute(op: KatalystKnobOp, a: Int, b: Int, c: Int, out: Int, r: DoubleArray) {
    when (op) {
        KatalystKnobOp.PLUS -> r[out] = plusLaw(a = r[a], b = r[b])
        KatalystKnobOp.MINUS -> r[out] = minusLaw(a = r[a], b = r[b])
        KatalystKnobOp.TIMES -> r[out] = timesLaw(a = r[a], b = r[b])
        KatalystKnobOp.DIV -> r[out] = divLaw(a = r[a], b = r[b])
        KatalystKnobOp.POW -> r[out] = powLaw(a = r[a], b = r[b])
        KatalystKnobOp.MIN -> r[out] = minLaw(a = r[a], b = r[b])
        KatalystKnobOp.MAX -> r[out] = maxLaw(a = r[a], b = r[b])
        // The Ignitor's `neg()` is `mul(-1.0)`: the Times law against -1, clamped like a multiply.
        KatalystKnobOp.NEG -> r[out] = timesLaw(a = r[a], b = -1.0)
        KatalystKnobOp.ABS -> r[out] = absLaw(r[a])
        KatalystKnobOp.RANGE -> r[out] = rangeLaw(v = r[a], from = r[b], to = r[c])
        // Never reached: `KatalystKnobProgram.run` samples a modulator itself (it needs the program's clocks), and the
        // fold never meets one (a modulator's register is never a constant). Present for exhaustiveness only.
        KatalystKnobOp.MODULATOR -> Unit
    }
}
