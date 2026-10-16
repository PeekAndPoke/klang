/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

import io.peekandpoke.klang.audio_bridge.constants.SUPERRAMP_DRAW_TRIES
import io.peekandpoke.klang.audio_bridge.constants.SUPERRAMP_K_MAX
import io.peekandpoke.klang.audio_bridge.constants.SUPERRAMP_K_MIN
import io.peekandpoke.klang.audio_bridge.constants.SUPERRAMP_POOL_SIZE
import io.peekandpoke.klang.audio_bridge.constants.SUPERRAMP_REFRESH_EVERY
import io.peekandpoke.klang.audio_bridge.constants.SUPERRAMP_SELECTION
import io.peekandpoke.klang.audio_bridge.constants.SUPERRAMP_WARMUP
import io.peekandpoke.klang.audio_bridge.constants.SUPERSAW_DRAW_TRIES
import io.peekandpoke.klang.audio_bridge.constants.SUPERSAW_K_MAX
import io.peekandpoke.klang.audio_bridge.constants.SUPERSAW_K_MIN
import io.peekandpoke.klang.audio_bridge.constants.SUPERSAW_POOL_SIZE
import io.peekandpoke.klang.audio_bridge.constants.SUPERSAW_REFRESH_EVERY
import io.peekandpoke.klang.audio_bridge.constants.SUPERSAW_SELECTION
import io.peekandpoke.klang.audio_bridge.constants.SUPERSAW_WARMUP
import io.peekandpoke.klang.audio_bridge.constants.SUPERSINE_DRAW_TRIES
import io.peekandpoke.klang.audio_bridge.constants.SUPERSINE_K_MAX
import io.peekandpoke.klang.audio_bridge.constants.SUPERSINE_K_MIN
import io.peekandpoke.klang.audio_bridge.constants.SUPERSINE_POOL_SIZE
import io.peekandpoke.klang.audio_bridge.constants.SUPERSINE_REFRESH_EVERY
import io.peekandpoke.klang.audio_bridge.constants.SUPERSINE_SELECTION
import io.peekandpoke.klang.audio_bridge.constants.SUPERSINE_WARMUP
import io.peekandpoke.klang.audio_bridge.constants.SUPERSQUARE_DRAW_TRIES
import io.peekandpoke.klang.audio_bridge.constants.SUPERSQUARE_K_MAX
import io.peekandpoke.klang.audio_bridge.constants.SUPERSQUARE_K_MIN
import io.peekandpoke.klang.audio_bridge.constants.SUPERSQUARE_POOL_SIZE
import io.peekandpoke.klang.audio_bridge.constants.SUPERSQUARE_REFRESH_EVERY
import io.peekandpoke.klang.audio_bridge.constants.SUPERSQUARE_SELECTION
import io.peekandpoke.klang.audio_bridge.constants.SUPERSQUARE_WARMUP
import io.peekandpoke.klang.audio_bridge.constants.SUPERTRI_DRAW_TRIES
import io.peekandpoke.klang.audio_bridge.constants.SUPERTRI_K_MAX
import io.peekandpoke.klang.audio_bridge.constants.SUPERTRI_K_MIN
import io.peekandpoke.klang.audio_bridge.constants.SUPERTRI_POOL_SIZE
import io.peekandpoke.klang.audio_bridge.constants.SUPERTRI_REFRESH_EVERY
import io.peekandpoke.klang.audio_bridge.constants.SUPERTRI_SELECTION
import io.peekandpoke.klang.audio_bridge.constants.SUPERTRI_WARMUP

/*
 * The builders: what a door hands to its `configure` lambda, ONE set for both doors
 * (`docs/tasks-archive/2026-10/20261016-kotlin-typed-doors.md`, "Revision (maintainer, 2026-10-16)").
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

// ── Sine ──────────────────────────────────────────────────────────────

/** Builder for [IgnitorDsl.Sine], handed to the `configure` lambda of `Ign.sine(...)`. Knobs: `analog`, `fundamental`, `harmonics`, `octaves`, `suboctaves`, `partial`, `analogSpread`, `phase`. */
data class OscSineBuilder(val node: IgnitorDsl.Sine) {

    /** Where in its cycle the sine runs; the text is on the script knob `phase` (`scriptPhase` in `klangscript-libs`). */
    fun phase(phase: IgnitorDsl): OscSineBuilder = copy(node = node.copy(phase = phase))

    /** How analog the oscillator is; the text is on the script knob `analog` (`scriptAnalog` in `klangscript-libs`). */
    fun analog(character: IgnitorDsl): OscSineBuilder = copy(node = node.copy(analog = character))

    /** Gain of the sine's own partial; the text is on the script knob `fundamental` (`scriptFundamental` in `klangscript-libs`). */
    fun fundamental(gain: IgnitorDsl): OscSineBuilder = copy(node = node.copy(fundamental = gain))

    /** Adds `count` partials at `2f, 3f, 4f ...`; the text is on the script knob `harmonics` (`scriptHarmonics` in `klangscript-libs`). */
    fun harmonics(count: IgnitorDsl, rolloff: IgnitorDsl = 1.0.ign): OscSineBuilder =
        copy(node = node.copy(harmonics = count, harmonicsRolloff = rolloff))

    /** Adds `count` partials at `2f, 4f, 8f ...`; the text is on the script knob `octaves` (`scriptOctaves` in `klangscript-libs`). */
    fun octaves(count: IgnitorDsl, rolloff: IgnitorDsl = 1.0.ign): OscSineBuilder =
        copy(node = node.copy(octaves = count, octavesRolloff = rolloff))

    /** Adds `count` partials at `f/2, f/4, f/8 ...`; the text is on the script knob `suboctaves` (`scriptSuboctaves` in `klangscript-libs`). */
    fun suboctaves(count: IgnitorDsl, rolloff: IgnitorDsl = 1.0.ign): OscSineBuilder =
        copy(node = node.copy(suboctaves = count, suboctavesRolloff = rolloff))

    /** Adds ONE partial at `ratio` times the sine's frequency, one more per call, in written order; the text is on the script knob `partial` (`scriptPartial` in `klangscript-libs`). */
    fun partial(ratio: IgnitorDsl, gain: IgnitorDsl = 1.0.ign, phase: IgnitorDsl = 0.0.ign): OscSineBuilder =
        copy(node = node.copy(partials = node.partials + IgnitorDsl.Sine.Partial(ratio = ratio, gain = gain, phase = phase)))

    /** How much the partials drift against each other under `analog`; the text is on the script knob `analogSpread` (`scriptAnalogSpread` in `klangscript-libs`). */
    fun analogSpread(amount: IgnitorDsl): OscSineBuilder = copy(node = node.copy(analogSpread = amount))
}

// ── Tri ───────────────────────────────────────────────────────────────

/** Builder for [IgnitorDsl.Tri], handed to the `configure` lambda of `Ign.tri(...)`. Knobs: `analog`, `phase`. */
data class OscTriBuilder(val node: IgnitorDsl.Tri) {

    /** Where in its cycle the triangle runs; the text is on the script knob `phase` (`scriptPhase` in `klangscript-libs`). */
    fun phase(phase: IgnitorDsl): OscTriBuilder = copy(node = node.copy(phase = phase))

    /** How analog the oscillator is; the text is on the script knob `analog` (`scriptAnalog` in `klangscript-libs`). */
    fun analog(character: IgnitorDsl): OscTriBuilder = copy(node = node.copy(analog = character))
}

// ── Zawtooth ──────────────────────────────────────────────────────────

/** Builder for [IgnitorDsl.Zawtooth], handed to the `configure` lambda of `Ign.zawtooth(...)`. Knobs: `analog`, `phase`. */
data class OscZawtoothBuilder(val node: IgnitorDsl.Zawtooth) {

    /** Where in its cycle the zawtooth runs; the text is on the script knob `phase` (`scriptPhase` in `klangscript-libs`). */
    fun phase(phase: IgnitorDsl): OscZawtoothBuilder = copy(node = node.copy(phase = phase))

    /** How analog the oscillator is; the text is on the script knob `analog` (`scriptAnalog` in `klangscript-libs`). */
    fun analog(character: IgnitorDsl): OscZawtoothBuilder = copy(node = node.copy(analog = character))
}

// ── Zamp ──────────────────────────────────────────────────────────────

/** Builder for [IgnitorDsl.Zamp], handed to the `configure` lambda of `Ign.zamp(...)`. Knobs: `analog`, `phase`. */
data class OscZampBuilder(val node: IgnitorDsl.Zamp) {

    /** Where in its cycle the zamp runs; the text is on the script knob `phase` (`scriptPhase` in `klangscript-libs`). */
    fun phase(phase: IgnitorDsl): OscZampBuilder = copy(node = node.copy(phase = phase))

    /** How analog the oscillator is; the text is on the script knob `analog` (`scriptAnalog` in `klangscript-libs`). */
    fun analog(character: IgnitorDsl): OscZampBuilder = copy(node = node.copy(analog = character))
}

// ── Impulse ───────────────────────────────────────────────────────────

/** Builder for [IgnitorDsl.Impulse], handed to the `configure` lambda of `Ign.impulse(...)`. Knobs: `analog`, `phase`. */
data class OscImpulseBuilder(val node: IgnitorDsl.Impulse) {

    /** Where in its cycle the impulse runs; the text is on the script knob `phase` (`scriptPhase` in `klangscript-libs`). */
    fun phase(phase: IgnitorDsl): OscImpulseBuilder = copy(node = node.copy(phase = phase))

    /** How analog the oscillator is; the text is on the script knob `analog` (`scriptAnalog` in `klangscript-libs`). */
    fun analog(character: IgnitorDsl): OscImpulseBuilder = copy(node = node.copy(analog = character))
}

// ── RawPulze ──────────────────────────────────────────────────────────

/** Builder for [IgnitorDsl.RawPulze], handed to the `configure` lambda of `Ign.pulze(...)`. Knobs: `duty`, `analog`, `phase`. */
data class OscPulzeBuilder(val node: IgnitorDsl.RawPulze) {

    /** Where in its cycle the raw pulse runs; the text is on the script knob `phase` (`scriptPhase` in `klangscript-libs`). */
    fun phase(phase: IgnitorDsl): OscPulzeBuilder = copy(node = node.copy(phase = phase))

    /** Pulse width, the share of each cycle the wave is high; the text is on the script knob `duty` (`scriptDuty` in `klangscript-libs`). */
    fun duty(width: IgnitorDsl): OscPulzeBuilder = copy(node = node.copy(duty = width))

    /** How analog the oscillator is; the text is on the script knob `analog` (`scriptAnalog` in `klangscript-libs`). */
    fun analog(character: IgnitorDsl): OscPulzeBuilder = copy(node = node.copy(analog = character))
}

// ── Pulze ─────────────────────────────────────────────────────────────

/** Builder for [IgnitorDsl.Pulze], handed to the `configure` lambda of `Ign.square(...)`. Knobs: `duty`, `analog`, `flankSamples`, `riseFlank`, `fallFlank`, `phase`. */
data class OscSquareBuilder(val node: IgnitorDsl.Pulze) {

    /** Where in its cycle the square runs; the text is on the script knob `phase` (`scriptPhase` in `klangscript-libs`). */
    fun phase(phase: IgnitorDsl): OscSquareBuilder = copy(node = node.copy(phase = phase))

    /** Pulse width, the share of each cycle the wave is high; the text is on the script knob `duty` (`scriptDuty` in `klangscript-libs`). */
    fun duty(width: IgnitorDsl): OscSquareBuilder = copy(node = node.copy(duty = width))

    /** How analog the oscillator is; the text is on the script knob `analog` (`scriptAnalog` in `klangscript-libs`). */
    fun analog(character: IgnitorDsl): OscSquareBuilder = copy(node = node.copy(analog = character))

    /** Minimum flank length in samples; the text is on the script knob `flankSamples` (`scriptFlankSamples` in `klangscript-libs`). */
    fun flankSamples(flankSamples: Double): OscSquareBuilder = copy(node = node.copy(flankSamples = flankSamples))

    /** Rising-edge flank fraction of the plateau; the text is on the script knob `riseFlank` (`scriptRiseFlank` in `klangscript-libs`). */
    fun riseFlank(riseFlank: Double): OscSquareBuilder = copy(node = node.copy(riseFlank = riseFlank))

    /** Falling-edge flank fraction of the plateau; the text is on the script knob `fallFlank` (`scriptFallFlank` in `klangscript-libs`). */
    fun fallFlank(fallFlank: Double): OscSquareBuilder = copy(node = node.copy(fallFlank = fallFlank))
}

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

// ── Ramp ──────────────────────────────────────────────────────────────

/** Builder for [IgnitorDsl.Ramp], handed to the `configure` lambda of `Ign.ramp(...)`. Knobs: `analog`, `resetSamples`, `shapeMax`, `phase`. */
data class OscRampBuilder(val node: IgnitorDsl.Ramp) {

    /** Where in its cycle the ramp runs; the text is on the script knob `phase` (`scriptPhase` in `klangscript-libs`). */
    fun phase(phase: IgnitorDsl): OscRampBuilder = copy(node = node.copy(phase = phase))

    /** How analog the oscillator is; the text is on the script knob `analog` (`scriptAnalog` in `klangscript-libs`). */
    fun analog(character: IgnitorDsl): OscRampBuilder = copy(node = node.copy(analog = character))

    /** Analog flyback time in samples; the text is on the script knob `resetSamples` (`scriptResetSamples` in `klangscript-libs`). */
    fun resetSamples(resetSamples: Double): OscRampBuilder = copy(node = node.copy(resetSamples = resetSamples))

    /** Max flyback fraction of a cycle; the text is on the script knob `shapeMax` (`scriptShapeMax` in `klangscript-libs`). */
    fun shapeMax(shapeMax: Double): OscRampBuilder = copy(node = node.copy(shapeMax = shapeMax))
}

// ── SuperSaw ──────────────────────────────────────────────────────────

/** Builder for [IgnitorDsl.SuperSaw], handed to the `configure` lambda of `Ign.supersaw(...)`. Knobs: `voices`, `spread`, `analog`, `analogSpread`, `spreadPower`, `sideAtten`, `gainJitter`, `centerJitter`, `phasePool`, `phase`. */
data class OscSuperSawBuilder(val node: IgnitorDsl.SuperSaw) {

    /** Shifts the whole stack in its cycle; the text is on the script knob `phase` (`scriptPhase` in `klangscript-libs`). */
    fun phase(phase: IgnitorDsl): OscSuperSawBuilder = copy(node = node.copy(phase = phase))

    /** Number of detuned voices in the stack; the text is on the script knob `voices` (`scriptVoices` in `klangscript-libs`). */
    fun voices(voices: IgnitorDsl): OscSuperSawBuilder = copy(node = node.copy(voices = voices))

    /** Unison frequency spread between the voices; the text is on the script knob `spread` (`scriptSpread` in `klangscript-libs`). */
    fun spread(spread: IgnitorDsl): OscSuperSawBuilder = copy(node = node.copy(spread = spread))

    /** How analog the oscillator is; the text is on the script knob `analog` (`scriptAnalog` in `klangscript-libs`). */
    fun analog(character: IgnitorDsl): OscSuperSawBuilder = copy(node = node.copy(analog = character))

    /** How much the voices drift against each other under `analog`; the text is on the script knob `analogSpread` (`scriptAnalogSpread` in `klangscript-libs`). */
    fun analogSpread(amount: IgnitorDsl): OscSuperSawBuilder = copy(node = node.copy(analogSpread = amount))

    /** Detune spacing shape; the text is on the script knob `spreadPower` (`scriptSpreadPower` in `klangscript-libs`). */
    fun spreadPower(spreadPower: Double): OscSuperSawBuilder = copy(node = node.copy(spreadPower = spreadPower))

    /** Center-dominant gain falloff; the text is on the script knob `sideAtten` (`scriptSideAtten` in `klangscript-libs`). */
    fun sideAtten(sideAtten: Double): OscSuperSawBuilder = copy(node = node.copy(sideAtten = sideAtten))

    /** Per-voice random amplitude offset; the text is on the script knob `gainJitter` (`scriptGainJitter` in `klangscript-libs`). */
    fun gainJitter(gainJitter: Double): OscSuperSawBuilder = copy(node = node.copy(gainJitter = gainJitter))

    /** Fraction of `gainJitter` the center voice gets; the text is on the script knob `centerJitter` (`scriptCenterJitter` in `klangscript-libs`). */
    fun centerJitter(centerJitter: Double): OscSuperSawBuilder = copy(node = node.copy(centerJitterScale = centerJitter))

    /**
     * Configures the banded start-phase pool in ONE call, `on` 1 by default, the rest this family's engine constants;
     * the text is on the script knob `phasePool` (`scriptPhasePool` in `klangscript-libs`).
     */
    fun phasePool(
        on: Double = 1.0,
        kMin: Double = SUPERSAW_K_MIN,
        kMax: Double = SUPERSAW_K_MAX,
        drawTries: Double = SUPERSAW_DRAW_TRIES,
        poolSize: Double = SUPERSAW_POOL_SIZE,
        refreshEvery: Double = SUPERSAW_REFRESH_EVERY,
        selection: String = SUPERSAW_SELECTION,
        warmup: Double = SUPERSAW_WARMUP,
    ): OscSuperSawBuilder = copy(
        node = node.copy(
            phasePool = on,
            kMin = kMin,
            kMax = kMax,
            drawTries = drawTries,
            poolSize = poolSize,
            refreshEvery = refreshEvery,
            selection = selection,
            warmup = warmup,
        ),
    )
}

// ── SuperSine ─────────────────────────────────────────────────────────

/** Builder for [IgnitorDsl.SuperSine], handed to the `configure` lambda of `Ign.supersine(...)`. Knobs: `voices`, `spread`, `analog`, `analogSpread`, `spreadPower`, `sideAtten`, `gainJitter`, `centerJitter`, `phasePool`, `phase`. */
data class OscSuperSineBuilder(val node: IgnitorDsl.SuperSine) {

    /** Shifts the whole stack in its cycle; the text is on the script knob `phase` (`scriptPhase` in `klangscript-libs`). */
    fun phase(phase: IgnitorDsl): OscSuperSineBuilder = copy(node = node.copy(phase = phase))

    /** Number of detuned voices in the stack; the text is on the script knob `voices` (`scriptVoices` in `klangscript-libs`). */
    fun voices(voices: IgnitorDsl): OscSuperSineBuilder = copy(node = node.copy(voices = voices))

    /** Unison frequency spread between the voices; the text is on the script knob `spread` (`scriptSpread` in `klangscript-libs`). */
    fun spread(spread: IgnitorDsl): OscSuperSineBuilder = copy(node = node.copy(spread = spread))

    /** How analog the oscillator is; the text is on the script knob `analog` (`scriptAnalog` in `klangscript-libs`). */
    fun analog(character: IgnitorDsl): OscSuperSineBuilder = copy(node = node.copy(analog = character))

    /** How much the voices drift against each other under `analog`; the text is on the script knob `analogSpread` (`scriptAnalogSpread` in `klangscript-libs`). */
    fun analogSpread(amount: IgnitorDsl): OscSuperSineBuilder = copy(node = node.copy(analogSpread = amount))

    /** Detune spacing shape; the text is on the script knob `spreadPower` (`scriptSpreadPower` in `klangscript-libs`). */
    fun spreadPower(spreadPower: Double): OscSuperSineBuilder = copy(node = node.copy(spreadPower = spreadPower))

    /** Center-dominant gain falloff; the text is on the script knob `sideAtten` (`scriptSideAtten` in `klangscript-libs`). */
    fun sideAtten(sideAtten: Double): OscSuperSineBuilder = copy(node = node.copy(sideAtten = sideAtten))

    /** Per-voice random amplitude offset; the text is on the script knob `gainJitter` (`scriptGainJitter` in `klangscript-libs`). */
    fun gainJitter(gainJitter: Double): OscSuperSineBuilder = copy(node = node.copy(gainJitter = gainJitter))

    /** Fraction of `gainJitter` the center voice gets; the text is on the script knob `centerJitter` (`scriptCenterJitter` in `klangscript-libs`). */
    fun centerJitter(centerJitter: Double): OscSuperSineBuilder = copy(node = node.copy(centerJitterScale = centerJitter))

    /**
     * Configures the banded start-phase pool in ONE call, `on` 1 by default, the rest this family's engine constants;
     * the text is on the script knob `phasePool` (`scriptPhasePool` in `klangscript-libs`).
     */
    fun phasePool(
        on: Double = 1.0,
        kMin: Double = SUPERSINE_K_MIN,
        kMax: Double = SUPERSINE_K_MAX,
        drawTries: Double = SUPERSINE_DRAW_TRIES,
        poolSize: Double = SUPERSINE_POOL_SIZE,
        refreshEvery: Double = SUPERSINE_REFRESH_EVERY,
        selection: String = SUPERSINE_SELECTION,
        warmup: Double = SUPERSINE_WARMUP,
    ): OscSuperSineBuilder = copy(
        node = node.copy(
            phasePool = on,
            kMin = kMin,
            kMax = kMax,
            drawTries = drawTries,
            poolSize = poolSize,
            refreshEvery = refreshEvery,
            selection = selection,
            warmup = warmup,
        ),
    )
}

// ── SuperSquare ───────────────────────────────────────────────────────

/** Builder for [IgnitorDsl.SuperSquare], handed to the `configure` lambda of `Ign.supersquare(...)`. Knobs: `voices`, `spread`, `analog`, `analogSpread`, `spreadPower`, `sideAtten`, `gainJitter`, `centerJitter`, `phasePool`, `phase`. */
data class OscSuperSquareBuilder(val node: IgnitorDsl.SuperSquare) {

    /** Shifts the whole stack in its cycle; the text is on the script knob `phase` (`scriptPhase` in `klangscript-libs`). */
    fun phase(phase: IgnitorDsl): OscSuperSquareBuilder = copy(node = node.copy(phase = phase))

    /** Number of detuned voices in the stack; the text is on the script knob `voices` (`scriptVoices` in `klangscript-libs`). */
    fun voices(voices: IgnitorDsl): OscSuperSquareBuilder = copy(node = node.copy(voices = voices))

    /** Unison frequency spread between the voices; the text is on the script knob `spread` (`scriptSpread` in `klangscript-libs`). */
    fun spread(spread: IgnitorDsl): OscSuperSquareBuilder = copy(node = node.copy(spread = spread))

    /** How analog the oscillator is; the text is on the script knob `analog` (`scriptAnalog` in `klangscript-libs`). */
    fun analog(character: IgnitorDsl): OscSuperSquareBuilder = copy(node = node.copy(analog = character))

    /** How much the voices drift against each other under `analog`; the text is on the script knob `analogSpread` (`scriptAnalogSpread` in `klangscript-libs`). */
    fun analogSpread(amount: IgnitorDsl): OscSuperSquareBuilder = copy(node = node.copy(analogSpread = amount))

    /** Detune spacing shape; the text is on the script knob `spreadPower` (`scriptSpreadPower` in `klangscript-libs`). */
    fun spreadPower(spreadPower: Double): OscSuperSquareBuilder = copy(node = node.copy(spreadPower = spreadPower))

    /** Center-dominant gain falloff; the text is on the script knob `sideAtten` (`scriptSideAtten` in `klangscript-libs`). */
    fun sideAtten(sideAtten: Double): OscSuperSquareBuilder = copy(node = node.copy(sideAtten = sideAtten))

    /** Per-voice random amplitude offset; the text is on the script knob `gainJitter` (`scriptGainJitter` in `klangscript-libs`). */
    fun gainJitter(gainJitter: Double): OscSuperSquareBuilder = copy(node = node.copy(gainJitter = gainJitter))

    /** Fraction of `gainJitter` the center voice gets; the text is on the script knob `centerJitter` (`scriptCenterJitter` in `klangscript-libs`). */
    fun centerJitter(centerJitter: Double): OscSuperSquareBuilder = copy(node = node.copy(centerJitterScale = centerJitter))

    /**
     * Configures the banded start-phase pool in ONE call, `on` 1 by default, the rest this family's engine constants;
     * the text is on the script knob `phasePool` (`scriptPhasePool` in `klangscript-libs`).
     */
    fun phasePool(
        on: Double = 1.0,
        kMin: Double = SUPERSQUARE_K_MIN,
        kMax: Double = SUPERSQUARE_K_MAX,
        drawTries: Double = SUPERSQUARE_DRAW_TRIES,
        poolSize: Double = SUPERSQUARE_POOL_SIZE,
        refreshEvery: Double = SUPERSQUARE_REFRESH_EVERY,
        selection: String = SUPERSQUARE_SELECTION,
        warmup: Double = SUPERSQUARE_WARMUP,
    ): OscSuperSquareBuilder = copy(
        node = node.copy(
            phasePool = on,
            kMin = kMin,
            kMax = kMax,
            drawTries = drawTries,
            poolSize = poolSize,
            refreshEvery = refreshEvery,
            selection = selection,
            warmup = warmup,
        ),
    )
}

// ── SuperTri ──────────────────────────────────────────────────────────

/** Builder for [IgnitorDsl.SuperTri], handed to the `configure` lambda of `Ign.supertri(...)`. Knobs: `voices`, `spread`, `analog`, `analogSpread`, `spreadPower`, `sideAtten`, `gainJitter`, `centerJitter`, `phasePool`, `phase`. */
data class OscSuperTriBuilder(val node: IgnitorDsl.SuperTri) {

    /** Shifts the whole stack in its cycle; the text is on the script knob `phase` (`scriptPhase` in `klangscript-libs`). */
    fun phase(phase: IgnitorDsl): OscSuperTriBuilder = copy(node = node.copy(phase = phase))

    /** Number of detuned voices in the stack; the text is on the script knob `voices` (`scriptVoices` in `klangscript-libs`). */
    fun voices(voices: IgnitorDsl): OscSuperTriBuilder = copy(node = node.copy(voices = voices))

    /** Unison frequency spread between the voices; the text is on the script knob `spread` (`scriptSpread` in `klangscript-libs`). */
    fun spread(spread: IgnitorDsl): OscSuperTriBuilder = copy(node = node.copy(spread = spread))

    /** How analog the oscillator is; the text is on the script knob `analog` (`scriptAnalog` in `klangscript-libs`). */
    fun analog(character: IgnitorDsl): OscSuperTriBuilder = copy(node = node.copy(analog = character))

    /** How much the voices drift against each other under `analog`; the text is on the script knob `analogSpread` (`scriptAnalogSpread` in `klangscript-libs`). */
    fun analogSpread(amount: IgnitorDsl): OscSuperTriBuilder = copy(node = node.copy(analogSpread = amount))

    /** Detune spacing shape; the text is on the script knob `spreadPower` (`scriptSpreadPower` in `klangscript-libs`). */
    fun spreadPower(spreadPower: Double): OscSuperTriBuilder = copy(node = node.copy(spreadPower = spreadPower))

    /** Center-dominant gain falloff; the text is on the script knob `sideAtten` (`scriptSideAtten` in `klangscript-libs`). */
    fun sideAtten(sideAtten: Double): OscSuperTriBuilder = copy(node = node.copy(sideAtten = sideAtten))

    /** Per-voice random amplitude offset; the text is on the script knob `gainJitter` (`scriptGainJitter` in `klangscript-libs`). */
    fun gainJitter(gainJitter: Double): OscSuperTriBuilder = copy(node = node.copy(gainJitter = gainJitter))

    /** Fraction of `gainJitter` the center voice gets; the text is on the script knob `centerJitter` (`scriptCenterJitter` in `klangscript-libs`). */
    fun centerJitter(centerJitter: Double): OscSuperTriBuilder = copy(node = node.copy(centerJitterScale = centerJitter))

    /**
     * Configures the banded start-phase pool in ONE call, `on` 1 by default, the rest this family's engine constants;
     * the text is on the script knob `phasePool` (`scriptPhasePool` in `klangscript-libs`).
     */
    fun phasePool(
        on: Double = 1.0,
        kMin: Double = SUPERTRI_K_MIN,
        kMax: Double = SUPERTRI_K_MAX,
        drawTries: Double = SUPERTRI_DRAW_TRIES,
        poolSize: Double = SUPERTRI_POOL_SIZE,
        refreshEvery: Double = SUPERTRI_REFRESH_EVERY,
        selection: String = SUPERTRI_SELECTION,
        warmup: Double = SUPERTRI_WARMUP,
    ): OscSuperTriBuilder = copy(
        node = node.copy(
            phasePool = on,
            kMin = kMin,
            kMax = kMax,
            drawTries = drawTries,
            poolSize = poolSize,
            refreshEvery = refreshEvery,
            selection = selection,
            warmup = warmup,
        ),
    )
}

// ── SuperRamp ─────────────────────────────────────────────────────────

/** Builder for [IgnitorDsl.SuperRamp], handed to the `configure` lambda of `Ign.superramp(...)`. Knobs: `voices`, `spread`, `analog`, `analogSpread`, `spreadPower`, `sideAtten`, `gainJitter`, `centerJitter`, `phasePool`, `phase`. */
data class OscSuperRampBuilder(val node: IgnitorDsl.SuperRamp) {

    /** Shifts the whole stack in its cycle; the text is on the script knob `phase` (`scriptPhase` in `klangscript-libs`). */
    fun phase(phase: IgnitorDsl): OscSuperRampBuilder = copy(node = node.copy(phase = phase))

    /** Number of detuned voices in the stack; the text is on the script knob `voices` (`scriptVoices` in `klangscript-libs`). */
    fun voices(voices: IgnitorDsl): OscSuperRampBuilder = copy(node = node.copy(voices = voices))

    /** Unison frequency spread between the voices; the text is on the script knob `spread` (`scriptSpread` in `klangscript-libs`). */
    fun spread(spread: IgnitorDsl): OscSuperRampBuilder = copy(node = node.copy(spread = spread))

    /** How analog the oscillator is; the text is on the script knob `analog` (`scriptAnalog` in `klangscript-libs`). */
    fun analog(character: IgnitorDsl): OscSuperRampBuilder = copy(node = node.copy(analog = character))

    /** How much the voices drift against each other under `analog`; the text is on the script knob `analogSpread` (`scriptAnalogSpread` in `klangscript-libs`). */
    fun analogSpread(amount: IgnitorDsl): OscSuperRampBuilder = copy(node = node.copy(analogSpread = amount))

    /** Detune spacing shape; the text is on the script knob `spreadPower` (`scriptSpreadPower` in `klangscript-libs`). */
    fun spreadPower(spreadPower: Double): OscSuperRampBuilder = copy(node = node.copy(spreadPower = spreadPower))

    /** Center-dominant gain falloff; the text is on the script knob `sideAtten` (`scriptSideAtten` in `klangscript-libs`). */
    fun sideAtten(sideAtten: Double): OscSuperRampBuilder = copy(node = node.copy(sideAtten = sideAtten))

    /** Per-voice random amplitude offset; the text is on the script knob `gainJitter` (`scriptGainJitter` in `klangscript-libs`). */
    fun gainJitter(gainJitter: Double): OscSuperRampBuilder = copy(node = node.copy(gainJitter = gainJitter))

    /** Fraction of `gainJitter` the center voice gets; the text is on the script knob `centerJitter` (`scriptCenterJitter` in `klangscript-libs`). */
    fun centerJitter(centerJitter: Double): OscSuperRampBuilder = copy(node = node.copy(centerJitterScale = centerJitter))

    /**
     * Configures the banded start-phase pool in ONE call, `on` 1 by default, the rest this family's engine constants;
     * the text is on the script knob `phasePool` (`scriptPhasePool` in `klangscript-libs`).
     */
    fun phasePool(
        on: Double = 1.0,
        kMin: Double = SUPERRAMP_K_MIN,
        kMax: Double = SUPERRAMP_K_MAX,
        drawTries: Double = SUPERRAMP_DRAW_TRIES,
        poolSize: Double = SUPERRAMP_POOL_SIZE,
        refreshEvery: Double = SUPERRAMP_REFRESH_EVERY,
        selection: String = SUPERRAMP_SELECTION,
        warmup: Double = SUPERRAMP_WARMUP,
    ): OscSuperRampBuilder = copy(
        node = node.copy(
            phasePool = on,
            kMin = kMin,
            kMax = kMax,
            drawTries = drawTries,
            poolSize = poolSize,
            refreshEvery = refreshEvery,
            selection = selection,
            warmup = warmup,
        ),
    )
}

// ── Pluck ─────────────────────────────────────────────────────────────

/** Builder for [IgnitorDsl.Pluck], handed to the `configure` lambda of `Ign.pluck(...)`. Knobs: `feedback`, `brightness`, `pickPosition`, `stiffness`, `analog`. */
data class OscPluckBuilder(val node: IgnitorDsl.Pluck) {

    /** Loop feedback per pass; the text is on the script knob `feedback` (`scriptFeedback` in `klangscript-libs`). */
    fun feedback(feedback: IgnitorDsl): OscPluckBuilder = copy(node = node.copy(feedback = feedback))

    /** Initial-burst brightness, the pick hardness; the text is on the script knob `brightness` (`scriptBrightness` in `klangscript-libs`). */
    fun brightness(brightness: IgnitorDsl): OscPluckBuilder = copy(node = node.copy(brightness = brightness))

    /** Relative pick position along the string; the text is on the script knob `pickPosition` (`scriptPickPosition` in `klangscript-libs`). */
    fun pickPosition(pickPosition: IgnitorDsl): OscPluckBuilder = copy(node = node.copy(pickPosition = pickPosition))

    /** String stiffness; the text is on the script knob `stiffness` (`scriptStiffness` in `klangscript-libs`). */
    fun stiffness(stiffness: IgnitorDsl): OscPluckBuilder = copy(node = node.copy(stiffness = stiffness))

    /** How analog the oscillator is; the text is on the script knob `analog` (`scriptAnalog` in `klangscript-libs`). */
    fun analog(character: IgnitorDsl): OscPluckBuilder = copy(node = node.copy(analog = character))
}

// ── SuperPluck ────────────────────────────────────────────────────────

/** Builder for [IgnitorDsl.SuperPluck], handed to the `configure` lambda of `Ign.superpluck(...)`. Knobs: `voices`, `spread`, `feedback`, `brightness`, `pickPosition`, `stiffness`, `analog`, `analogSpread`. */
data class OscSuperPluckBuilder(val node: IgnitorDsl.SuperPluck) {

    /** Number of detuned voices in the stack; the text is on the script knob `voices` (`scriptVoices` in `klangscript-libs`). */
    fun voices(voices: IgnitorDsl): OscSuperPluckBuilder = copy(node = node.copy(voices = voices))

    /** Unison frequency spread between the voices; the text is on the script knob `spread` (`scriptSpread` in `klangscript-libs`). */
    fun spread(spread: IgnitorDsl): OscSuperPluckBuilder = copy(node = node.copy(spread = spread))

    /** Loop feedback per pass; the text is on the script knob `feedback` (`scriptFeedback` in `klangscript-libs`). */
    fun feedback(feedback: IgnitorDsl): OscSuperPluckBuilder = copy(node = node.copy(feedback = feedback))

    /** Initial-burst brightness, the pick hardness; the text is on the script knob `brightness` (`scriptBrightness` in `klangscript-libs`). */
    fun brightness(brightness: IgnitorDsl): OscSuperPluckBuilder = copy(node = node.copy(brightness = brightness))

    /** Relative pick position along the string; the text is on the script knob `pickPosition` (`scriptPickPosition` in `klangscript-libs`). */
    fun pickPosition(pickPosition: IgnitorDsl): OscSuperPluckBuilder = copy(node = node.copy(pickPosition = pickPosition))

    /** String stiffness; the text is on the script knob `stiffness` (`scriptStiffness` in `klangscript-libs`). */
    fun stiffness(stiffness: IgnitorDsl): OscSuperPluckBuilder = copy(node = node.copy(stiffness = stiffness))

    /** How analog the oscillator is; the text is on the script knob `analog` (`scriptAnalog` in `klangscript-libs`). */
    fun analog(character: IgnitorDsl): OscSuperPluckBuilder = copy(node = node.copy(analog = character))

    /** How much the voices drift against each other under `analog`; the text is on the script knob `analogSpread` (`scriptAnalogSpread` in `klangscript-libs`). */
    fun analogSpread(amount: IgnitorDsl): OscSuperPluckBuilder = copy(node = node.copy(analogSpread = amount))
}

// ── Vibrato ─────────────────────────────────────────────────────────────

/** Builder for [IgnitorDsl.Vibrato], handed to the `configure` lambda of `.vibrato(...)`. Knobs: `range`, `phase`. */
data class VibratoBuilder(val node: IgnitorDsl.Vibrato) {

    /** Where the vibrato's swing sits; the text is on the script knob `range` (`scriptRange` in `klangscript-libs`). */
    fun range(from: IgnitorDsl, to: IgnitorDsl): VibratoBuilder = copy(node = node.copy(rangeFrom = from, rangeTo = to))

    /** Where in its cycle the vibrato's LFO runs; the text is on the script knob `phase` (`scriptPhase` in `klangscript-libs`). */
    fun phase(phase: IgnitorDsl): VibratoBuilder = copy(node = node.copy(phase = phase))
}
