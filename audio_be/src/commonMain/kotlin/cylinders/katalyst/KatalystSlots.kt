/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_be.ignitor.Ignitor
import io.peekandpoke.klang.audio_be.ignitor.buildExciter
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_bridge.BodyMaterials
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.VowelBands
import io.peekandpoke.klang.audio_bridge.constants.BODY_FLOOR
import io.peekandpoke.klang.audio_bridge.constants.BODY_WET
import io.peekandpoke.klang.audio_bridge.constants.DUCK_ATTACK_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.VOWEL_FLOOR
import io.peekandpoke.klang.audio_bridge.constants.VOWEL_WET

/**
 * Reads the knobs of a Katalyst chain: one [IgnitorDsl] slot node in, one `Double` out, plus the
 * three composite values a stage wants instead of a number (the body and vowel [FilterDef]s, the
 * compressor and duck settings).
 *
 * Katalyst step 3a (2026-09-17). The per-stage contract is `docs/tasks/katalyst-dsl.md` §7 and the
 * value rule is the signal-flow plan §7 (D4): **a chain's stage knobs come from its slots only**,
 * on every chain since step 5b-1, the one a cylinder is born with included. The owner voice is a
 * knob source only through the map it carries ([KatalystKnob]), never through its bus fields.
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
     * all. `Osc.freq()` answers 440 and 660, `Osc.freq().mul(2)` answers 880 and 1320, and both
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
     * time inside the render callback, where an escaping exception takes the worklet with it. A
     * hand-built tree can still throw at build time (an empty `Osc.variants()` does), and the
     * house answer to "the engine cannot read this knob" is the knob's default, not a dead voice.
     *
     * The build itself allocates, which is why this is a chain-build path only.
     */
    private fun coerce(node: IgnitorDsl, fallback: Double): Double {
        // The guard covers the two reads as well as the build: every override today is a pure
        // fold, and the policy on this thread is degrade, never throw.
        val first: Double
        val second: Double

        try {
            val ignitor = node.buildExciter().ignitor

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

    /**
     * The body resonator a declared stage asks for, from its RESOLVED `wet` and `floor`, or null
     * (the stage is off) when [bands] is null, whatever `mix` says.
     *
     * The bands come from `BodyMaterials.modesAt(index)`, which the writer calls directly: step 3c
     * (2026-09-17) moved the table into `audio_bridge` and step 5a-2 (2026-09-18) made the wire
     * carry the INDEX rather than the name, which left nothing for a wrapper here to add. An unset
     * slot (non-finite), an index of 0 (`none`) and an index out of range are the same answer, off,
     * which is the rule `SprudelVoiceData.toVoiceData` follows for an unknown NAME on the voice
     * path; the name-to-index half is `BodyMaterials.indexOf`, and both doors call it.
     *
     * `mix` is the `wet` slot and a non-finite `floor` takes [BODY_FLOOR], which is also what a
     * null floor means to [FilterDef.Body]; the constant is written out so the stage carries one
     * value instead of two spellings of it. A non-finite `mix` takes [BODY_WET] by the same rule:
     * unset is unset on every knob, and the resonator's own `mix` is read straight into the
     * wet/dry law, where a NaN would silence the orbit.
     *
     * These two substitutions are the **NaN rule for a raw `katp` write**, not a second fill. The
     * `body(...)` door fills its own companions when a call names the material (`/dsl-design` §4,
     * checklist 11, Katalyst step 5a-3), so a slot only ever arrives unset here when somebody wrote
     * `katp("body.material", n)` by hand, or when a chain declares the knob and nothing sets it.
     */
    fun bodyDef(bands: List<FilterDef.Body.Mode>?, mix: Double, floor: Double): FilterDef.Body? {
        if (bands == null) {
            return null
        }

        return FilterDef.Body(
            bands = bands,
            // NaN-guards on values the author can write: a non-finite slot is "unset".
            mix = if (mix.isFinite()) mix else BODY_WET,
            floor = if (floor.isFinite()) floor else BODY_FLOOR,
        )
    }

    /**
     * The formant bank a declared stage asks for. Twin of [bodyDef], with the vowel constants and
     * `VowelBands.bandsAt` as the lookup, and the same NaN rule for a raw `katp` write.
     */
    fun vowelDef(bands: List<FilterDef.Formant.Band>?, mix: Double, floor: Double): FilterDef.Formant? {
        if (bands == null) {
            return null
        }

        return FilterDef.Formant(
            bands = bands,
            // NaN-guards on values the author can write: a non-finite slot is "unset".
            mix = if (mix.isFinite()) mix else VOWEL_WET,
            floor = if (floor.isFinite()) floor else VOWEL_FLOOR,
        )
    }

    /**
     * The compressor settings a declared stage asks for, from its five RESOLVED slot values, or
     * null (the stage is off) when none of them is finite.
     *
     * Straight through [Voice.Compressor.fromParams], the voice path's own rule: any of the five
     * set means on, and every unset one takes its `COMPRESSOR_*` constant. A non-finite slot is
     * what "unset" looks like on the wire, so it maps to the `null` that function reads.
     *
     * That substitution is the NaN rule for a raw `katp` write, not a second fill: since Katalyst
     * step 5a-3 the `compressor(...)` door fills the other four itself, whichever of the five the
     * call named, so the values it writes are already the ones this function would have supplied.
     */
    fun compressorSettings(
        threshold: Double,
        ratio: Double,
        knee: Double,
        attack: Double,
        release: Double,
    ): Voice.Compressor? =
        Voice.Compressor.fromParams(
            threshold = finiteOrNull(threshold),
            ratio = finiteOrNull(ratio),
            knee = finiteOrNull(knee),
            attack = finiteOrNull(attack),
            release = finiteOrNull(release),
        )

    /**
     * The duck settings a declared stage asks for, from its three RESOLVED slot values, or null
     * (the stage is off) unless the stage names a source orbit AND asks for depth.
     *
     * `orbit` is a number the runtime coerces to an Int, exactly as the sprudel door does; a
     * finite negative is a request like any other, not an off switch (the off switch is the
     * non-finite default). A non-finite attack takes [DUCK_ATTACK_SECONDS].
     */
    fun duckSettings(orbit: Double, depth: Double, attack: Double): Voice.Ducking? {
        // NaN-guard on values the author can write: a non-finite orbit is "no source named".
        if (!orbit.isFinite() || !(depth > 0.0)) {
            return null
        }

        return Voice.Ducking(
            cylinderId = orbit.toInt(),
            attackSeconds = if (attack.isFinite()) attack else DUCK_ATTACK_SECONDS,
            depth = depth,
        )
    }

    /** A resolved slot as a `Double?`: the number when it is finite, null when it reads as unset. */
    private fun finiteOrNull(value: Double): Double? {
        // NaN-guard on a value the author can write: a non-finite slot was never set.
        return if (value.isFinite()) value else null
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
 * (`Katalyst.param("room", 5).mul(2)`) is neither a constant nor a `Param`, so it goes through
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
     * the two send stages need it to keep that rule (see [KatalystReverbWriter]).
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
