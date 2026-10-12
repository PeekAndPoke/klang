/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:KlangScript.Library(KlangScriptLibraries.STDLIB)

package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystKnobDsl
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries

/**
 * `Katalyst` for KlangScript: builds [KatalystDsl] chains, for an orbit or for the output.
 *
 * A Katalyst is the instrument an orbit plays through: the effects its summed voices run, in the
 * order written, before the playback's master bus. The master bus runs one too: hand the same kind
 * of chain to `master(...)` and everything the playback plays runs through it last (phase 3 step 12:
 * one chain type, two positions). Author one by CALLING `Katalyst` with a configure lambda and hand
 * it to the `katalyst(...)` or `master(...)` pattern door; the lambda receives a [KatalystBuilder]
 * whose knobs append stages in written order:
 *
 * ```
 * let guitarBus = Katalyst(k => k
 *   .eq(e => e.band(freq = 300, q = 0.8, db = 2.0))
 *   .reverb(0.15, 3)
 *   .compressor(threshold = -21, ratio = 3)
 * )
 * stack(guitar1, guitar2, guitar3).katalyst(guitarBus)
 *
 * master(Katalyst(k => k.reverb(0.05, 9).gain(2.5).limiter()))
 * ```
 *
 * `Katalyst()` with no lambda is the empty chain; `Katalyst(k => ...)` is the same as [build]. The
 * method forms exist so the callable form can be tested against them. `master(Katalyst())` switches
 * a master back off: a master reference means "change to this", so deleting a `master(...)` line
 * while live coding leaves the last chain in place.
 *
 * Sibling of `Ignitor` (the per-voice instrument): same shape, different host.
 *
 * **The chain is the instrument.** A stage the chain does not declare does not run, however loudly
 * a voice asks for it, so `Katalyst(k => k.eq(...))` is honestly "an EQ and nothing else". Start
 * from the familiar orbit with [classic], which brings the historical stages plus the group fader
 * at unity, as named slots the bus doors and `katp` can drive.
 */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.Object("Katalyst")
object KlangScriptKatalyst {
    override fun toString(): String = "[Katalyst object]"

    /**
     * Builds a chain (for an orbit or the output): the lambda receives a [KatalystBuilder] and appends stages in order
     * (`body`, `vowel`, `delay`, `reverb`, `phaser`, `compressor`, `limiter`, `duck`, `distort`, `eq`, `gain`, and
     * `classic` for the whole familiar block at once, at most once per builder). No lambda, or an
     * empty one, is the empty chain.
     *
     * ```
     * katalyst(Katalyst.build(k => k.reverb(0.2).gain(1.4)))
     * ```
     *
     * @param configure receives the [KatalystBuilder] and returns it.
     */
    @KlangScript.Method
    fun build(configure: ((KatalystBuilder) -> KatalystBuilder)? = null): KatalystDsl =
        KatalystBuilder(KatalystDsl(emptyList())).configuredBy("Katalyst", configure).node

    /**
     * The chain every orbit has always run: body, vowel, delay, reverb, phaser, compressor and the
     * duck, in that order, plus the group fader at unity (`gain.gain`) between the compressor and
     * the duck, which is bit-transparent and is there so a pattern can reach a group fader. Every
     * knob is a named slot the bus doors and `katp` write.
     *
     * Write it to say "the familiar orbit", or use it as the base of a chain that adds to it:
     *
     * ```
     * katalyst(Katalyst.classic())
     * katalyst(Katalyst(k => k.classic().eq(e => e.band(freq = 160, q = 1.0, db = -3.0))))
     * katalyst(Katalyst(k => k.classic().duck(orbit = 1, depth = 0.8)))
     * ```
     *
     * Its duck names no source, so nothing ducks until one is written. The third line above is how:
     * a cylinder runs exactly one ducking effect, so the LAST duck in the chain wins and appending
     * one replaces the unset duck `classic` brought.
     *
     * **Writing it is not the same as writing nothing** (decided 2026-09-18). An orbit that declares
     * no chain runs the historical one from the voice's own effect fields; declaring THIS one names
     * the same stages as slots, so `katp` and the bus doors drive them, which is what makes
     * `Katalyst(k => k.classic())` the one line to add before automating a classic knob. The sound
     * is the same either way; what changes is who the knobs listen to. Its `body` material and its
     * `vowel` are slots as well, carrying the INDEX of a name in the shared catalogues, so a
     * pattern's `body(material = "wood")` still reaches it.
     */
    @KlangScript.Method
    fun classic(): KatalystDsl = KatalystDsl.classic

    /**
     * Creates a named **chain slot** with a default value: the knob a pattern can then move with
     * `.katp("<name>", value)`, or with `.katp(slot, value)` when the slot is held in a variable.
     *
     * A slot is read per block from the orbit's owner, the newest note that is still held on it (its gate open; a release tail does not own), so it is orbit state and
     * not a per-note snapshot. When nothing has written the name, the knob is [default]. Write a
     * plain number instead of a slot where the chain should stay fixed.
     *
     * ```
     * let bus = Katalyst(k => k.reverb(0.5, Katalyst.param("room", 5.0)))
     * note("c3 e3 g3").s("supersaw").katalyst(bus).katp("room", "<2 9>")
     * ```
     *
     * It returns a Katalyst knob (a [KatalystKnobDsl.Param], the wire node the chain carries), not a sound. It has the
     * Ignitor's arithmetic words and `range` (step 4 of `docs/tasks-archive/2026-10/20261012-katalyst-knob-expressions.md`):
     * `Katalyst.param("fader", 1).times(0.5)` is a knob too, which the chain evaluates whenever the pattern writes the
     * slot. `katp` writes the param, never an expression over it. An Ignitor node on a chain knob is a script error at
     * the stage call.
     *
     * The twin of `Ignitor.param` on the other host: that one fills the voice's own instrument from
     * `ignp`, this one the orbit's chain from `katp`. The two namespaces never cross, and the two kinds of param do
     * not either: an `Ignitor.param` on a chain knob, or this one handed to `ignp` or placed in an Ignitor tree, is a
     * script error at the call.
     *
     * Typed as the [KatalystKnobDsl] root on this door, as `Ignitor.param` is typed `IgnitorDsl`: the editor names the
     * knob language rather than its leaf, and the Kotlin door's [KatalystDsl.Slots] carry the leaf type.
     *
     * @param name slot name, `<stage>.<knob>` for a classic knob or any word for an authored one
     * @param default the value the knob has while nothing writes the name
     * @param description human-readable description for documentation
     */
    @KlangScript.Method
    fun param(name: String, default: Double, description: String = ""): KatalystKnobDsl =
        KatalystKnobDsl.Param(name = name, default = default, description = description)

    // ── The modulators: a bus LFO, on any knob that takes a Kat.param ─────────────────────────────────────────────
    //
    // Decision D3 of `docs/tasks-archive/2026-10/20261012-katalyst-knob-expressions.md`: the Ignitor's LFO-capable oscillators,
    // their words and their door shape (`freq` on the door, `phase` and the square's `duty` on a builder behind
    // `configure`; the noises flat), with three differences recorded there: `freq` and the noises' `rate` are REQUIRED
    // (Q10: a bus has no note to default to; the Ignitor's noise default of 1 walks 0.384 cells per block, a noise AM
    // rather than an LFO), and there is no `analog` and no flank, reset or shape knob (Q11: their effect lives at
    // sample level, and a modulator here is read once per block). Each returns a Katalyst knob: it takes the knob
    // arithmetic (`range`, `times`, ...) and goes wherever a `Kat.param` goes, except the `eq` sections. On a `body`'s
    // or `vowel`'s `wet` and `floor` it glides the blend in place (step 5c).
    //
    // The chain reads a modulator once per BLOCK, at the block's start, and the stage glides the knob linearly across
    // the block to that value (D2). Its clock starts when the chain is built and runs over the blocks the chain
    // processes (Q3): it pauses while the orbit sleeps and is not reset with the chain. A rate above half the block
    // rate (about 187 Hz at 48 kHz) aliases; nothing clamps it.

    /**
     * A sine LFO for a chain knob: `-1..1`, starting at 0 going up, at [freq] Hz. Map it onto the knob's range with
     * `range(from, to)`.
     *
     * ```KlangScript
     * // the phaser's center sweeps 400 to 1600 Hz every ten seconds
     * Katalyst(k => k.phaser(wet = 0.6, center = Kat.sine(0.1).range(400, 1600)))
     * ```
     *
     * @param freq the rate in Hz, required: a number, a `Kat.param` or a Kat expression (a pattern that wants tempo
     *   sync writes it through a `Kat.param`).
     * @param configure receives the [KatalystSineBuilder] (knob: `phase`) and returns it.
     */
    @KlangScript.Method
    fun sine(freq: IgnitorDslLike, configure: ((KatalystSineBuilder) -> KatalystSineBuilder)? = null): KatalystKnobDsl =
        KatalystSineBuilder(KatalystKnobDsl.Sine(freq = freq.toKatalystKnob()))
            .configuredBy("Katalyst.sine", configure).node

    /**
     * A sawtooth LFO for a chain knob: rises from -1 to 1 over each cycle at [freq] Hz, then falls back over the
     * Ignitor saw's short flyback.
     *
     * ```KlangScript
     * // the fader climbs from 0.2 to 1 once every two seconds
     * Katalyst(k => k.gain(Kat.saw(0.5).range(0.2, 1)))
     * ```
     *
     * @param freq the rate in Hz, required: a number, a `Kat.param` or a Kat expression.
     * @param configure receives the [KatalystSawBuilder] (knob: `phase`) and returns it.
     */
    @KlangScript.Method
    fun saw(freq: IgnitorDslLike, configure: ((KatalystSawBuilder) -> KatalystSawBuilder)? = null): KatalystKnobDsl =
        KatalystSawBuilder(KatalystKnobDsl.Saw(freq = freq.toKatalystKnob()))
            .configuredBy("Katalyst.saw", configure).node

    /**
     * A ramp (falling sawtooth) LFO for a chain knob: falls from 1 to -1 over each cycle at [freq] Hz.
     *
     * ```KlangScript
     * // the room shrinks from size 8 to 2 every four seconds
     * Katalyst(k => k.reverb(0.3, Kat.ramp(0.25).range(2, 8)))
     * ```
     *
     * @param freq the rate in Hz, required: a number, a `Kat.param` or a Kat expression.
     * @param configure receives the [KatalystRampBuilder] (knob: `phase`) and returns it.
     */
    @KlangScript.Method
    fun ramp(freq: IgnitorDslLike, configure: ((KatalystRampBuilder) -> KatalystRampBuilder)? = null): KatalystKnobDsl =
        KatalystRampBuilder(KatalystKnobDsl.Ramp(freq = freq.toKatalystKnob()))
            .configuredBy("Katalyst.ramp", configure).node

    /**
     * A triangle LFO for a chain knob: from -1 up to 1 and back over each cycle at [freq] Hz.
     *
     * ```KlangScript
     * // the delay's feedback swells between 0.2 and 0.6 every eight seconds
     * Katalyst(k => k.delay(0.3, 0.375, Kat.tri(0.125).range(0.2, 0.6)))
     * ```
     *
     * @param freq the rate in Hz, required: a number, a `Kat.param` or a Kat expression.
     * @param configure receives the [KatalystTriBuilder] (knob: `phase`) and returns it.
     */
    @KlangScript.Method
    fun tri(freq: IgnitorDslLike, configure: ((KatalystTriBuilder) -> KatalystTriBuilder)? = null): KatalystKnobDsl =
        KatalystTriBuilder(KatalystKnobDsl.Tri(freq = freq.toKatalystKnob()))
            .configuredBy("Katalyst.tri", configure).node

    /**
     * A square LFO for a chain knob: -1 or 1, switching over the Ignitor pulse's short flanks, at [freq] Hz; its
     * `duty` (on the builder, default 0.5) is the share of each cycle spent high.
     *
     * ```KlangScript
     * // the fader gates between 0.3 and 1 twice a second
     * Katalyst(k => k.gain(Kat.square(2).range(0.3, 1)))
     * ```
     *
     * Read once per block, an edge lands on a block boundary and the stage ramps it across that block (about 2.7 ms
     * at 48 kHz), so a square here is a soft gate, never a click-sharp one.
     *
     * @param freq the rate in Hz, required: a number, a `Kat.param` or a Kat expression.
     * @param configure receives the [KatalystSquareBuilder] (knobs: `duty`, `phase`) and returns it.
     */
    @KlangScript.Method
    fun square(
        freq: IgnitorDslLike,
        configure: ((KatalystSquareBuilder) -> KatalystSquareBuilder)? = null,
    ): KatalystKnobDsl =
        KatalystSquareBuilder(KatalystKnobDsl.Square(freq = freq.toKatalystKnob()))
            .configuredBy("Katalyst.square", configure).node

    /**
     * A Perlin noise walk for a chain knob: smooth, organic drift in about `-1..1`, the Ignitor's `perlin`. Every
     * build of a chain walks the same noise, and two noises in one chain walk different ones (the n-th noise of a
     * chain is seeded by its position).
     *
     * ```KlangScript
     * // the room drifts slowly between size 3 and 7
     * Katalyst(k => k.reverb(0.3, Kat.perlin(0.0005).range(3, 7)))
     * ```
     *
     * @param rate the walk's speed, required: the Ignitor's unit, 0.003 noise cells per sample at rate 1 (about 144
     *   cells a second at 48 kHz, so an LFO-like drift wants a rate around 0.0005 to 0.01).
     * @param octaves fractal-Brownian-motion octaves: 1 = plain (default), higher = more detail (capped at 8).
     * @param persistence fBm amplitude falloff per octave (default 0.5; lower = quieter upper octaves).
     */
    @KlangScript.Method
    fun perlin(
        rate: IgnitorDslLike,
        octaves: IgnitorDslLike = 1.0,
        persistence: IgnitorDslLike = 0.5,
    ): KatalystKnobDsl =
        KatalystKnobDsl.PerlinNoise(
            rate = rate.toKatalystKnob(),
            octaves = octaves.toKatalystKnob(),
            persistence = persistence.toKatalystKnob(),
        )

    /**
     * A Berlin noise walk for a chain knob: piecewise-linear drift in about `-1..1`, the Ignitor's `berlin`, seeded by
     * its position in the chain as [perlin] is.
     *
     * ```KlangScript
     * // the delay's wet wanders between 0.1 and 0.4
     * Katalyst(k => k.delay(Kat.berlin(0.002).range(0.1, 0.4), 0.25, 0.4))
     * ```
     *
     * @param rate the walk's speed, required, in [perlin]'s unit.
     * @param octaves fBm octaves: 1 = plain (default), higher = more detail (capped at 8).
     * @param persistence fBm amplitude falloff per octave (default 0.5).
     */
    @KlangScript.Method
    fun berlin(
        rate: IgnitorDslLike,
        octaves: IgnitorDslLike = 1.0,
        persistence: IgnitorDslLike = 0.5,
    ): KatalystKnobDsl =
        KatalystKnobDsl.BerlinNoise(
            rate = rate.toKatalystKnob(),
            octaves = octaves.toKatalystKnob(),
            persistence = persistence.toKatalystKnob(),
        )

    /**
     * The knobs of the classic chain, one group per stage: `Katalyst.slot.reverb.wet`, `Katalyst.slot.gain.gain` and
     * the rest, each the Katalyst param named `<stage>.<knob>` that `classic()` places. Hand one to `katp` instead of
     * typing its name. `Kat.slot` is the same; the Kotlin door is `KatalystDsl.Slots`, the same objects.
     *
     * ```
     * note("c3 e3 g3").s("supersaw").reverb(wet = 0.4).katp(Katalyst.slot.reverb.size, "<2 8>")
     * ```
     */
    @KlangScript.Property
    val slot: KlangScriptKatalystSlots = KlangScriptKatalystSlots

    /**
     * `Katalyst(k => ...)`: the callable form of [build]. `Katalyst()` is the empty chain.
     *
     * ```
     * katalyst(Katalyst(k => k.reverb(0.2, 4).compressor(threshold = -18)))
     * ```
     *
     * @param configure receives the [KatalystBuilder] and returns it.
     */
    @KlangScript.Invoke
    operator fun invoke(configure: ((KatalystBuilder) -> KatalystBuilder)? = null): KatalystDsl = build(configure)
}

/**
 * The short name of [KlangScriptKatalyst]: the same object under a second name, so `Kat(k => ...)` is
 * `Katalyst(k => ...)` and `Kat.classic()` is `Katalyst.classic()`, for every member: the short form for songs
 * and live coding, while the docs spell `Katalyst` out.
 *
 * ```KlangScript
 * note("c3 e3 g3").s("supersaw").katalyst(Kat(k => k.reverb(0.2, 4).gain(1.2)))
 * ```
 *
 * @category object
 * @tags katalyst, kat, alias, chain
 */
@KlangScript.Constant
val Kat: KlangScriptKatalyst = KlangScriptKatalyst
