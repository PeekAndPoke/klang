/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

/**
 * The typed Kotlin `Ign`: the KlangScript object `Ign` (`Ignitor`) word for word, for Kotlin (`docs/tasks-archive/2026-10/20261016-kotlin-typed-doors.md`,
 * Q46 and the maintainer's revision of 2026-10-16). Same function words, parameter names, order and defaults, the SAME
 * builders and shapes; only the accepted types differ: every value parameter is an [IgnitorDsl] (a number enters as
 * `0.5.ign`), and a `configure` lambda takes the same builder with typed knobs.
 *
 * ```kotlin
 * Ign.saw(110.ign) { it.analog(5.ign) }.vibrato(5.ign, 0.3.ign) { it.range(0.ign, 1.ign) }
 * // script: Ign.saw(110, x => x.analog(5)).vibrato(5, 0.3, v => v.range(0, 1))
 * ```
 *
 * The full text of each door (what it sounds like, the ranges) lives once, in the KDoc of the KlangScript door
 * (`KlangScriptIgnitor` and its builder knobs, module `klangscript-libs`). Build-time knobs that the script door takes
 * as plain numbers (`resetSamples`, `shapeMax`, the flanks, the unison character knobs, `phasePool`'s arguments) stay
 * plain `Double` here (Q51; no `Int` overloads, write `4.0`).
 *
 * Every oscillator door is on its builder (typed-door rollout S1 and S2): no flat doors are pending among the
 * oscillators. The noise sources take their few values as plain parameters, the script's own shape.
 */
object Ign {

    override fun toString(): String = "[Ign]"

    /** The canonical open slots of a custom sound, the same objects as the script's `Ign.slot`. */
    val slot: IgnitorDsl.Slots = IgnitorDsl.Slots

    // ── Oscillator Primitives ────────────────────────────────────────────────

    /** The voice's note frequency. Script door: `Ignitor.freq()`. */
    fun freq(): IgnitorDsl = IgnitorDsl.Freq

    /**
     * A sine oscillator, with the partial banks and the repeatable `partial` on its builder. Script door:
     * `Ignitor.sine(freq, configure)`, the same [OscSineBuilder] (knobs `analog`, `fundamental`, `harmonics`,
     * `octaves`, `suboctaves`, `partial`, `analogSpread`, `phase`).
     */
    fun sine(
        freq: IgnitorDsl = IgnitorDsl.Freq,
        configure: ((OscSineBuilder) -> OscSineBuilder)? = null,
    ): IgnitorDsl.Sine = OscSineBuilder(IgnitorDsl.Sine(freq = freq)).configured(configure).node

    /**
     * A sawtooth oscillator. Script door: `Ignitor.saw(freq, configure)`; the same [OscSawBuilder] (knobs `analog`,
     * `resetSamples`, `shapeMax`, `phase`) and the same shape: `Ign.saw(110.ign) { it.analog(5.ign) }`.
     */
    fun saw(
        freq: IgnitorDsl = IgnitorDsl.Freq,
        configure: ((OscSawBuilder) -> OscSawBuilder)? = null,
    ): IgnitorDsl.Saw = OscSawBuilder(IgnitorDsl.Saw(freq = freq)).configured(configure).node

    /**
     * A square oscillator, the flank-shaped pulse. Script door: `Ignitor.square(freq, configure)`, the same
     * [OscSquareBuilder] (knobs `duty`, `analog`, `flankSamples`, `riseFlank`, `fallFlank`, `phase`).
     */
    fun square(
        freq: IgnitorDsl = IgnitorDsl.Freq,
        configure: ((OscSquareBuilder) -> OscSquareBuilder)? = null,
    ): IgnitorDsl.Pulze = OscSquareBuilder(IgnitorDsl.Pulze(freq = freq)).configured(configure).node

    /**
     * A triangle oscillator. Script door: `Ignitor.tri(freq, configure)`, the same [OscTriBuilder] (knobs `analog`,
     * `phase`).
     */
    fun tri(
        freq: IgnitorDsl = IgnitorDsl.Freq,
        configure: ((OscTriBuilder) -> OscTriBuilder)? = null,
    ): IgnitorDsl.Tri = OscTriBuilder(IgnitorDsl.Tri(freq = freq)).configured(configure).node

    /**
     * A ramp (reverse sawtooth) oscillator. Script door: `Ignitor.ramp(freq, configure)`, the same [OscRampBuilder]
     * (knobs `analog`, `resetSamples`, `shapeMax`, `phase`).
     */
    fun ramp(
        freq: IgnitorDsl = IgnitorDsl.Freq,
        configure: ((OscRampBuilder) -> OscRampBuilder)? = null,
    ): IgnitorDsl.Ramp = OscRampBuilder(IgnitorDsl.Ramp(freq = freq)).configured(configure).node

    /**
     * A naive sawtooth without anti-aliasing. Script door: `Ignitor.zawtooth(freq, configure)`, the same
     * [OscZawtoothBuilder] (knobs `analog`, `phase`).
     */
    fun zawtooth(
        freq: IgnitorDsl = IgnitorDsl.Freq,
        configure: ((OscZawtoothBuilder) -> OscZawtoothBuilder)? = null,
    ): IgnitorDsl.Zawtooth = OscZawtoothBuilder(IgnitorDsl.Zawtooth(freq = freq)).configured(configure).node

    /**
     * A raw ramp. Script door: `Ignitor.zamp(freq, configure)`, the same [OscZampBuilder] (knobs `analog`, `phase`).
     */
    fun zamp(
        freq: IgnitorDsl = IgnitorDsl.Freq,
        configure: ((OscZampBuilder) -> OscZampBuilder)? = null,
    ): IgnitorDsl.Zamp = OscZampBuilder(IgnitorDsl.Zamp(freq = freq)).configured(configure).node

    /**
     * An impulse (click) oscillator. Script door: `Ignitor.impulse(freq, configure)`, the same [OscImpulseBuilder]
     * (knobs `analog`, `phase`).
     */
    fun impulse(
        freq: IgnitorDsl = IgnitorDsl.Freq,
        configure: ((OscImpulseBuilder) -> OscImpulseBuilder)? = null,
    ): IgnitorDsl.Impulse = OscImpulseBuilder(IgnitorDsl.Impulse(freq = freq)).configured(configure).node

    /**
     * A raw, aliased pulse. Script door: `Ignitor.pulze(freq, configure)`, the same [OscPulzeBuilder] (knobs `duty`,
     * `analog`, `phase`).
     */
    fun pulze(
        freq: IgnitorDsl = IgnitorDsl.Freq,
        configure: ((OscPulzeBuilder) -> OscPulzeBuilder)? = null,
    ): IgnitorDsl.RawPulze = OscPulzeBuilder(IgnitorDsl.RawPulze(freq = freq)).configured(configure).node

    /** A silent ignitor. Script door: `Ignitor.silence()`. */
    fun silence(): IgnitorDsl = IgnitorDsl.Silence

    // ── Noise Sources ────────────────────────────────────────────────────────

    /** A white noise source, a fresh instance per call. Script door: `Ignitor.whitenoise(color)`. */
    fun whitenoise(color: IgnitorDsl = 0.0.ign): IgnitorDsl.WhiteNoise = IgnitorDsl.WhiteNoise(color = color)

    /** A brown noise source, a fresh instance per call. Script door: `Ignitor.brownnoise(leak)`. */
    fun brownnoise(leak: IgnitorDsl = 0.02.ign): IgnitorDsl.BrownNoise = IgnitorDsl.BrownNoise(leak = leak)

    /** A pink noise source, a fresh instance per call. Script door: `Ignitor.pinknoise()`. */
    fun pinknoise(): IgnitorDsl.PinkNoise = IgnitorDsl.PinkNoise()

    /** A Perlin noise source. Script door: `Ignitor.perlin(rate, octaves, persistence)`. */
    fun perlin(
        rate: IgnitorDsl = 1.0.ign,
        octaves: IgnitorDsl = 1.0.ign,
        persistence: IgnitorDsl = 0.5.ign,
    ): IgnitorDsl.PerlinNoise = IgnitorDsl.PerlinNoise(rate = rate, octaves = octaves, persistence = persistence)

    /** A Berlin noise source. Script door: `Ignitor.berlin(rate, octaves, persistence)`. */
    fun berlin(
        rate: IgnitorDsl = 1.0.ign,
        octaves: IgnitorDsl = 1.0.ign,
        persistence: IgnitorDsl = 0.5.ign,
    ): IgnitorDsl.BerlinNoise = IgnitorDsl.BerlinNoise(rate = rate, octaves = octaves, persistence = persistence)

    /** A dust source (sparse random impulses). Script door: `Ignitor.dust(density, tail, bipolar)`. */
    fun dust(
        density: IgnitorDsl = 0.2.ign,
        tail: IgnitorDsl = 1.0.ign,
        bipolar: IgnitorDsl = 0.0.ign,
    ): IgnitorDsl.Dust = IgnitorDsl.Dust(density = density, tail = tail, bipolar = bipolar)

    /** A crackle source. Script door: `Ignitor.crackle(chaos)`. */
    fun crackle(chaos: IgnitorDsl = 1.5.ign): IgnitorDsl.Crackle = IgnitorDsl.Crackle(chaos = chaos)

    // ── Super Oscillators ────────────────────────────────────────────────────

    /**
     * A supersaw. Script door: `Ignitor.supersaw(freq, configure)`, the same [OscSuperSawBuilder] (knobs `voices`,
     * `spread`, `analog`, `analogSpread`, `spreadPower`, `sideAtten`, `gainJitter`, `centerJitter`, `phasePool`,
     * `phase`).
     */
    fun supersaw(
        freq: IgnitorDsl = IgnitorDsl.Freq,
        configure: ((OscSuperSawBuilder) -> OscSuperSawBuilder)? = null,
    ): IgnitorDsl.SuperSaw = OscSuperSawBuilder(IgnitorDsl.SuperSaw(freq = freq)).configured(configure).node

    /**
     * A supersine. Script door: `Ignitor.supersine(freq, configure)`, the same [OscSuperSineBuilder] (knobs `voices`,
     * `spread`, `analog`, `analogSpread`, `spreadPower`, `sideAtten`, `gainJitter`, `centerJitter`, `phasePool`,
     * `phase`).
     */
    fun supersine(
        freq: IgnitorDsl = IgnitorDsl.Freq,
        configure: ((OscSuperSineBuilder) -> OscSuperSineBuilder)? = null,
    ): IgnitorDsl.SuperSine = OscSuperSineBuilder(IgnitorDsl.SuperSine(freq = freq)).configured(configure).node

    /**
     * A supersquare. Script door: `Ignitor.supersquare(freq, configure)`, the same [OscSuperSquareBuilder] (knobs
     * `voices`, `spread`, `analog`, `analogSpread`, `spreadPower`, `sideAtten`, `gainJitter`, `centerJitter`,
     * `phasePool`, `phase`).
     */
    fun supersquare(
        freq: IgnitorDsl = IgnitorDsl.Freq,
        configure: ((OscSuperSquareBuilder) -> OscSuperSquareBuilder)? = null,
    ): IgnitorDsl.SuperSquare = OscSuperSquareBuilder(IgnitorDsl.SuperSquare(freq = freq)).configured(configure).node

    /**
     * A supertri. Script door: `Ignitor.supertri(freq, configure)`, the same [OscSuperTriBuilder] (knobs `voices`,
     * `spread`, `analog`, `analogSpread`, `spreadPower`, `sideAtten`, `gainJitter`, `centerJitter`, `phasePool`,
     * `phase`).
     */
    fun supertri(
        freq: IgnitorDsl = IgnitorDsl.Freq,
        configure: ((OscSuperTriBuilder) -> OscSuperTriBuilder)? = null,
    ): IgnitorDsl.SuperTri = OscSuperTriBuilder(IgnitorDsl.SuperTri(freq = freq)).configured(configure).node

    /**
     * A superramp. Script door: `Ignitor.superramp(freq, configure)`, the same [OscSuperRampBuilder] (knobs `voices`,
     * `spread`, `analog`, `analogSpread`, `spreadPower`, `sideAtten`, `gainJitter`, `centerJitter`, `phasePool`,
     * `phase`).
     */
    fun superramp(
        freq: IgnitorDsl = IgnitorDsl.Freq,
        configure: ((OscSuperRampBuilder) -> OscSuperRampBuilder)? = null,
    ): IgnitorDsl.SuperRamp = OscSuperRampBuilder(IgnitorDsl.SuperRamp(freq = freq)).configured(configure).node

    // ── Physical Models ──────────────────────────────────────────────────────

    /**
     * A Karplus-Strong plucked string. Script door: `Ignitor.pluck(freq, configure)`, the same [OscPluckBuilder] (knobs
     * `feedback`, `brightness`, `pickPosition`, `stiffness`, `analog`).
     *
     * The string knobs default to sealed constants, not the node's open `Slots.*` params: a custom pluck ignores
     * sprudel's per-note modulation unless the author opts in with `Ign.slot.*`. The script door builds on this door,
     * so the sealed defaults live here once.
     */
    fun pluck(
        freq: IgnitorDsl = IgnitorDsl.Freq,
        configure: ((OscPluckBuilder) -> OscPluckBuilder)? = null,
    ): IgnitorDsl.Pluck = OscPluckBuilder(
        IgnitorDsl.Pluck(
            freq = freq,
            feedback = 0.996.ign,
            brightness = 0.5.ign,
            pickPosition = 0.5.ign,
            stiffness = 0.0.ign,
        ),
    ).configured(configure).node

    /**
     * A unison Karplus-Strong plucked string. Script door: `Ignitor.superpluck(freq, configure)`, the same
     * [OscSuperPluckBuilder] (knobs `voices`, `spread`, `feedback`, `brightness`, `pickPosition`, `stiffness`,
     * `analog`, `analogSpread`).
     *
     * Sealed constants like [pluck]; unlike the super oscillators, the unison pair is sealed too. The script door
     * builds on this door.
     */
    fun superpluck(
        freq: IgnitorDsl = IgnitorDsl.Freq,
        configure: ((OscSuperPluckBuilder) -> OscSuperPluckBuilder)? = null,
    ): IgnitorDsl.SuperPluck = OscSuperPluckBuilder(
        IgnitorDsl.SuperPluck(
            freq = freq,
            voices = 8.0.ign,
            spread = 0.2.ign,
            feedback = 0.996.ign,
            brightness = 0.5.ign,
            pickPosition = 0.5.ign,
            stiffness = 0.0.ign,
        ),
    ).configured(configure).node

    // ── Parameter Slot, Constant, Selection ──────────────────────────────────

    /** A named parameter slot with a default value. Script door: `Ignitor.param(name, default, description)`. */
    fun param(name: String, default: Double, description: String = ""): IgnitorDsl.Param =
        IgnitorDsl.Param(name = name, default = default, description = description)

    /** A fixed constant that no ignitorParam overrides. Script door: `Ignitor.constant(value)`. */
    fun constant(value: Double): IgnitorDsl.Constant = IgnitorDsl.Constant(value)

    /** Selects one of [children] per note by the voice's `soundIndex`. Script door: `Ignitor.variants(...)`. */
    fun variants(vararg children: IgnitorDsl): IgnitorDsl.Variants = IgnitorDsl.Variants(children.toList())
}
