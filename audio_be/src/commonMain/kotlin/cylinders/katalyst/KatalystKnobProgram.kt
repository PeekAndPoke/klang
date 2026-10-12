/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_be.ignitor.absLaw
import io.peekandpoke.klang.audio_be.ignitor.divLaw
import io.peekandpoke.klang.audio_be.ignitor.maxLaw
import io.peekandpoke.klang.audio_be.ignitor.minLaw
import io.peekandpoke.klang.audio_be.ignitor.minusLaw
import io.peekandpoke.klang.audio_be.ignitor.plusLaw
import io.peekandpoke.klang.audio_be.ignitor.powLaw
import io.peekandpoke.klang.audio_be.ignitor.rangeLaw
import io.peekandpoke.klang.audio_be.ignitor.timesLaw
import io.peekandpoke.klang.audio_bridge.KatalystKnobDsl

/** One instruction kind of a [KatalystKnobProgram]: one per arithmetic node of [KatalystKnobDsl]. */
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
}

/**
 * A Katalyst knob EXPRESSION compiled once, when the chain is built, into flat arrays: the evaluator of decision D4 of
 * `docs/tasks/in-progress/katalyst-knob-expressions.md` (step 4).
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
 * once into a new constant register and emits no instruction. A tree with no slot leaf folds whole ([isConstant]).
 *
 * **V8** (`audio/ref/performance.md`): [run] takes no double and returns none, [execute] is Kotlin `inline` and its
 * `when` is a statement whose arms store into the registers (a `when` yielding a double boxes inside a loop), and the
 * registers are a `DoubleArray`, a `Float64Array` on Kotlin/JS, whose elements never box. No lambda, no allocation:
 * evaluating allocates nothing on the JVM, measured (`KatalystSettingsAllocationSpec`); V8 is measured in step 6.
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
) {
    /**
     * True when the whole tree folded at compile: no slot leaf, so no instruction either, and the value is
     * [resultRegister]'s. A bare [KatalystKnobDsl.Param] root has no instruction but a slot leaf, so it is not.
     */
    val isConstant: Boolean get() = slotNames.isEmpty()

    /** How many instructions [run] executes: what is left after the fold. */
    val instructionCount: Int get() = ops.size

    /** Runs every instruction once, in order, over the registers as they stand (the slot registers loaded). */
    fun run() {
        val r = registers

        for (i in ops.indices) {
            execute(op = ops[i], a = inA[i], b = inB[i], c = inC[i], out = out[i], r = r)
        }
    }

    companion object {
        /**
         * Compiles [node] (cold: the chain build). A [KatalystKnobDsl.Param] root is one slot register and no
         * instruction, never [isConstant]; `KatalystKnob` keeps a plain `Param` on its own path anyway.
         */
        fun compile(node: KatalystKnobDsl): KatalystKnobProgram = Compiler().build(node)
    }

    /** The one compile pass: a post-order walk that allocates registers and folds constant subtrees. */
    private class Compiler {
        private val values = ArrayList<Double>()
        private val constant = ArrayList<Boolean>()
        private val ops = ArrayList<KatalystKnobOp>()
        private val inA = ArrayList<Int>()
        private val inB = ArrayList<Int>()
        private val inC = ArrayList<Int>()
        private val out = ArrayList<Int>()
        private val slotNames = ArrayList<String>()
        private val slotDefaults = ArrayList<Double>()
        private val slotRegisters = ArrayList<Int>()

        fun build(node: KatalystKnobDsl): KatalystKnobProgram {
            val result = compile(node)

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
    }
}
