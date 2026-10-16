/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

/**
 * The typed Kotlin `Kat`: the KlangScript object `Kat` (`Katalyst`) word for word, for Kotlin
 * (`docs/tasks/kotlin-typed-doors.md`, Q46). Same function words, same parameter names, order and defaults; every value
 * parameter is a [KatalystKnobDsl] (a number enters as `0.5.kat`). Pending (the typed-door rollout, after spike S1):
 * where the script door takes a `configure` lambda this door still takes the builder's knobs as FLAT named parameters;
 * they move onto the same builders and shapes as the script (`Kat.sine(0.7.kat) { it.phase(0.25.kat) }`).
 *
 * ```kotlin
 * Kat.sine(freq = 0.7.kat, phase = 0.25.kat)   // script: Kat.sine(0.7, x => x.phase(0.25))
 * Kat.perlin(rate = 0.0031.kat, octaves = 8.kat)
 * ```
 *
 * The full text of each door lives once, in the KDoc of the KlangScript door (`KlangScriptKatalyst` and its builders in
 * `KatalystBuilders.kt`, module `klangscript-libs`). The chain door (`Kat.build(configure)`, `Kat(configure)`) is not
 * here yet: it is step 4 of the task.
 */
object Kat {

    override fun toString(): String = "[Kat]"

    /** The Katalyst's open slots, the same objects as the script's `Kat.slot`. */
    val slot: KatalystDsl.Slots = KatalystDsl.Slots

    /** The historical orbit chain with every stage as a slot. Script door: `Katalyst.classic()`. */
    fun classic(): KatalystDsl = KatalystDsl.classic

    /** A named parameter slot with a default value. Script door: `Katalyst.param(name, default, description)`. */
    fun param(name: String, default: Double, description: String = ""): KatalystKnobDsl.Param =
        KatalystKnobDsl.Param(name = name, default = default, description = description)

    // ── Modulators ───────────────────────────────────────────────────────────

    /** A sine modulator. Script door: `Katalyst.sine(freq, configure)`, knob `phase`. */
    fun sine(freq: KatalystKnobDsl, phase: KatalystKnobDsl = 0.0.kat): KatalystKnobDsl.Sine =
        KatalystKnobDsl.Sine(freq = freq, phase = phase)

    /** A rising sawtooth modulator. Script door: `Katalyst.saw(freq, configure)`, knob `phase`. */
    fun saw(freq: KatalystKnobDsl, phase: KatalystKnobDsl = 0.0.kat): KatalystKnobDsl.Saw =
        KatalystKnobDsl.Saw(freq = freq, phase = phase)

    /** A falling ramp modulator. Script door: `Katalyst.ramp(freq, configure)`, knob `phase`. */
    fun ramp(freq: KatalystKnobDsl, phase: KatalystKnobDsl = 0.0.kat): KatalystKnobDsl.Ramp =
        KatalystKnobDsl.Ramp(freq = freq, phase = phase)

    /** A triangle modulator. Script door: `Katalyst.tri(freq, configure)`, knob `phase`. */
    fun tri(freq: KatalystKnobDsl, phase: KatalystKnobDsl = 0.0.kat): KatalystKnobDsl.Tri =
        KatalystKnobDsl.Tri(freq = freq, phase = phase)

    /** A square modulator. Script door: `Katalyst.square(freq, configure)`, knobs `duty`, `phase`. */
    fun square(
        freq: KatalystKnobDsl,
        duty: KatalystKnobDsl = 0.5.kat,
        phase: KatalystKnobDsl = 0.0.kat,
    ): KatalystKnobDsl.Square = KatalystKnobDsl.Square(freq = freq, duty = duty, phase = phase)

    /** A Perlin noise modulator. Script door: `Katalyst.perlin(rate, octaves, persistence)`. */
    fun perlin(
        rate: KatalystKnobDsl,
        octaves: KatalystKnobDsl = 1.0.kat,
        persistence: KatalystKnobDsl = 0.5.kat,
    ): KatalystKnobDsl.PerlinNoise = KatalystKnobDsl.PerlinNoise(rate = rate, octaves = octaves, persistence = persistence)

    /** A Berlin noise modulator. Script door: `Katalyst.berlin(rate, octaves, persistence)`. */
    fun berlin(
        rate: KatalystKnobDsl,
        octaves: KatalystKnobDsl = 1.0.kat,
        persistence: KatalystKnobDsl = 0.5.kat,
    ): KatalystKnobDsl.BerlinNoise = KatalystKnobDsl.BerlinNoise(rate = rate, octaves = octaves, persistence = persistence)
}
