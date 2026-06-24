/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge


sealed class FilterDef {
    @WireName("low-pass")
    data class LowPass(
        val cutoffHz: Double,
        val q: Double?,
        val envelope: FilterEnvDef? = null,
    ) : FilterDef()

    @WireName("high-pass")
    data class HighPass(
        val cutoffHz: Double,
        val q: Double?,
        val envelope: FilterEnvDef? = null,
    ) : FilterDef()

    @WireName("band-pass")
    data class BandPass(
        val cutoffHz: Double,
        val q: Double?,
        val envelope: FilterEnvDef? = null,
    ) : FilterDef()

    @WireName("notch")
    data class Notch(
        val cutoffHz: Double,
        val q: Double?,
        val envelope: FilterEnvDef? = null,
    ) : FilterDef()

    @WireName("formant")
    data class Formant(
        val bands: List<Band>,
        /**
         * Dry/wet amount for the formant bank, blended the same way as [Body.mix] (additive
         * "floor + peaks" via the shared mix component). `0` = dry source, higher = more vowel
         * colour on top. The formant bank is level-tamed before this blend so the broadband dry
         * stays audible between formants (a vowel is a source *shaped by* formants, not replaced
         * by them).
         */
        val mix: Double,
    ) : FilterDef() {
        /**
         * One formant band — a single SVF bandpass tuned to a vowel formant peak.
         *
         * **Gain semantic (constant-skirt SVF convention):** the actual peak gain at
         * `freq` is `Q · 10^(db/20)`. The user-facing `db` is *additional* gain on top
         * of the BPF's intrinsic Q peak — a band with `db = 0, q = 10` produces
         * **+20 dB** at `freq`, not 0 dB. F1 is conventionally `db = 0`; upper formants
         * use negative dB to compensate for their own Q-driven peak.
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
     * 1. **Additive body amount** ([mix]) — the resonances are *added on top of* the full dry
     *    source (`out = dry + wet·mix`), so no broadband content is lost. [Formant] is wet-only
     *    and would strip the spectrum. `mix = 0` is the untouched source; values > 1 drive the
     *    resonances harder.
     * 2. **Fixed Hz centers that do not track the played note** — different notes get
     *    emphasized at different points in their harmonic series, breaking the spectral
     *    "lockstep" that reads as plastic. (Already how SVF centers work; called out here
     *    because it is the whole point of the effect.)
     *
     * Bands are resolved from a named material (`wood`, `tube`, `glass`, `membrane`) at the
     * sprudel DSL layer; this contract carries only the already-resolved modes + mix.
     */
    @WireName("body")
    data class Body(
        val bands: List<Mode>,
        val mix: Double,
    ) : FilterDef() {
        /**
         * One body mode — a single SVF bandpass tuned to a resonance of the body.
         *
         * **Gain semantic (constant-skirt SVF convention):** identical to [Formant.Band] —
         * the actual peak gain at `freq` is `Q · 10^(db/20)`; `db` is *additional* gain on
         * top of the bandpass's intrinsic Q peak. Material tables conventionally set the
         * lowest mode to `db = 0` and use negative dB for upper modes.
         */
        data class Mode(
            val freq: Double,
            val db: Double,
            val q: Double,
        )
    }
}
