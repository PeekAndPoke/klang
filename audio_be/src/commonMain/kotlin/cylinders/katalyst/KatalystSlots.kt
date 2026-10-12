/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_bridge.KatalystKnobDsl
import io.peekandpoke.klang.audio_bridge.constants.SLOT_UNSET
import io.peekandpoke.klang.common.infra.KlangMap

/*
 * Reads the knobs of a Katalyst chain: one [KatalystKnobDsl] in, one `Double` out ([KatalystKnob]). The composite
 * values a stage wants instead of a number (the body and vowel `ResonatorConfig`s, the [CompressorSettings], the
 * [DuckSettings]) are filled by that stage's writer (`KatalystSlotWriters.kt`), each the one caller of its rule.
 *
 * Katalyst step 3a (2026-09-17). The per-stage contract is `docs/tasks-archive/2026-09/20260928-katalyst-dsl.md` §7 and
 * the value rule is the signal-flow plan §7 (D4): **a chain's stage knobs come from its slots only**, on every chain
 * since step 5b-1, the one a cylinder is born with included. The owner voice is a knob source only through the map it
 * carries; it has no bus fields since 5b-3.
 *
 * The param state is the owner voice's `katalystParams` map, which `.katp` and the bus doors write: a
 * [KatalystKnobDsl.Param] reads `state[name]` and falls back to its authored default. Only a `Param` moves; a
 * [KatalystKnobDsl.Constant] stays as it was built, and an expression over them moves with its slots
 * ([KatalystKnobProgram]).
 */

/**
 * One knob of a DECLARED stage: its authored value, plus the slot names the orbit's param state may move it with.
 *
 * Built ONCE per knob when the chain is built, in one of three shapes:
 *  - a [KatalystKnobDsl.Constant] is its number, and so is an expression without a slot leaf, folded here through
 *    the laws ([KatalystKnobProgram.isConstant]);
 *  - a [KatalystKnobDsl.Param] is its default and its name;
 *  - an expression with at least one slot leaf (step 4 of `docs/tasks/in-progress/katalyst-knob-expressions.md`,
 *    D4) is a [KatalystKnobProgram], compiled here.
 *
 * From then on [resolve] is one map lookup for a plain slot, one per slot LEAF and one run of the program for an
 * expression, and nothing at all for a constant. That is the cost rule of the param state: one lookup per SLOT per
 * CHANGED map, and zero per block, because the writers read [value] and never the map. An expression over slots
 * only is evaluated at resolve and never per block ([evaluations] counts it).
 *
 * **Resolution happens ONCE, when the chain is built**, and again only when the orbit's param state CHANGES (Katalyst
 * step 5a): every answer here is block-constant, so the writer [KatalystChainBuilder] installs holds the resolved
 * numbers here and re-applies them on every owner claim. That is what keeps a writer allocation-free per block.
 *
 * "Not in the map" and "no map at all" are the same answer, the authored default, so a pattern that
 * writes nothing hears exactly what the chain says.
 *
 * **An expression with a non-finite slot leaf is unset as a whole** (Q5): a leaf that reads a non-finite value (from
 * the state, or its default while nothing writes it) makes [value] [SLOT_UNSET] and [written] false, so the stage
 * takes its constant exactly as for an unset plain slot; nothing is evaluated then. The laws would not keep the NaN
 * (`times` scrubs it to 0), so without the rule `Kat.slot.body.wet.times(2)` would read as a SET 0. A constant is not
 * a slot: a non-finite constant in an expression follows the laws.
 *
 * Never an Ignitor node: the wire type is [KatalystKnobDsl] (decision D1). Until 2026-10-12 a knob was an
 * `IgnitorDsl`, and anything that was neither a constant nor a slot was probed at two note frequencies and folded to
 * one number here; that coercion is gone with the type.
 */
internal class KatalystKnob(node: KatalystKnobDsl) {

    /** The slot name when the knob is a plain [KatalystKnobDsl.Param], null otherwise. */
    private val slot: String?

    /** The compiled expression when the knob is one with a slot leaf, null otherwise. */
    private val program: KatalystKnobProgram?

    /** What the chain itself says: a constant's number (a folded expression's too), a `Param`'s default. */
    private val authored: Double

    /**
     * Test seam: how many times this knob ran its expression, the one at build included. Zero for a plain knob.
     * Declared before `init`, which runs the first evaluation.
     */
    internal var evaluations: Int = 0
        private set

    init {
        when (node) {
            is KatalystKnobDsl.Constant -> {
                slot = null
                program = null
                authored = node.value
            }

            is KatalystKnobDsl.Param -> {
                slot = node.name
                program = null
                authored = node.default
            }

            is KatalystKnobDsl.Plus, is KatalystKnobDsl.Minus, is KatalystKnobDsl.Times, is KatalystKnobDsl.Div,
            is KatalystKnobDsl.Pow, is KatalystKnobDsl.Min, is KatalystKnobDsl.Max, is KatalystKnobDsl.Neg,
            is KatalystKnobDsl.Abs, is KatalystKnobDsl.Range,
            -> {
                val compiled = KatalystKnobProgram.compile(node)

                slot = null

                if (compiled.isConstant) {
                    program = null
                    authored = compiled.registers[compiled.resultRegister]
                } else {
                    program = compiled
                    // The expression at every slot's default, the Q5 rule included: what [resolve] gives with no map.
                    // Read straight off the result register after `run()`, no double returned across a call.
                    if (loadSlots(compiled, null) == LOADED_UNSET) {
                        authored = SLOT_UNSET
                    } else {
                        evaluations++
                        compiled.run()
                        authored = compiled.registers[compiled.resultRegister]
                    }
                }
            }
        }
    }

    /** The number the stage is configured with right now. */
    var value: Double = authored
        private set

    /**
     * True when [value] came OUT of the orbit's param state as a finite number, rather than from
     * what the chain authored. "The pattern wrote this knob", which is the slot twin of the wire's
     * old "the voice TOUCHED this effect" (`VoiceFactory`'s `reverbTouched` / `delayTouched`), and
     * the delay and reverb stages need it to keep that rule (see [KatalystReverbWriter]).
     *
     * FINITE on purpose: a non-finite slot is the wire's "never set" (`/dsl-design` §4), so a
     * cleared knob reads as untouched, exactly as a null field did.
     *
     * For an expression: at least one slot leaf took a finite value from the state AND the expression's value is
     * finite, so `written` always comes with a finite [value], as on a plain slot. An unset expression (Q5) is never
     * written.
     *
     * Always false for a knob that is not a slot: a chain that AUTHORED its wet as a number said
     * what it wanted, and no pattern touched it.
     */
    var written: Boolean = false
        private set

    /** Re-reads the slots from the orbit's param state; null (no owner, no slots) is the defaults. */
    fun resolve(params: KlangMap<Double>?) {
        val compiled = program

        if (compiled != null) {
            resolveExpression(compiled, params)

            return
        }

        val name = slot ?: return
        val fromState = params?.get(name)

        // NaN-guard on a value the author can write: a non-finite slot was never set, so it is not
        // a write either.
        // Two branches and not `fromState ?: authored`: on V8 that merge of the map's tagged value with the double
        // field boxed an absent knob's non-integral default. Measured on the classic chain: about 180 of the about
        // 1,310 bytes a new param map cost, on both bundles, pinned to one core and not (the V8 rule in
        // `audio/ref/performance.md`, V8 allocation pass).
        if (fromState != null) {
            written = fromState.isFinite()
            value = fromState
        } else {
            written = false
            value = authored
        }
    }

    /** The expression path of [resolve]: load every slot leaf, then run, unless a leaf is unset (Q5). */
    private fun resolveExpression(compiled: KatalystKnobProgram, params: KlangMap<Double>?) {
        val loaded = loadSlots(compiled, params)

        if (loaded == LOADED_UNSET) {
            written = false
            value = SLOT_UNSET

            return
        }

        // `run()` takes and returns no double; the value is read off the result register here, so no double crosses
        // a call that V8 may leave out once step 5 evaluates per block (`audio/ref/performance.md`).
        evaluations++
        compiled.run()

        val result = compiled.registers[compiled.resultRegister]

        // NaN-guard: a non-finite result (a non-finite constant through a law that keeps it) is not a write.
        written = loaded == LOADED_WRITTEN && result.isFinite()
        value = result
    }

    private companion object {
        /** [loadSlots]: a slot leaf is non-finite, the whole knob is unset (Q5); the loading stopped there. */
        const val LOADED_UNSET = 0

        /** [loadSlots]: every leaf is finite, none came from the state. */
        const val LOADED_AUTHORED = 1

        /** [loadSlots]: every leaf is finite, and at least one came from the state. */
        const val LOADED_WRITTEN = 2

        /**
         * Loads every slot leaf of [compiled] into its register, from [params] or its default, the plain slot's two
         * branches (no `?:` merge, the V8 rule in [resolve]). One lookup per leaf; it stops at the first non-finite
         * leaf, the Q5 short-circuit. An `Int` result, never a double across the call.
         */
        fun loadSlots(compiled: KatalystKnobProgram, params: KlangMap<Double>?): Int {
            val names = compiled.slotNames
            val defaults = compiled.slotDefaults
            val regs = compiled.slotRegisters
            val r = compiled.registers
            var fromState = false

            for (s in names.indices) {
                val v = params?.get(names[s])

                if (v != null) {
                    r[regs[s]] = v
                    fromState = true
                } else {
                    r[regs[s]] = defaults[s]
                }

                // NaN-guard on a value the author can write: one unset leaf unsets the knob (Q5).
                if (!r[regs[s]].isFinite()) {
                    return LOADED_UNSET
                }
            }

            return if (fromState) LOADED_WRITTEN else LOADED_AUTHORED
        }
    }
}

/**
 * The knob of a field that may be MISSING: the reverb's omitted `lowpass` and an EQ section knob the engine cannot read
 * (`KatalystChainBuilder.eqKnob`), the two nullable knobs of a chain. A missing knob is [SLOT_UNSET], a constant: the
 * stage's own "unset" (the engine's damping, the section helper's fallback). Every other stage knob is non-null on the
 * wire and goes straight to [KatalystKnob].
 */
internal fun knobOrUnset(node: KatalystKnobDsl?): KatalystKnob =
    KatalystKnob(node ?: KatalystKnobDsl.Constant(SLOT_UNSET))
