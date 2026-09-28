/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

/**
 * Per-stage envelope shape applied to attack / decay / release.
 *
 * The shape is evaluated as `f(p)` where `p` is linear progress 0..1
 * through the stage:
 *  - [Linear]: `p`           — straight ramp
 *  - [Square]: `p*p`         — convex (slow start, fast finish for upward
 *                              ramps; fast initial drop for downward ramps)
 *  - [Cube]:   `p*p*p`       — more pronounced version of [Square]
 *  - [SCurve]: ease-in-out   — `2p²` then `1-2(1-p)²`; zero slope at BOTH
 *                              ends (soft start AND soft finish — no corner)
 *  - [InvSquare]: `p(2-p)`   — concave mirror of [Square]: strong start,
 *                              then eases gently into the endpoint
 *  - [Exponential]: `(eᴷᵖ−1)/(eᴷ−1)` — a true exponential (convex, like
 *                              [Square] but steeper-tailed); for decay/release
 *                              this is the natural analog "fast drop, long tail"
 *
 * For decay and release the ramp uses `(1 - p)` so the level falls from
 * its starting value to its endpoint with a curved tail.
 */
enum class AdsrCurve {
    Linear, Square, Cube, SCurve, InvSquare, Exponential;

    companion object {
        /** THE default curve of every AMPLITUDE envelope stage on every door (maintainer decision,
         *  2026-08-24): unset means [Exponential]. Every amplitude-envelope fallback site
         *  references THIS value; flip it here, it flips everywhere. The MODULATION envelopes
         *  (the Ignitor filter, pitch and FM envelopes, and the voice's own pitch and FM envelopes)
         *  fall back to `MOD_ENV_CURVE` instead (`constants/EnvelopeDefaults.kt`), a decision of
         *  its own (D3), exponential too. */
        val Default = Exponential
    }
}

/**
 * An envelope carried as DATA, not as a slot: a sample's own envelope in its metadata (`SampleMetadata.adsr`, a
 * SoundFont zone's transparent one). Its one reader fills the sample instrument's `adsr.*` slots the pattern left
 * unset with these values (`withSampleEnvelopeDefaults` in audio_be). A voice's envelope travels as `classic()`'s
 * `adsr.*` slots; the merge, resolve and default-envelope API this type carried for the retired voice strip left
 * with the typed `VoiceData.adsr` field (phase 3 step 9).
 */
sealed interface AdsrDef {

    /**
     * Standard 4-stage ADSR envelope (attack / decay / sustain / release)
     * with per-stage shape curves. A null stage or curve is unset.
     */
    @WireName("std")
    data class Std(
        val attack: Double? = null,
        val decay: Double? = null,
        val sustain: Double? = null,
        val release: Double? = null,
        val attackCurve: AdsrCurve? = null,
        val decayCurve: AdsrCurve? = null,
        val releaseCurve: AdsrCurve? = null,
    ) : AdsrDef
}
