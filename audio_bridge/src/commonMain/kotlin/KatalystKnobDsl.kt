/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

/**
 * The value of one Katalyst stage knob: the Katalyst's own small knob language, on the wire next to [KatalystStageDsl]
 * (decision D1 of `docs/tasks/in-progress/katalyst-knob-expressions.md`, maintainer 2026-10-11: "yes separate wire
 * type").
 *
 * The Ignitor and the Katalyst stay two DSLs, on the wire too (`docs/plans/future/signal-graph-engine.md` §6.10): a
 * chain knob is never an [IgnitorDsl] node, and a knob is never an Ignitor tree's leaf. Both doors keep them apart: a
 * Katalyst knob in an Ignitor tree, or an Ignitor node on a Katalyst knob, is a script error at the call, and the
 * Kotlin door's stage constructors take only this type.
 *
 * Two leaves, a [Constant] and a [Param] (step 3 of that task), and the arithmetic over them (step 4, decision D4:
 * [Plus], [Minus], [Times], [Div], [Pow], [Min], [Max], [Neg], [Abs], [Range]); the modulators join in step 5. The
 * engine evaluates an expression with the Ignitor's own laws, on a scalar, whenever the orbit's `katp` state changes
 * (`KatalystKnob` in `audio_be`). The one residue of mixing is the `eq` stage, whose sections stay
 * [IgnitorDsl.EqSection] shared with the voice (Q2, E2): its door maps a [Param] onto the section's [IgnitorDsl.Param]
 * and refuses an expression.
 *
 * Every node carries the same field names and wire names as its Ignitor twin ([IgnitorDsl.Constant],
 * [IgnitorDsl.Param], [IgnitorDsl.Plus] and the rest); the wire tells the two hierarchies apart by the field's declared
 * type, not by the name. [Min] and [Max] are the MATHEMATICAL minimum and maximum, as on the Ignitor: the `max` door
 * (a cap) builds [Min] and the `min` door (a floor) builds [Max].
 */
@WireFormat
sealed interface KatalystKnobDsl {

    /**
     * A fixed number: what a knob is when the author writes one. It never listens to the orbit's `katp` state.
     *
     * @param value the knob's value, on the knob's own scale (a non-finite one reads as unset in the engine).
     */
    @WireName("const")
    data class Constant(
        val value: Double,
    ) : KatalystKnobDsl

    /**
     * A named slot of the chain: the knob reads the orbit's `katp` state under [name], and [default] while nothing has
     * written it (a non-finite written value is unset too). `Katalyst.param(...)` makes one, `Katalyst.slot.*` and
     * [KatalystDsl.Slots] hold the classic chain's. At the output nothing writes a slot, so there it is its default.
     *
     * @param name slot name, `<stage>.<knob>` for a classic knob or any word for an authored one: what `katp` writes.
     * @param default the value the knob has while nothing writes the name.
     * @param description human-readable description for documentation.
     */
    @WireName("param")
    data class Param(
        val name: String,
        val default: Double,
        val description: String = "",
    ) : KatalystKnobDsl

    // ── The arithmetic (step 4, D4): the Ignitor's laws, on one number ─────────────────────────────────────────────

    /** [left] plus [right]: the Ignitor's `plus` law, unclamped. */
    @WireName("plus")
    data class Plus(val left: KatalystKnobDsl, val right: KatalystKnobDsl) : KatalystKnobDsl

    /** [left] minus [right]: the Ignitor's `minus` law, unclamped. */
    @WireName("minus")
    data class Minus(val left: KatalystKnobDsl, val right: KatalystKnobDsl) : KatalystKnobDsl

    /** [left] times [right]: the Ignitor's `times` law, clamped to `±1e15`, a NaN product reads as 0. */
    @WireName("times")
    data class Times(val left: KatalystKnobDsl, val right: KatalystKnobDsl) : KatalystKnobDsl

    /** [left] divided by [right]: the Ignitor's `div` law; a divisor of exactly zero gives zero. */
    @WireName("div")
    data class Div(val left: KatalystKnobDsl, val right: KatalystKnobDsl) : KatalystKnobDsl

    /** [base] to the power of [exp], keeping the sign of the base: the Ignitor's `pow` law. */
    @WireName("pow")
    data class Pow(val base: KatalystKnobDsl, val exp: KatalystKnobDsl) : KatalystKnobDsl

    /** The smaller of [left] and [right] (mathematical; the `max` door, a cap, builds it). */
    @WireName("min")
    data class Min(val left: KatalystKnobDsl, val right: KatalystKnobDsl) : KatalystKnobDsl

    /** The larger of [left] and [right] (mathematical; the `min` door, a floor, builds it). */
    @WireName("max")
    data class Max(val left: KatalystKnobDsl, val right: KatalystKnobDsl) : KatalystKnobDsl

    /** [inner] with its sign flipped: a multiply by -1, clamped like [Times], as the Ignitor's `neg`. */
    @WireName("neg")
    data class Neg(val inner: KatalystKnobDsl) : KatalystKnobDsl

    /** The absolute value of [inner]. */
    @WireName("abs")
    data class Abs(val inner: KatalystKnobDsl) : KatalystKnobDsl

    /** [inner] mapped from `[-1, 1]` onto `[from, to]`, linearly and unclamped: the Ignitor's `range` law. */
    @WireName("range")
    data class Range(val inner: KatalystKnobDsl, val from: KatalystKnobDsl, val to: KatalystKnobDsl) : KatalystKnobDsl
}
