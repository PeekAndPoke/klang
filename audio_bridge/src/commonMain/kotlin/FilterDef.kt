/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

import kotlin.math.roundToInt


/**
 * The orbit's resonator band carriers: a vowel's formant bands ([Formant]) and a body's modes ([Body]), resolved from
 * their tables (`VowelBands`, `BodyMaterials`) by the orbit's slots (`KatalystSlots`). The voice filter variants
 * (low-pass, high-pass, band-pass, notch) left with the typed `VoiceData.filters` field in phase 3 step 9: a voice
 * filter is a `classic()` slot now.
 */
sealed class FilterDef {
    data class Formant(
        val bands: List<Band>,
        /**
         * Wet/dry balance for the formant bank, blended the same way as [Body.mix]: the shared
         * C4 wet/dry law, correlated branch, with a floored dry (`VOWEL_FLOOR`). The mix lives
         * on [0, 1] (values above 1 behave as 1). `0` = dry source, `1` = formants at full
         * level over the floored dry. The formant bank is level-tamed before this blend so the
         * broadband dry stays audible between formants (a vowel is a source *shaped by*
         * formants, not replaced by them).
         */
        val mix: Double,
        /**
         * Broadband dry floor for the blend (the `vowel(floor = ...)` door). `null` = engine default
         * (`VOWEL_FLOOR`). Lower = the formants dominate a thinner source (more "vowel"); higher =
         * more untouched source between formants.
         */
        val floor: Double? = null,
    ) : FilterDef() {
        /**
         * One formant band — a single SVF bandpass tuned to a vowel formant peak.
         *
         * **Gain semantic (legacy Q-peak convention, preserved by a fold):** the actual
         * peak gain at `freq` is `Q · 10^(db/20)`. The engine bandpass is UNITY-peak since
         * C2 of the filter unification; the engine's vowel mapping folds the legacy Q peak into
         * its band gains so the shipped vowel tables keep this convention exactly — do NOT
         * "clean up" that fold without rewriting every table. A band with `db = 0, q = 10`
         * produces **+20 dB** at `freq`. F1 is conventionally `db = 0`; upper formants use
         * negative dB to compensate for their own Q-driven peak.
         *
         * **Q range**: SVF accepts `q ∈ [0.1, 200.0]`. Vowel tables typically use 60–130.
         */
        data class Band(
            val freq: Double,
            val db: Double,
            val q: Double,
        )
    }

    /**
     * Body resonator — a parallel bank of fixed-frequency resonant bandpasses mixed on top
     * of the dry source, to give a voice a resonating "body" instead of a synthetic/plastic
     * tone. Same parallel-SVF-bandpass core as [Formant], with two differences that make it
     * a *body* rather than a vowel:
     *
     * 1. **Floored body amount** ([mix]) — the resonances blend on top of a dry that never
     *    drops below its physical floor (the shared C4 wet/dry law, correlated branch, with
     *    `BODY_FLOOR`), so broadband content is never lost. [Formant] is wet-only and would
     *    strip the spectrum. `mix = 0` is the untouched source; the mix lives on [0, 1]
     *    (values above 1 behave as 1; the old raw extension is a deleted capability).
     * 2. **Fixed Hz centers that do not track the played note** — different notes get
     *    emphasized at different points in their harmonic series, breaking the spectral
     *    "lockstep" that reads as plastic. (Already how SVF centers work; called out here
     *    because it is the whole point of the effect.)
     *
     * Bands are resolved from a named material (`wood`, `cedar`, `tube`, `glass`, `membrane`,
     * `brass`) through [BodyMaterials]; this contract carries only the already-resolved modes + mix.
     */
    data class Body(
        val bands: List<Mode>,
        val mix: Double,
        /**
         * Broadband dry floor for the blend (the `body(floor = ...)` door). `null` = engine default
         * (`BODY_FLOOR`). Lower = more audible body (resonances over less dry); higher = subtler
         * colour. Independent of [mix] (which lives on [0, 1] since C4).
         */
        val floor: Double? = null,
    ) : FilterDef() {
        /**
         * One body mode — a single SVF bandpass tuned to a resonance of the body.
         *
         * **Gain semantic (unity-peak):** UNLIKE [Formant.Band], the peak gain at `freq`
         * is `10^(db/20)`: the engine's body bank always normalised the Q peak away (pre-C2 via an
         * explicit 1/Q, since C2 natively via the unity-peak SVF), so `db` IS the peak.
         * Material tables conventionally set the lowest mode to `db = 0` and use negative
         * dB for upper modes.
         */
        data class Mode(
            val freq: Double,
            val db: Double,
            val q: Double,
        )
    }
}

/**
 * Upper bound for the `passes` cascade count (C5). 16 stages is 192 dB/oct — far past any
 * musical use; the bound exists because `passes` is a RESOURCE count, not a tone knob:
 * a live-typed `lpf(passes = 1e9)` would otherwise allocate a billion filter stages inside a note-on
 * on the render thread. Nothing about the sound of a reachable value is clamped.
 */
const val FILTER_MAX_PASSES = 16

/**
 * The ONE place a `passes` value is coerced (parameter-parity rule: conversions live in a
 * single place). Every consumer funnels through here — the sprudel voice-data builder, the
 * ignitor runtime, the graph optimizer and the engine's q-ladder — so a value that is legal
 * on one door cannot be illegal on another.
 *
 * Two callers coerce while CONSTRUCTING the value rather than while consuming it: sprudel's
 * `classicSlotParams` (which writes the `lpf.passes` / `hpf.passes` slot the wire carries) and, on the ignitor door, the
 * KlangScript stdlib builder — the latter because its generated thunk would truncate a `Double`
 * (`0.3 * 10` is 2.9999999999999996, and every KlangScript number is a double). Coercing
 * early only normalises the value that gets stored and encoded; every consumer re-coerces
 * idempotently, so the two timings cannot disagree about the rendered filter.
 */
fun coercePasses(passes: Int): Int = passes.coerceIn(1, FILTER_MAX_PASSES)

/**
 * The pattern-value door onto [coercePasses]. Sprudel carries every control value as a
 * `Double`, and pattern arithmetic lands on things like `2.9999999996` — truncating there
 * silently drops a cascade stage, so the value is ROUNDED. `roundToInt()` throws on NaN, and
 * this runs on the render thread, hence the explicit guard rather than a try.
 *
 * A NON-FINITE value is one pass, the house rule that a non-finite wire number reads as unset
 * (`/dsl-design` section 4). Until phase 3 step 5 only the NaN was guarded and `+Infinity`
 * saturated `roundToInt()` to the 16-pass ceiling; the filters' `passes` knob (read at voice
 * build for `classic()`'s `lpf.passes` / `hpf.passes` slots) reads through here too, so the
 * pattern side and the tree agree on every value.
 */
fun coercePasses(passes: Double): Int {
    if (!passes.isFinite()) { // NaN-guard: non-finite reads as unset, one pass
        return 1
    }

    return coercePasses(passes.roundToInt())
}
