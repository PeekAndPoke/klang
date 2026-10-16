/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

/*
 * The builders: what a door hands to its `configure` lambda, ONE set for both doors
 * (`docs/tasks/kotlin-typed-doors.md`, "Revision (maintainer, 2026-10-16)").
 *
 *     Ign.saw().vibrato(5.ign, 0.3.ign) { it.range(0.ign, 1.ign) }    // Kotlin
 *     Ign.saw().vibrato(5, 0.3, v => v.range(0, 1))                  // KlangScript
 *
 * Each builder is an immutable value wrapper around its node; every knob returns a NEW builder. The knobs here are
 * the TYPED ones, class members, so a Kotlin caller sees only them. The KlangScript door adds `internal`
 * `@KlangScript.Function` extensions in `klangscript-libs`, each with a Kotlin name of its own and the script word in
 * the annotation (`@KlangScript.Function(name = "range") internal fun VibratoBuilder.scriptRange(...)`), that convert
 * their `Any` arguments and call these members by name. A variant never shares the member's Kotlin name, so a renamed
 * or retyped member is a compile error in the variant, never a silent self-call. The user-facing text of each knob
 * lives on that script variant (KSP reads KDoc only from its own module); a member carries a one-line pointer.
 */

/** Applies a door's [configure] lambda to this freshly made builder, or keeps it when there is none. */
internal fun <B> B.configured(configure: ((B) -> B)?): B = configure?.invoke(this) ?: this

// ── Saw ─────────────────────────────────────────────────────────────────

/** Builder for [IgnitorDsl.Saw], handed to the `configure` lambda of `Ign.saw(...)`. Knobs: `analog`, `resetSamples`, `shapeMax`, `phase`. */
data class OscSawBuilder(val node: IgnitorDsl.Saw) {

    /** Where in its cycle the sawtooth runs; the text is on the script knob `phase` (`scriptPhase` in `klangscript-libs`). */
    fun phase(phase: IgnitorDsl): OscSawBuilder = copy(node = node.copy(phase = phase))

    /** How analog the oscillator is; the text is on the script knob `analog` (`scriptAnalog` in `klangscript-libs`). */
    fun analog(character: IgnitorDsl): OscSawBuilder = copy(node = node.copy(analog = character))

    /** Analog flyback time in samples; the text is on the script knob `resetSamples` (`scriptResetSamples` in `klangscript-libs`). */
    fun resetSamples(resetSamples: Double): OscSawBuilder = copy(node = node.copy(resetSamples = resetSamples))

    /** Max flyback fraction of a cycle; the text is on the script knob `shapeMax` (`scriptShapeMax` in `klangscript-libs`). */
    fun shapeMax(shapeMax: Double): OscSawBuilder = copy(node = node.copy(shapeMax = shapeMax))
}

// ── Vibrato ─────────────────────────────────────────────────────────────

/** Builder for [IgnitorDsl.Vibrato], handed to the `configure` lambda of `.vibrato(...)`. Knobs: `range`, `phase`. */
data class VibratoBuilder(val node: IgnitorDsl.Vibrato) {

    /** Where the vibrato's swing sits; the text is on the script knob `range` (`scriptRange` in `klangscript-libs`). */
    fun range(from: IgnitorDsl, to: IgnitorDsl): VibratoBuilder = copy(node = node.copy(rangeFrom = from, rangeTo = to))

    /** Where in its cycle the vibrato's LFO runs; the text is on the script knob `phase` (`scriptPhase` in `klangscript-libs`). */
    fun phase(phase: IgnitorDsl): VibratoBuilder = copy(node = node.copy(phase = phase))
}
