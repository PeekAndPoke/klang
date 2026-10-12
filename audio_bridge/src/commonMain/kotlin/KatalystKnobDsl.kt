/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

/**
 * The value of one Katalyst stage knob: the Katalyst's own small knob language, on the wire next to [KatalystStageDsl]
 * (decision D1 of `docs/tasks/katalyst-knob-expressions.md`, maintainer 2026-10-11: "yes separate wire type").
 *
 * The Ignitor and the Katalyst stay two DSLs, on the wire too (`docs/plans/future/signal-graph-engine.md` §6.10): a
 * chain knob is never an [IgnitorDsl] node, and a knob is never an Ignitor tree's leaf. Both doors keep them apart: a
 * Katalyst knob in an Ignitor tree, or an Ignitor node on a Katalyst knob, is a script error at the call, and the
 * Kotlin door's stage constructors take only this type.
 *
 * Two leaves for now, a [Constant] and a [Param] (step 3 of that task); the arithmetic and the modulators join in
 * steps 4 and 5. The one residue of mixing is the `eq` stage, whose sections stay [IgnitorDsl.EqSection] shared with
 * the voice (Q2, E2): its door maps a [Param] onto the section's [IgnitorDsl.Param].
 *
 * The leaves carry the same field names and wire names as their Ignitor twins ([IgnitorDsl.Constant],
 * [IgnitorDsl.Param]); the wire tells the two hierarchies apart by the field's declared type, not by the name.
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
}
