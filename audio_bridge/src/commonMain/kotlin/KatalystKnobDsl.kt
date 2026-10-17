/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

/**
 * The value of one Katalyst stage knob: the Katalyst's own small knob language, on the wire next to [KatalystStageDsl]
 * (decision D1 of `docs/tasks-archive/2026-10/20261012-katalyst-knob-expressions.md`, maintainer 2026-10-11: "yes separate wire
 * type").
 *
 * The Ignitor and the Katalyst stay two DSLs, on the wire too (`docs/plans/future/signal-graph-engine.md` §6.10): a
 * chain knob is never an [IgnitorDsl] node, and a knob is never an Ignitor tree's leaf. Both doors keep them apart: a
 * Katalyst knob in an Ignitor tree, or an Ignitor node on a Katalyst knob, is a script error at the call, and the
 * Kotlin door's stage constructors take only this type.
 *
 * Two leaves, a [Constant] and a [Param] (step 3 of that task), and the arithmetic over them (step 4, decision D4:
 * [Plus], [Minus], [Times], [Div], [Pow], [Min], [Max], [Neg], [Abs], [Range]), and the modulators (step 5, D3:
 * [Sine], [Saw], [Ramp], [Tri], [Square], [PerlinNoise], [BerlinNoise]). The engine evaluates an expression with the
 * Ignitor's own laws, on a scalar, whenever the orbit's `katp` state changes (`KatalystKnob` in `audio_be`); a
 * modulated one once per block the chain processes (`KatalystKnob.tick`, called by `KatalystChain.process`; step 5b).
 * The doors of the modulators are `Kat.sine` and the rest: the typed [Kat] here, the script `KlangScriptKatalyst` over it. The one residue
 * of mixing is the `eq` stage, whose sections stay [IgnitorDsl.EqSection] shared with the voice (Q2, E2): its door
 * maps a [Param] onto the section's [IgnitorDsl.Param] and refuses an expression.
 *
 * Every node carries the same field names and wire names as its Ignitor twin ([IgnitorDsl.Constant],
 * [IgnitorDsl.Param], [IgnitorDsl.Plus] and the rest); the wire tells the two hierarchies apart by the field's declared
 * type, not by the name. One modulator differs: [Square] has the fields of the node the Ignitor's `square` door
 * builds ([IgnitorDsl.Pulze], minus the Ignitor-only knobs) and the class and wire name of the door word, which is
 * also [IgnitorDsl.Square]'s. [Min] and [Max] are the MATHEMATICAL minimum and maximum, as on the Ignitor: the `max`
 * door (a cap) builds [Min] and the `min` door (a floor) builds [Max].
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

    // ── The modulators (step 5, D3): the Ignitor's LFO shapes, read once per block ─────────────────────────────────
    //
    // A bus LFO owned by the chain: its clock starts when the chain is built and advances over the blocks the chain
    // processes (Q3). Each value is the Ignitor's own law at the same phase (`SineIgnitor`, the wave oscillator's
    // trapezoid, the perlin and berlin fBm walks), sampled at the start of each block, so it is not bit-identical to
    // an Ignitor LFO's per-sample stream: the same law at block resolution. Every operand is a knob of its own, so a
    // rate can be a `Kat.param` or an expression. Recorded asymmetries with the Ignitor (Q11): no `analog`, no flank,
    // reset or shape knobs (their effect lives at sample level), and the three audio-rate noises are not here (read
    // once per block they are a random step, not a modulator).

    /**
     * A sine, `-1` to `1`. Phase 0 is the upward zero crossing, as the Ignitor's `sine`.
     *
     * @param freq frequency in Hz (required, Q10); a negative one runs the phase backwards, raw.
     * @param phase offset as a fraction of one cycle, wrapped (1.25 is 0.25). A constant is the start phase; a moving
     *   one shifts the reading point per block, the Ignitor's phase input at block resolution.
     */
    @WireName("sine")
    data class Sine(
        val freq: KatalystKnobDsl,
        val phase: KatalystKnobDsl = Constant(0.0),
    ) : KatalystKnobDsl

    /**
     * A rising sawtooth, `-1` to `1`, with the Ignitor saw's flyback (its default flank in samples at this [freq]).
     * Phase 0 is `-1`, the bottom of the rise.
     *
     * @param freq frequency in Hz (required, Q10).
     * @param phase offset as a fraction of one cycle, wrapped; see [Sine.phase].
     */
    @WireName("saw")
    data class Saw(
        val freq: KatalystKnobDsl,
        val phase: KatalystKnobDsl = Constant(0.0),
    ) : KatalystKnobDsl

    /**
     * A falling ramp, `1` to `-1`: the negated [Saw], as the Ignitor's `ramp`. Phase 0 is `+1`, the top of the fall.
     *
     * @param freq frequency in Hz (required, Q10).
     * @param phase offset as a fraction of one cycle, wrapped; see [Sine.phase].
     */
    @WireName("ramp")
    data class Ramp(
        val freq: KatalystKnobDsl,
        val phase: KatalystKnobDsl = Constant(0.0),
    ) : KatalystKnobDsl

    /**
     * A triangle, `-1` to `1`: the Ignitor's `tri`, the pulse shape with both flanks fully open. Phase 0 is `-1`, its
     * lowest point.
     *
     * @param freq frequency in Hz (required, Q10).
     * @param phase offset as a fraction of one cycle, wrapped; see [Sine.phase].
     */
    @WireName("tri")
    data class Tri(
        val freq: KatalystKnobDsl,
        val phase: KatalystKnobDsl = Constant(0.0),
    ) : KatalystKnobDsl

    /**
     * A square with a variable [duty], `-1` to `1`: the Ignitor's `square` door, which builds its rounded pulse
     * (`IgnitorDsl.Pulze`) with the default edges (the minimum flank in samples at this [freq], no extra rise or
     * fall). Phase 0 is `-1`, the foot of the rising edge. Named for the door word, `square`; the Ignitor's own
     * `IgnitorDsl.Square` is a fixed-duty node no door builds. Read once per block: a high plateau shorter than a
     * block (`duty / freq` below about 2.67 ms at 48 kHz) is caught by some block starts and missed by others, so a
     * narrow-duty gate drops pulses well below the about 187 Hz alias limit (no clamp).
     *
     * @param freq frequency in Hz (required, Q10).
     * @param duty the share of the cycle spent high, clamped to `0.01..0.99` by the shape (the Ignitor's law).
     * @param phase offset as a fraction of one cycle, wrapped; see [Sine.phase].
     */
    @WireName("square")
    data class Square(
        val freq: KatalystKnobDsl,
        val duty: KatalystKnobDsl = Constant(0.5),
        val phase: KatalystKnobDsl = Constant(0.0),
    ) : KatalystKnobDsl

    /**
     * Perlin noise, about `-1` to `1`: the Ignitor's `perlin` walk, seeded by its position in the chain (Q12: the
     * n-th noise of a chain takes the base seed plus n), so every build of a chain hears the same walks.
     *
     * Read once per block, its `rate` reads in cells per block: rate 1 steps `128 * 0.003 = 0.384` cells a block
     * (about 144 cells a second at 48 kHz), a noise AM rather than an LFO. Useful as a modulator from about 0.001 to
     * 0.05 (about 0.15 to 7 cells a second at 48 kHz); each octave above the first moves twice as fast as the one
     * below, so the top octaves turn into per-block jitter first.
     *
     * @param rate walk speed in the Ignitor's unit: `rate * 0.003` noise units per SAMPLE, so its speed follows the
     *   sample rate (Q12, inherited for parity).
     * @param octaves fBm octaves, truncated to a whole number and held to `1..8`.
     * @param persistence fBm amplitude falloff per octave.
     */
    @WireName("perlin-noise")
    data class PerlinNoise(
        val rate: KatalystKnobDsl = Constant(1.0),
        val octaves: KatalystKnobDsl = Constant(1.0),
        val persistence: KatalystKnobDsl = Constant(0.5),
    ) : KatalystKnobDsl

    /**
     * Berlin noise, `-1` to `1`: the Ignitor's `berlin` walk (piecewise-linear), seeded by its position in the chain
     * (Q12). Its `rate` reads as [PerlinNoise]'s, with the same useful range.
     *
     * @param rate walk speed in the Ignitor's unit, per sample; see [PerlinNoise.rate].
     * @param octaves fBm octaves, truncated to a whole number and held to `1..8`.
     * @param persistence fBm amplitude falloff per octave.
     */
    @WireName("berlin-noise")
    data class BerlinNoise(
        val rate: KatalystKnobDsl = Constant(1.0),
        val octaves: KatalystKnobDsl = Constant(1.0),
        val persistence: KatalystKnobDsl = Constant(0.5),
    ) : KatalystKnobDsl
}

// Numbers into the typed Kotlin door

/**
 * This number as a Katalyst knob: the way a plain number enters the typed Kotlin door, e.g. `0.3.kat` for a knob
 * held at 0.3, or `BodyMaterials.indexOf("wood").kat` for a name knob. The same node the script door builds from a
 * bare `0.3`.
 *
 * Boxing a number into a node at build time is fine (maintainer, 2026-10-16): it happens once, when the chain is
 * written, never in the audio path.
 */
val Double.kat: KatalystKnobDsl.Constant get() = KatalystKnobDsl.Constant(this)

/** [Double.kat] for an [Int]: `2.kat` is `KatalystKnobDsl.Constant(2.0)`, the same tree as `2.0.kat`. */
val Int.kat: KatalystKnobDsl.Constant get() = KatalystKnobDsl.Constant(this.toDouble())

/**
 * The negated number, still a [KatalystKnobDsl.Constant]: `-1.kat` is `Constant(-1.0)`, the same tree as the script's
 * `-1`, not a negation node around a constant.
 *
 * Kotlin reads `-1.kat` as `-(1.kat)`, so this operator is what keeps the tree flat. Being declared on the precise
 * `Constant` type, it wins over any extension operator on `KatalystKnobDsl` in this package; keep the general
 * operators as extensions (a member would win).
 */
operator fun KatalystKnobDsl.Constant.unaryMinus(): KatalystKnobDsl.Constant = KatalystKnobDsl.Constant(-value)

// Knob expressions: the Ignitor's arithmetic on a Katalyst knob
//
// ONE typed function per word, every operand a KatalystKnobDsl: a number comes in through `.kat`
// (`Kat.param("fader", 1.0).times(0.5.kat)`), the typed-doors rollout S5 (`docs/tasks-archive/2026-10/20261016-kotlin-typed-doors.md`). The script
// door keeps taking numbers (`Kat.param("fader", 1).times(0.5)`): its `internal`, renamed variants (`scriptTimes`,
// `klangscript-libs`) convert once and call these. The script's aliases (`add`, `sub`, `mul`, `power`, `negate`) are
// here too, one-line typed delegates to their main word, so a script ports word for word. The text of each word lives on its script variant. Plain functions, as they
// always were, no operators; extensions, so `Constant.unaryMinus` keeps winning.

/** This knob plus [other], the Ignitor's `plus` law (script knob `plus`, alias `add`). */
fun KatalystKnobDsl.plus(other: KatalystKnobDsl): KatalystKnobDsl.Plus = KatalystKnobDsl.Plus(left = this, right = other)

/** This knob minus [other], the Ignitor's `minus` law (script knob `minus`, alias `sub`). */
fun KatalystKnobDsl.minus(other: KatalystKnobDsl): KatalystKnobDsl.Minus = KatalystKnobDsl.Minus(left = this, right = other)

/** This knob times [other], the Ignitor's `times` law, clamped to `±1e15` (script knob `times`, alias `mul`). */
fun KatalystKnobDsl.times(other: KatalystKnobDsl): KatalystKnobDsl.Times = KatalystKnobDsl.Times(left = this, right = other)

/** This knob divided by [other], the Ignitor's `div` law (script knob `div`). */
fun KatalystKnobDsl.div(other: KatalystKnobDsl): KatalystKnobDsl.Div = KatalystKnobDsl.Div(left = this, right = other)

/** This knob to the power of [exp], sign kept, the Ignitor's `pow` law (script knob `pow`, alias `power`). */
fun KatalystKnobDsl.pow(exp: KatalystKnobDsl): KatalystKnobDsl.Pow = KatalystKnobDsl.Pow(base = this, exp = exp)

/**
 * This knob, but at least [other]: a floor is the MAXIMUM of the two, so this builds [KatalystKnobDsl.Max]. Do not
 * "correct" the crossing (guardrail, `CLAUDE.md`) (script knob `min`).
 */
fun KatalystKnobDsl.min(other: KatalystKnobDsl): KatalystKnobDsl.Max = KatalystKnobDsl.Max(left = this, right = other)

/**
 * This knob, but at most [other]: a cap is the MINIMUM of the two, so this builds [KatalystKnobDsl.Min]. Do not
 * "correct" the crossing (guardrail, `CLAUDE.md`) (script knob `max`).
 */
fun KatalystKnobDsl.max(other: KatalystKnobDsl): KatalystKnobDsl.Min = KatalystKnobDsl.Min(left = this, right = other)

/** This knob with its sign flipped, the Ignitor's `neg` (script knob `neg`, alias `negate`). */
fun KatalystKnobDsl.neg(): KatalystKnobDsl.Neg = KatalystKnobDsl.Neg(inner = this)

/** The absolute value of this knob (script knob `abs`). */
fun KatalystKnobDsl.abs(): KatalystKnobDsl.Abs = KatalystKnobDsl.Abs(inner = this)

/** This knob mapped from `-1..1` onto [from]..[to], unclamped, the Ignitor's `range` law (script knob `range`). */
fun KatalystKnobDsl.range(from: KatalystKnobDsl, to: KatalystKnobDsl): KatalystKnobDsl.Range =
    KatalystKnobDsl.Range(inner = this, from = from, to = to)

/** Alias for [plus], the script's `add`. */
fun KatalystKnobDsl.add(other: KatalystKnobDsl): KatalystKnobDsl.Plus = plus(other)

/** Alias for [minus], the script's `sub`. */
fun KatalystKnobDsl.sub(other: KatalystKnobDsl): KatalystKnobDsl.Minus = minus(other)

/** Alias for [times], the script's `mul`. */
fun KatalystKnobDsl.mul(other: KatalystKnobDsl): KatalystKnobDsl.Times = times(other)

/** Alias for [pow], the script's `power`. */
fun KatalystKnobDsl.power(exp: KatalystKnobDsl): KatalystKnobDsl.Pow = pow(exp)

/** Alias for [neg], the script's `negate`. */
fun KatalystKnobDsl.negate(): KatalystKnobDsl.Neg = neg()
