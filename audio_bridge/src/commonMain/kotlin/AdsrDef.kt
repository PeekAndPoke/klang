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
enum class AdsrCurve { Linear, Square, Cube, SCurve, InvSquare, Exponential }

sealed interface AdsrDef {

    /** Merges this envelope with a fallback. Values in `this` take precedence over `other`. */
    fun mergeWith(other: AdsrDef?): AdsrDef

    /** Resolves to non-nullable values using provided defaults as final fallback. */
    fun resolve(defaults: AdsrDef = Std.defaultSynth): Resolved

    /**
     * Standard 4-stage ADSR envelope (attack / decay / sustain / release)
     * with per-stage shape curves.
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
    ) : AdsrDef {

        override fun mergeWith(other: AdsrDef?): AdsrDef = when (other) {
            null -> this
            is Std -> Std(
                attack = attack ?: other.attack,
                decay = decay ?: other.decay,
                sustain = sustain ?: other.sustain,
                release = release ?: other.release,
                attackCurve = attackCurve ?: other.attackCurve,
                decayCurve = decayCurve ?: other.decayCurve,
                releaseCurve = releaseCurve ?: other.releaseCurve,
            )
        }

        override fun resolve(defaults: AdsrDef): Resolved {
            val d = defaults as? Std ?: defaultSynth
            return Resolved(
                attack = attack ?: d.attack ?: 0.01,
                decay = decay ?: d.decay ?: 0.1,
                sustain = sustain ?: d.sustain ?: 1.0,
                release = release ?: d.release ?: 0.1,
                attackCurve = attackCurve ?: d.attackCurve ?: AdsrCurve.Exponential,
                decayCurve = decayCurve ?: d.decayCurve ?: AdsrCurve.Exponential,
                releaseCurve = releaseCurve ?: d.releaseCurve ?: AdsrCurve.Exponential,
            )
        }

        companion object {
            val empty = Std()

            /** Standard Synth defaults (Organ-like) */
            val defaultSynth = Std(
                attack = 0.01,
                decay = 0.1,
                sustain = 1.0,
                release = 0.05,
                attackCurve = AdsrCurve.Exponential,
                decayCurve = AdsrCurve.Exponential,
                releaseCurve = AdsrCurve.Exponential,
            )
        }
    }

    /**
     * Resolved ADSR — all values non-null, ready for the audio engine.
     */
    data class Resolved(
        val attack: Double,
        val decay: Double,
        val sustain: Double,
        val release: Double,
        val attackCurve: AdsrCurve,
        val decayCurve: AdsrCurve,
        val releaseCurve: AdsrCurve,
    )

    companion object {
        /** Empty envelope (all nulls). */
        val empty: AdsrDef = Std.empty

        /** Standard Synth defaults (Organ-like). */
        val defaultSynth: AdsrDef = Std.defaultSynth
    }
}
