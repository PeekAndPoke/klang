/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

/**
 * The typed Kotlin `Kat`: the KlangScript object `Kat` (`Katalyst`) word for word, for Kotlin
 * (`docs/tasks/kotlin-typed-doors.md`, Q46, rollout S5). Same function words, same parameter names, order and
 * defaults, the same builders and shapes; every value parameter is a [KatalystKnobDsl] (a number enters as `0.5.kat`).
 *
 * ```kotlin
 * Kat { it.reverb(wet = 0.2.kat, size = 4.kat).gain(1.2.kat) }   // script: Kat(k => k.reverb(0.2, 4).gain(1.2))
 * Kat.sine(0.7.kat) { it.phase(0.25.kat) }                         // script: Kat.sine(0.7, x => x.phase(0.25))
 * Kat.perlin(rate = 0.0031.kat, octaves = 8.kat)
 * ```
 *
 * The full text of each door lives once, in the KDoc of the KlangScript door (`KlangScriptKatalyst` and the script
 * knobs in `KatalystBuilders.kt`, module `klangscript-libs`); the builders themselves live here
 * ([KatalystBuilder] and the rest, `KatalystBuilders.kt`).
 */
object Kat {

    override fun toString(): String = "[Kat]"

    /** The Katalyst's open slots, the same objects as the script's `Kat.slot`. */
    val slot: KatalystDsl.Slots = KatalystDsl.Slots

    /**
     * Builds a chain: [configure] receives an empty [KatalystBuilder] and appends stages in written order. No lambda is
     * the empty chain. Script door: `Katalyst.build(configure)`.
     */
    fun build(configure: ((KatalystBuilder) -> KatalystBuilder)? = null): KatalystDsl =
        KatalystBuilder(KatalystDsl(emptyList())).configured(configure).node

    /** `Kat { ... }`, the call form of [build]. Script door: `Katalyst(configure)`. */
    operator fun invoke(configure: ((KatalystBuilder) -> KatalystBuilder)? = null): KatalystDsl = build(configure)

    /** The historical orbit chain with every stage as a slot. Script door: `Katalyst.classic()`. */
    fun classic(): KatalystDsl = KatalystDsl.classic

    /** A named parameter slot with a default value. Script door: `Katalyst.param(name, default, description)`. */
    fun param(name: String, default: Double, description: String = ""): KatalystKnobDsl.Param =
        KatalystKnobDsl.Param(name = name, default = default, description = description)

    // ── Modulators ───────────────────────────────────────────────────────────
    //
    // `freq` is required (a bus has no note to default to); `phase` and the square's `duty` are knobs on the builder.

    /** A sine modulator. Script door: `Katalyst.sine(freq, configure)`, knob `phase`. */
    fun sine(freq: KatalystKnobDsl, configure: ((KatalystSineBuilder) -> KatalystSineBuilder)? = null): KatalystKnobDsl.Sine =
        KatalystSineBuilder(KatalystKnobDsl.Sine(freq = freq)).configured(configure).node

    /** A rising sawtooth modulator. Script door: `Katalyst.saw(freq, configure)`, knob `phase`. */
    fun saw(freq: KatalystKnobDsl, configure: ((KatalystSawBuilder) -> KatalystSawBuilder)? = null): KatalystKnobDsl.Saw =
        KatalystSawBuilder(KatalystKnobDsl.Saw(freq = freq)).configured(configure).node

    /** A falling ramp modulator. Script door: `Katalyst.ramp(freq, configure)`, knob `phase`. */
    fun ramp(freq: KatalystKnobDsl, configure: ((KatalystRampBuilder) -> KatalystRampBuilder)? = null): KatalystKnobDsl.Ramp =
        KatalystRampBuilder(KatalystKnobDsl.Ramp(freq = freq)).configured(configure).node

    /** A triangle modulator. Script door: `Katalyst.tri(freq, configure)`, knob `phase`. */
    fun tri(freq: KatalystKnobDsl, configure: ((KatalystTriBuilder) -> KatalystTriBuilder)? = null): KatalystKnobDsl.Tri =
        KatalystTriBuilder(KatalystKnobDsl.Tri(freq = freq)).configured(configure).node

    /** A square modulator. Script door: `Katalyst.square(freq, configure)`, knobs `duty`, `phase`. */
    fun square(
        freq: KatalystKnobDsl,
        configure: ((KatalystSquareBuilder) -> KatalystSquareBuilder)? = null,
    ): KatalystKnobDsl.Square = KatalystSquareBuilder(KatalystKnobDsl.Square(freq = freq)).configured(configure).node

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
