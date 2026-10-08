/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_be.ignitor.Ignitor
import io.peekandpoke.klang.audio_be.ignitor.buildExciter
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import kotlin.random.Random

/**
 * Reads the knobs of a Katalyst chain: one [IgnitorDsl] slot node in, one `Double` out. The
 * composite values a stage wants instead of a number (the body and vowel `ResonatorConfig`s, the
 * [CompressorSettings], the [DuckSettings]) are built by that stage's writer
 * (`KatalystSlotWriters.kt`), each the one caller of its rule.
 *
 * Katalyst step 3a (2026-09-17). The per-stage contract is `docs/tasks-archive/2026-09/20260928-katalyst-dsl.md` §7 and the
 * value rule is the signal-flow plan §7 (D4): **a chain's stage knobs come from its slots only**,
 * on every chain since step 5b-1, the one a cylinder is born with included. The owner voice is a
 * knob source only through the map it carries ([KatalystKnob]); it has no bus fields since 5b-3.
 *
 * **Resolution happens ONCE, when the chain is built**, and again only when the orbit's param
 * state CHANGES (Katalyst step 5a): every answer here is block-constant by contract, so the writer
 * [KatalystChainBuilder] installs holds the resolved numbers in its [KatalystKnob]s and re-applies
 * them on every owner claim. That is what keeps a writer allocation-free per block.
 *
 * The param state is the owner voice's `katalystParams` map, which `.katp` and the bus doors write:
 * a [IgnitorDsl.Param] reads `state[name]` and falls back to its authored default. Only a `Param`
 * moves; a [IgnitorDsl.Constant] and a coerced node stay as they were built ([KatalystKnob]).
 */
internal object KatalystSlots {

    /**
     * The two note frequencies a non-leaf knob is read at (see [coerce]). Any two audible,
     * unequal values do the job; these are an A4 and its octave, so a failure message reads like
     * music rather than like a magic number. Not user-facing, so they live here and not in
     * `audio_bridge/constants/`.
     */
    private const val PROBE_A_HZ: Double = 440.0
    private const val PROBE_B_HZ: Double = 660.0 // not an octave of PROBE_A_HZ, so an octave-invariant use of freq still disagrees

    /**
     * The seed of the random stream a coerced knob's build draws from (see [coerce]). A bus has no
     * voice and so no voice stream; a fixed seed keeps the build off the process-wide `Random`.
     * No answer of [coerce] reads a draw: the nodes that draw at build (noise, the unison stacks,
     * a humanized filter) answer null at control rate, so the knob takes its fallback whatever the
     * seed. Fixed rather than global so a second backend has nothing hidden to reproduce.
     */
    private const val KNOB_BUILD_SEED: Int = 0

    /**
     * The AUTHORED value of one knob: [IgnitorDsl.Constant] is its number, [IgnitorDsl.Param] is
     * its default, a missing node is [fallback], and anything else is coerced. What the orbit's
     * param state then makes of a `Param` is [KatalystKnob]'s job, not this one's.
     *
     * Not an exhaustive `when`, deliberately: a knob is block-constant BY CONTRACT
     * ([KatalystStageDsl]), so the two leaf kinds are the vocabulary and everything else is the
     * pathological case the contract already says to coerce rather than reject. Enumerating the
     * ninety-odd [IgnitorDsl] variants here would claim a meaning for each of them that the bus
     * does not have.
     */
    fun resolve(node: IgnitorDsl?, fallback: Double): Double = when (node) {
        null -> fallback
        is IgnitorDsl.Constant -> node.value
        is IgnitorDsl.Param -> node.default
        else -> coerce(node, fallback)
    }

    /**
     * A knob that is not a slot leaf: read it at control rate if the graph answers the SAME finite
     * number whatever note it is read at, else take the knob's default. Never a throw and never a
     * rejection: the Motor stays raw, and an author who hands an oscillator to a bus knob keeps
     * their sound and loses only the modulation.
     *
     * **Two probes at two frequencies, and the answers must agree** (decided with the maintainer,
     * 2026-09-17): a bus has no note, so a knob whose value DEPENDS on one is not a bus knob at
     * all. `Ignitor.freq()` answers 440 and 660, `Ignitor.freq().mul(2)` answers 880 and 1320, and both
     * therefore take their constant instead of an invented number. A pitch-free fold
     * (`0.1 * 3`, or `1 / 0`, which the engine's `Div` maps to a finite 0.0) answers the same on
     * both probes and is the author's value, zero included: nothing here may second-guess a 0.0,
     * or `phaser(rate = 0)` would stop meaning what it says.
     *
     * A single probe cannot make that distinction, which is what makes this the discriminator and
     * not a NaN guard: the first version handed in one non-finite frequency and was defeated by
     * the engine's own scrubbing (`Times` runs its product through `safeOut`, so NaN came back as
     * a finite 0.0 and read as "delay off").
     *
     * The graph is built ONCE and read twice: [Ignitor.controlRateValueOrNull] is a pure
     * structural read by contract, so the second query cannot advance any state the first one saw.
     *
     * The try/catch is the audio-thread guard, not a diagnostic: [resolve] runs at chain-install
     * time inside the render callback, where an escaping exception takes the worklet with it. Should
     * a build ever throw, the house answer to "the engine cannot read this knob" is the knob's default,
     * not a dead voice.
     *
     * An empty `Ignitor.variants()` no longer throws (since 2026-10-07 it is silence everywhere): it builds
     * `Constant(0)`, both probes agree, and the knob reads 0.0. Until then the build threw and the knob
     * fell back to its default.
     *
     * The build itself allocates, which is why this is a chain-build path only.
     */
    private fun coerce(node: IgnitorDsl, fallback: Double): Double {
        // The guard covers the two reads as well as the build: every override today is a pure
        // fold, and the policy on this thread is degrade, never throw.
        val first: Double
        val second: Double

        try {
            val ignitor = node.buildExciter(random = Random(KNOB_BUILD_SEED)).ignitor

            first = ignitor.controlRateValueOrNull(PROBE_A_HZ) ?: return fallback
            second = ignitor.controlRateValueOrNull(PROBE_B_HZ) ?: return fallback
        } catch (_: Throwable) {
            return fallback
        }

        // NaN-guard on a value the author can write, plus the agreement test: a NaN never equals
        // itself, so the comparison also rejects a graph that answers non-finite on either probe.
        if (!first.isFinite() || first != second) {
            return fallback
        }

        return first
    }
}

/**
 * One knob of a DECLARED stage: its authored value, plus the slot name the orbit's param state may
 * move it with.
 *
 * Built ONCE per knob when the chain is built, which is where the expensive part of
 * [KatalystSlots.resolve] lives: a [IgnitorDsl.Constant] is read, and anything that is neither a
 * constant nor a slot is probed and folded to a number for good. From then on this knob is two
 * fields, and [resolve] is one map lookup for a slot and nothing at all for every other knob kind.
 * That is the cost rule of the param state: one lookup per SLOT per CHANGED map, and zero per
 * block, because the writers read [value] and never the map.
 *
 * "Not in the map" and "no map at all" are the same answer, the authored default, so a pattern that
 * writes nothing hears exactly what the chain says.
 *
 * **Only a knob that IS a slot listens.** An expression OVER a slot
 * (`Ignitor.param("room", 5).mul(2)` on a chain knob through the script door, or the same built in Kotlin;
 * `Katalyst.param(...)` itself has no arithmetic) is neither a constant nor a `Param`, so it goes through
 * [KatalystSlots.resolve]'s coercion once, here, and becomes a number for the life of the chain:
 * `katp("room", x)` never reaches it. A bus knob is block-constant by contract, and folding is what
 * that contract means; the author's way to scale a slot is on the pattern side.
 */
internal class KatalystKnob(node: IgnitorDsl?, fallback: Double) {

    /** The slot name when the knob is a [IgnitorDsl.Param], null for every other knob kind. */
    private val slot: String? = (node as? IgnitorDsl.Param)?.name

    /** What the chain itself says: a `Param`'s default, a constant's number, a coerced fold. */
    private val authored: Double = KatalystSlots.resolve(node, fallback)

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
     * Always false for a knob that is not a slot: a chain that AUTHORED its wet as a number said
     * what it wanted, and no pattern touched it.
     */
    var written: Boolean = false
        private set

    /** Re-reads a slot from the orbit's param state; null (no owner, no slots) is the default. */
    fun resolve(params: Map<String, Double>?) {
        val name = slot ?: return
        val fromState = params?.get(name)

        // NaN-guard on a value the author can write: a non-finite slot was never set, so it is not
        // a write either.
        written = fromState != null && fromState.isFinite()
        value = fromState ?: authored
    }
}
