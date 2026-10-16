/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

import io.peekandpoke.klang.audio_bridge.constants.PULSE_FALL_FLANK
import io.peekandpoke.klang.audio_bridge.constants.PULSE_MIN_FLANK_SAMPLES
import io.peekandpoke.klang.audio_bridge.constants.PULSE_RISE_FLANK
import io.peekandpoke.klang.audio_bridge.constants.RAMP_RESET_SAMPLES
import io.peekandpoke.klang.audio_bridge.constants.RAMP_SHAPE_MAX
import io.peekandpoke.klang.audio_bridge.constants.SUPERRAMP_CENTER_JITTER_SCALE
import io.peekandpoke.klang.audio_bridge.constants.SUPERRAMP_GAIN_JITTER
import io.peekandpoke.klang.audio_bridge.constants.SUPERRAMP_SIDE_ATTEN
import io.peekandpoke.klang.audio_bridge.constants.SUPERRAMP_SPREAD_POWER
import io.peekandpoke.klang.audio_bridge.constants.SUPERSAW_CENTER_JITTER_SCALE
import io.peekandpoke.klang.audio_bridge.constants.SUPERSAW_GAIN_JITTER
import io.peekandpoke.klang.audio_bridge.constants.SUPERSAW_SIDE_ATTEN
import io.peekandpoke.klang.audio_bridge.constants.SUPERSAW_SPREAD_POWER
import io.peekandpoke.klang.audio_bridge.constants.SUPERSINE_CENTER_JITTER_SCALE
import io.peekandpoke.klang.audio_bridge.constants.SUPERSINE_GAIN_JITTER
import io.peekandpoke.klang.audio_bridge.constants.SUPERSINE_SIDE_ATTEN
import io.peekandpoke.klang.audio_bridge.constants.SUPERSINE_SPREAD_POWER
import io.peekandpoke.klang.audio_bridge.constants.SUPERSQUARE_CENTER_JITTER_SCALE
import io.peekandpoke.klang.audio_bridge.constants.SUPERSQUARE_GAIN_JITTER
import io.peekandpoke.klang.audio_bridge.constants.SUPERSQUARE_SIDE_ATTEN
import io.peekandpoke.klang.audio_bridge.constants.SUPERSQUARE_SPREAD_POWER
import io.peekandpoke.klang.audio_bridge.constants.SUPERTRI_CENTER_JITTER_SCALE
import io.peekandpoke.klang.audio_bridge.constants.SUPERTRI_GAIN_JITTER
import io.peekandpoke.klang.audio_bridge.constants.SUPERTRI_SIDE_ATTEN
import io.peekandpoke.klang.audio_bridge.constants.SUPERTRI_SPREAD_POWER

/**
 * The typed Kotlin `Ign`: the KlangScript object `Ign` (`Ignitor`) word for word, for Kotlin (`docs/tasks/kotlin-typed-doors.md`,
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
 * as plain numbers (`resetSamples`, `shapeMax`, the flanks, the unison character knobs) stay plain `Double` here
 * (Q51; no `Int` overloads, write `4.0`).
 *
 * Pending (the typed-door rollout, after spike S1): `saw` is on its builder; the other doors with knobs still take
 * them as FLAT named parameters (`Ign.supersaw(voices = 9.ign)`) and move onto their builders next.
 */
object Ign {

    override fun toString(): String = "[Ign]"

    /** The canonical open slots of a custom sound, the same objects as the script's `Ign.slot`. */
    val slot: IgnitorDsl.Slots = IgnitorDsl.Slots

    // ── Oscillator Primitives ────────────────────────────────────────────────

    /** The voice's note frequency. Script door: `Ignitor.freq()`. */
    fun freq(): IgnitorDsl = IgnitorDsl.Freq

    /**
     * A sine oscillator. Script door: `Ignitor.sine(freq, configure)` with the builder knobs `analog`, `fundamental`,
     * `analogSpread` and `phase` as named parameters.
     */
    fun sine(
        freq: IgnitorDsl = IgnitorDsl.Freq,
        analog: IgnitorDsl = IgnitorDsl.Slots.analog,
        fundamental: IgnitorDsl = 1.0.ign,
        analogSpread: IgnitorDsl = 1.0.ign,
        phase: IgnitorDsl = 0.0.ign,
    ): IgnitorDsl.Sine = IgnitorDsl.Sine(
        freq = freq,
        analog = analog,
        fundamental = fundamental,
        analogSpread = analogSpread,
        phase = phase,
    )

    /**
     * A sawtooth oscillator. Script door: `Ignitor.saw(freq, configure)`; the same [OscSawBuilder] (knobs `analog`,
     * `resetSamples`, `shapeMax`, `phase`) and the same shape: `Ign.saw(110.ign) { it.analog(5.ign) }`.
     */
    fun saw(
        freq: IgnitorDsl = IgnitorDsl.Freq,
        configure: ((OscSawBuilder) -> OscSawBuilder)? = null,
    ): IgnitorDsl.Saw = OscSawBuilder(IgnitorDsl.Saw(freq = freq)).configured(configure).node

    /**
     * A square oscillator, the flank-shaped pulse. Script door: `Ignitor.square(freq, configure)`, knobs `duty`,
     * `analog`, `flankSamples`, `riseFlank`, `fallFlank`, `phase`.
     */
    fun square(
        freq: IgnitorDsl = IgnitorDsl.Freq,
        duty: IgnitorDsl = IgnitorDsl.Slots.duty,
        analog: IgnitorDsl = IgnitorDsl.Slots.analog,
        flankSamples: Double = PULSE_MIN_FLANK_SAMPLES,
        riseFlank: Double = PULSE_RISE_FLANK,
        fallFlank: Double = PULSE_FALL_FLANK,
        phase: IgnitorDsl = 0.0.ign,
    ): IgnitorDsl.Pulze = IgnitorDsl.Pulze(
        freq = freq,
        duty = duty,
        analog = analog,
        flankSamples = flankSamples,
        riseFlank = riseFlank,
        fallFlank = fallFlank,
        phase = phase,
    )

    /** A triangle oscillator. Script door: `Ignitor.tri(freq, configure)`, knobs `analog`, `phase`. */
    fun tri(
        freq: IgnitorDsl = IgnitorDsl.Freq,
        analog: IgnitorDsl = IgnitorDsl.Slots.analog,
        phase: IgnitorDsl = 0.0.ign,
    ): IgnitorDsl.Tri = IgnitorDsl.Tri(freq = freq, analog = analog, phase = phase)

    /** A ramp (reverse sawtooth) oscillator. Script door: `Ignitor.ramp(freq, configure)`, knobs `analog`, `resetSamples`, `shapeMax`, `phase`. */
    fun ramp(
        freq: IgnitorDsl = IgnitorDsl.Freq,
        analog: IgnitorDsl = IgnitorDsl.Slots.analog,
        resetSamples: Double = RAMP_RESET_SAMPLES,
        shapeMax: Double = RAMP_SHAPE_MAX,
        phase: IgnitorDsl = 0.0.ign,
    ): IgnitorDsl.Ramp = IgnitorDsl.Ramp(
        freq = freq,
        analog = analog,
        resetSamples = resetSamples,
        shapeMax = shapeMax,
        phase = phase,
    )

    /** A naive sawtooth without anti-aliasing. Script door: `Ignitor.zawtooth(freq, configure)`, knobs `analog`, `phase`. */
    fun zawtooth(
        freq: IgnitorDsl = IgnitorDsl.Freq,
        analog: IgnitorDsl = IgnitorDsl.Slots.analog,
        phase: IgnitorDsl = 0.0.ign,
    ): IgnitorDsl.Zawtooth = IgnitorDsl.Zawtooth(freq = freq, analog = analog, phase = phase)

    /** A raw ramp. Script door: `Ignitor.zamp(freq, configure)`, knobs `analog`, `phase`. */
    fun zamp(
        freq: IgnitorDsl = IgnitorDsl.Freq,
        analog: IgnitorDsl = IgnitorDsl.Slots.analog,
        phase: IgnitorDsl = 0.0.ign,
    ): IgnitorDsl.Zamp = IgnitorDsl.Zamp(freq = freq, analog = analog, phase = phase)

    /** An impulse (click) oscillator. Script door: `Ignitor.impulse(freq, configure)`, knobs `analog`, `phase`. */
    fun impulse(
        freq: IgnitorDsl = IgnitorDsl.Freq,
        analog: IgnitorDsl = IgnitorDsl.Slots.analog,
        phase: IgnitorDsl = 0.0.ign,
    ): IgnitorDsl.Impulse = IgnitorDsl.Impulse(freq = freq, analog = analog, phase = phase)

    /** A raw, aliased pulse. Script door: `Ignitor.pulze(freq, configure)`, knobs `duty`, `analog`, `phase`. */
    fun pulze(
        freq: IgnitorDsl = IgnitorDsl.Freq,
        duty: IgnitorDsl = IgnitorDsl.Slots.duty,
        analog: IgnitorDsl = IgnitorDsl.Slots.analog,
        phase: IgnitorDsl = 0.0.ign,
    ): IgnitorDsl.RawPulze = IgnitorDsl.RawPulze(freq = freq, duty = duty, analog = analog, phase = phase)

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
     * A supersaw. Script door: `Ignitor.supersaw(freq, configure)`, knobs `voices`, `spread`, `analog`, `analogSpread`,
     * `spreadPower`, `sideAtten`, `gainJitter`, `centerJitter`, `phase`.
     */
    fun supersaw(
        freq: IgnitorDsl = IgnitorDsl.Freq,
        voices: IgnitorDsl = IgnitorDsl.Slots.voices,
        spread: IgnitorDsl = IgnitorDsl.Slots.spread,
        analog: IgnitorDsl = IgnitorDsl.Slots.analog,
        analogSpread: IgnitorDsl = 1.0.ign,
        spreadPower: Double = SUPERSAW_SPREAD_POWER,
        sideAtten: Double = SUPERSAW_SIDE_ATTEN,
        gainJitter: Double = SUPERSAW_GAIN_JITTER,
        centerJitter: Double = SUPERSAW_CENTER_JITTER_SCALE,
        phase: IgnitorDsl = 0.0.ign,
    ): IgnitorDsl.SuperSaw = IgnitorDsl.SuperSaw(
        freq = freq,
        voices = voices,
        spread = spread,
        analog = analog,
        analogSpread = analogSpread,
        spreadPower = spreadPower,
        sideAtten = sideAtten,
        gainJitter = gainJitter,
        centerJitterScale = centerJitter,
        phase = phase,
    )

    /** A supersine. Script door: `Ignitor.supersine(freq, configure)`, the knobs of [supersaw]. */
    fun supersine(
        freq: IgnitorDsl = IgnitorDsl.Freq,
        voices: IgnitorDsl = IgnitorDsl.Slots.voices,
        spread: IgnitorDsl = IgnitorDsl.Slots.spread,
        analog: IgnitorDsl = IgnitorDsl.Slots.analog,
        analogSpread: IgnitorDsl = 1.0.ign,
        spreadPower: Double = SUPERSINE_SPREAD_POWER,
        sideAtten: Double = SUPERSINE_SIDE_ATTEN,
        gainJitter: Double = SUPERSINE_GAIN_JITTER,
        centerJitter: Double = SUPERSINE_CENTER_JITTER_SCALE,
        phase: IgnitorDsl = 0.0.ign,
    ): IgnitorDsl.SuperSine = IgnitorDsl.SuperSine(
        freq = freq,
        voices = voices,
        spread = spread,
        analog = analog,
        analogSpread = analogSpread,
        spreadPower = spreadPower,
        sideAtten = sideAtten,
        gainJitter = gainJitter,
        centerJitterScale = centerJitter,
        phase = phase,
    )

    /** A supersquare. Script door: `Ignitor.supersquare(freq, configure)`, the knobs of [supersaw]. */
    fun supersquare(
        freq: IgnitorDsl = IgnitorDsl.Freq,
        voices: IgnitorDsl = IgnitorDsl.Slots.voices,
        spread: IgnitorDsl = IgnitorDsl.Slots.spread,
        analog: IgnitorDsl = IgnitorDsl.Slots.analog,
        analogSpread: IgnitorDsl = 1.0.ign,
        spreadPower: Double = SUPERSQUARE_SPREAD_POWER,
        sideAtten: Double = SUPERSQUARE_SIDE_ATTEN,
        gainJitter: Double = SUPERSQUARE_GAIN_JITTER,
        centerJitter: Double = SUPERSQUARE_CENTER_JITTER_SCALE,
        phase: IgnitorDsl = 0.0.ign,
    ): IgnitorDsl.SuperSquare = IgnitorDsl.SuperSquare(
        freq = freq,
        voices = voices,
        spread = spread,
        analog = analog,
        analogSpread = analogSpread,
        spreadPower = spreadPower,
        sideAtten = sideAtten,
        gainJitter = gainJitter,
        centerJitterScale = centerJitter,
        phase = phase,
    )

    /** A supertri. Script door: `Ignitor.supertri(freq, configure)`, the knobs of [supersaw]. */
    fun supertri(
        freq: IgnitorDsl = IgnitorDsl.Freq,
        voices: IgnitorDsl = IgnitorDsl.Slots.voices,
        spread: IgnitorDsl = IgnitorDsl.Slots.spread,
        analog: IgnitorDsl = IgnitorDsl.Slots.analog,
        analogSpread: IgnitorDsl = 1.0.ign,
        spreadPower: Double = SUPERTRI_SPREAD_POWER,
        sideAtten: Double = SUPERTRI_SIDE_ATTEN,
        gainJitter: Double = SUPERTRI_GAIN_JITTER,
        centerJitter: Double = SUPERTRI_CENTER_JITTER_SCALE,
        phase: IgnitorDsl = 0.0.ign,
    ): IgnitorDsl.SuperTri = IgnitorDsl.SuperTri(
        freq = freq,
        voices = voices,
        spread = spread,
        analog = analog,
        analogSpread = analogSpread,
        spreadPower = spreadPower,
        sideAtten = sideAtten,
        gainJitter = gainJitter,
        centerJitterScale = centerJitter,
        phase = phase,
    )

    /** A superramp. Script door: `Ignitor.superramp(freq, configure)`, the knobs of [supersaw]. */
    fun superramp(
        freq: IgnitorDsl = IgnitorDsl.Freq,
        voices: IgnitorDsl = IgnitorDsl.Slots.voices,
        spread: IgnitorDsl = IgnitorDsl.Slots.spread,
        analog: IgnitorDsl = IgnitorDsl.Slots.analog,
        analogSpread: IgnitorDsl = 1.0.ign,
        spreadPower: Double = SUPERRAMP_SPREAD_POWER,
        sideAtten: Double = SUPERRAMP_SIDE_ATTEN,
        gainJitter: Double = SUPERRAMP_GAIN_JITTER,
        centerJitter: Double = SUPERRAMP_CENTER_JITTER_SCALE,
        phase: IgnitorDsl = 0.0.ign,
    ): IgnitorDsl.SuperRamp = IgnitorDsl.SuperRamp(
        freq = freq,
        voices = voices,
        spread = spread,
        analog = analog,
        analogSpread = analogSpread,
        spreadPower = spreadPower,
        sideAtten = sideAtten,
        gainJitter = gainJitter,
        centerJitterScale = centerJitter,
        phase = phase,
    )

    // ── Physical Models ──────────────────────────────────────────────────────

    /**
     * A Karplus-Strong plucked string. Script door: `Ignitor.pluck(freq, configure)`, knobs `feedback`, `brightness`,
     * `pickPosition`, `stiffness`, `analog`.
     *
     * The string knobs default to sealed constants, not the node's open `Slots.*` params: a custom pluck ignores
     * sprudel's per-note modulation unless the author opts in with `Ign.slot.*`. The script door builds on this door,
     * so the sealed defaults live here once.
     */
    fun pluck(
        freq: IgnitorDsl = IgnitorDsl.Freq,
        feedback: IgnitorDsl = 0.996.ign,
        brightness: IgnitorDsl = 0.5.ign,
        pickPosition: IgnitorDsl = 0.5.ign,
        stiffness: IgnitorDsl = 0.0.ign,
        analog: IgnitorDsl = IgnitorDsl.Slots.analog,
    ): IgnitorDsl.Pluck = IgnitorDsl.Pluck(
        freq = freq,
        feedback = feedback,
        brightness = brightness,
        pickPosition = pickPosition,
        stiffness = stiffness,
        analog = analog,
    )

    /**
     * A unison Karplus-Strong plucked string. Script door: `Ignitor.superpluck(freq, configure)`, knobs `voices`,
     * `spread`, `feedback`, `brightness`, `pickPosition`, `stiffness`, `analog`, `analogSpread`.
     *
     * Sealed constants like [pluck]; unlike the super oscillators, the unison pair is sealed too. The script door
     * builds on this door.
     */
    fun superpluck(
        freq: IgnitorDsl = IgnitorDsl.Freq,
        voices: IgnitorDsl = 8.0.ign,
        spread: IgnitorDsl = 0.2.ign,
        feedback: IgnitorDsl = 0.996.ign,
        brightness: IgnitorDsl = 0.5.ign,
        pickPosition: IgnitorDsl = 0.5.ign,
        stiffness: IgnitorDsl = 0.0.ign,
        analog: IgnitorDsl = IgnitorDsl.Slots.analog,
        analogSpread: IgnitorDsl = 1.0.ign,
    ): IgnitorDsl.SuperPluck = IgnitorDsl.SuperPluck(
        freq = freq,
        voices = voices,
        spread = spread,
        feedback = feedback,
        brightness = brightness,
        pickPosition = pickPosition,
        stiffness = stiffness,
        analog = analog,
        analogSpread = analogSpread,
    )

    // ── Parameter Slot, Constant, Selection ──────────────────────────────────

    /** A named parameter slot with a default value. Script door: `Ignitor.param(name, default, description)`. */
    fun param(name: String, default: Double, description: String = ""): IgnitorDsl.Param =
        IgnitorDsl.Param(name = name, default = default, description = description)

    /** A fixed constant that no ignitorParam overrides. Script door: `Ignitor.constant(value)`. */
    fun constant(value: Double): IgnitorDsl.Constant = IgnitorDsl.Constant(value)

    /** Selects one of [children] per note by the voice's `soundIndex`. Script door: `Ignitor.variants(...)`. */
    fun variants(vararg children: IgnitorDsl): IgnitorDsl.Variants = IgnitorDsl.Variants(children.toList())
}
