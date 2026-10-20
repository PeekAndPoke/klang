/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

import io.peekandpoke.klang.audio_bridge.constants.EQ_BAND_DB
import io.peekandpoke.klang.audio_bridge.constants.EQ_TAP_GAIN
import io.peekandpoke.klang.audio_bridge.constants.FILTER_PASSES
import io.peekandpoke.klang.audio_bridge.constants.FILTER_Q
import io.peekandpoke.klang.audio_bridge.constants.MOD_ENV_CURVE

/*
 * The effect builders: what an effect door hands to its `configure` lambda, ONE set for both doors
 * (KLANG-00314 (kotlin typed doors): rollout S3a the four filters and the eq, S3b the amplitude envelope, the pitch
 * envelope and the FM, S3c the phaser, the shimmer and the tremolo; the oscillators' and the vibrato's builders are
 * in `IgnitorBuilders.kt`).
 *
 *     Ign.saw().lowpass(freq = 800.ign, q = 1.2.ign) { it.env(24.ign).adsr(0.005.ign, 0.3.ign, 0.2.ign, 0.2.ign) }   // Kotlin
 *     Ignitor.saw().lowpass(800, 1.2, x => x.env(24).adsr(0.005, 0.3, 0.2, 0.2))                         // KlangScript
 *
 * The same rules as there: immutable values, every knob a TYPED class member returning a new builder; the KlangScript
 * door adds `internal`, renamed `@KlangScript.Function` variants in `klangscript-libs` (`scriptEnv`, the script word in
 * `name`) that convert their `Any` arguments and call these members; the user-facing text lives on those variants.
 */

// ── Amplitude envelope ──────────────────────────────────────────────────────────

/**
 * Builder for [IgnitorDsl.Adsr], handed to the `configure` lambda of `.adsr(attack, decay, sustain, release,
 * configure)`, the amplitude envelope. Knobs: `curves`, `declick`. A knob that is not named keeps the node's default.
 * Immutable: every knob returns a new builder.
 */
data class AdsrBuilder(val node: IgnitorDsl.Adsr) {
    /**
     * Shapes the three stages, each a curve's [AdsrCurves] index (a constant or a slot); an omitted one is
     * [AdsrCurve.Default], so every call sets all three stages. The text is on the script knob `curves` (`scriptCurves`
     * in `klangscript-libs`).
     */
    fun curves(
        attack: IgnitorDsl = AdsrCurves.knob(AdsrCurve.Default),
        decay: IgnitorDsl = AdsrCurves.knob(AdsrCurve.Default),
        release: IgnitorDsl = AdsrCurves.knob(AdsrCurve.Default),
    ): AdsrBuilder = copy(node = node.copy(attackCurve = attack, decayCurve = decay, releaseCurve = release))

    /**
     * [curves] by NAME (`it.curves("linear", "exp", "exp")`), through the lookup the script knob uses
     * ([AdsrCurves.indexOf]): an unknown name is [AdsrCurve.Default], coerced, never thrown.
     */
    fun curves(
        attack: String,
        decay: String = AdsrCurves.names[AdsrCurve.Default.ordinal],
        release: String = AdsrCurves.names[AdsrCurve.Default.ordinal],
    ): AdsrBuilder = curves(
        attack = AdsrCurves.indexOf(attack, AdsrCurve.Default).ign,
        decay = AdsrCurves.indexOf(decay, AdsrCurve.Default).ign,
        release = AdsrCurves.indexOf(release, AdsrCurve.Default).ign,
    )

    /** De-clicks the envelope's gain by [seconds] (0 = off); the text is on the script knob `declick` (`scriptDeclick` in `klangscript-libs`). */
    fun declick(seconds: IgnitorDsl): AdsrBuilder = copy(node = node.copy(declick = seconds))
}

// ── Modulation envelope ─────────────────────────────────────────────────────────

/**
 * Builder for the `adsr(attack, decay, sustain, release, configure)` INSIDE a modulation envelope's builder: the four
 * filters' cutoff envelope and the pitch envelope. Knob: `curves`. `null` = not named: the node keeps its default.
 */
data class ModAdsrBuilder(
    val attackCurve: IgnitorDsl? = null,
    val decayCurve: IgnitorDsl? = null,
    val releaseCurve: IgnitorDsl? = null,
) {
    /**
     * Shapes the three stages, each a curve's [AdsrCurves] index (a constant or a slot); an omitted one is
     * `MOD_ENV_CURVE`, so every call sets all three stages. The text is on the script knob `curves` (`scriptCurves` in
     * `klangscript-libs`).
     */
    fun curves(
        attack: IgnitorDsl = AdsrCurves.knob(MOD_ENV_CURVE),
        decay: IgnitorDsl = AdsrCurves.knob(MOD_ENV_CURVE),
        release: IgnitorDsl = AdsrCurves.knob(MOD_ENV_CURVE),
    ): ModAdsrBuilder = copy(attackCurve = attack, decayCurve = decay, releaseCurve = release)

    /**
     * [curves] by NAME (`it.curves("lin", "exp", "exp")`), through the lookup the script knob uses
     * ([AdsrCurves.indexOf]): an unknown name is `MOD_ENV_CURVE`, coerced, never thrown.
     */
    fun curves(
        attack: String,
        decay: String = AdsrCurves.names[MOD_ENV_CURVE.ordinal],
        release: String = AdsrCurves.names[MOD_ENV_CURVE.ordinal],
    ): ModAdsrBuilder = curves(
        attack = AdsrCurves.indexOf(attack, MOD_ENV_CURVE).ign,
        decay = AdsrCurves.indexOf(decay, MOD_ENV_CURVE).ign,
        release = AdsrCurves.indexOf(release, MOD_ENV_CURVE).ign,
    )
}

// ── Filters ──────────────────────────────────────────────────────────────────

/**
 * The knobs a filter lambda collected. Not a node: the cutoff envelope's compound fill needs to know which knobs the
 * call NAMED (`null` = not named), and it runs once, after the lambda, in the Kotlin door ([fillFilterEnvelope] behind
 * [lowpass] and its siblings), which the script door calls too.
 */
data class FilterKnobs(
    val passes: IgnitorDsl = FILTER_PASSES.ign,
    val analog: IgnitorDsl = IgnitorDsl.Constant(0.0),
    val humanize: Boolean = false,
    val env: IgnitorDsl? = null,
    val attack: IgnitorDsl? = null,
    val decay: IgnitorDsl? = null,
    val sustain: IgnitorDsl? = null,
    val release: IgnitorDsl? = null,
    val attackCurve: IgnitorDsl? = null,
    val decayCurve: IgnitorDsl? = null,
    val releaseCurve: IgnitorDsl? = null,
) {
    /**
     * The builder's `adsr`: the four stages AND the curves its own lambda set (unshaped without one). One `adsr` call
     * is the whole envelope, so a later call replaces an earlier one completely, curves included.
     */
    internal fun adsr(a: IgnitorDsl, d: IgnitorDsl, s: IgnitorDsl, r: IgnitorDsl, curves: ModAdsrBuilder): FilterKnobs = copy(
        attack = a, decay = d, sustain = s, release = r,
        attackCurve = curves.attackCurve, decayCurve = curves.decayCurve, releaseCurve = curves.releaseCurve,
    )

    /** The five envelope knobs after the compound fill: [fillFilterEnvelope], once per door call. */
    internal fun envelope(): FilterEnvelopeKnobs =
        fillFilterEnvelope(env = env, attack = attack, decay = decay, sustain = sustain, release = release)
}

/**
 * Builder for `.lowpass(freq, q, configure)` and `.highpass(freq, q, configure)`. Knobs: `passes`, `analog`,
 * `humanize`, `env`, `adsr` (whose own lambda carries `curves`). Immutable: every knob returns a new builder.
 */
data class FilterBuilder(val knobs: FilterKnobs = FilterKnobs()) {

    /** Cascade count, a number or a slot read at voice build (rounded, coerced to 1..16); the text is on the script knob `passes` (`scriptPasses` in `klangscript-libs`). */
    fun passes(n: IgnitorDsl): FilterBuilder = copy(knobs = knobs.copy(passes = n))

    /** Analog character, 0..10; the text is on the script knob `analog` (`scriptAnalog` in `klangscript-libs`). */
    fun analog(character: IgnitorDsl): FilterBuilder = copy(knobs = knobs.copy(analog = character))

    /** Per-voice cutoff tolerance and drift lane, scaled by `analog`; the text is on the script knob `humanize` (`scriptHumanize` in `klangscript-libs`). */
    fun humanize(on: Boolean = true): FilterBuilder = copy(knobs = knobs.copy(humanize = on))

    /** Cutoff-envelope depth in semitones, compound with [adsr]; the text is on the script knob `env` (`scriptEnv` in `klangscript-libs`). */
    fun env(semitones: IgnitorDsl): FilterBuilder = copy(knobs = knobs.copy(env = semitones))

    /** The cutoff envelope in ONE call, its lambda shaping the stages; the text is on the script knob `adsr` (`scriptAdsr` in `klangscript-libs`). */
    fun adsr(
        attack: IgnitorDsl,
        decay: IgnitorDsl,
        sustain: IgnitorDsl,
        release: IgnitorDsl,
        configure: ((ModAdsrBuilder) -> ModAdsrBuilder)? = null,
    ): FilterBuilder = copy(knobs = knobs.adsr(attack, decay, sustain, release, ModAdsrBuilder().configured(configure)))
}

/**
 * Builder for `.bandpass(freq, q, configure)` and `.notch(freq, q, configure)`: the knobs of [FilterBuilder] without
 * `passes`.
 */
data class BandFilterBuilder(val knobs: FilterKnobs = FilterKnobs()) {

    /** Scales [humanize] only (the saturation is not implemented for this tap); the text is on the script knob `analog` (`scriptAnalog` in `klangscript-libs`). */
    fun analog(character: IgnitorDsl): BandFilterBuilder = copy(knobs = knobs.copy(analog = character))

    /** Per-voice cutoff tolerance and drift lane, scaled by `analog`; the text is on the script knob `humanize` (`scriptHumanize` in `klangscript-libs`). */
    fun humanize(on: Boolean = true): BandFilterBuilder = copy(knobs = knobs.copy(humanize = on))

    /** Cutoff-envelope depth in semitones, compound with [adsr]; the text is on the script knob `env` (`scriptEnv` in `klangscript-libs`). */
    fun env(semitones: IgnitorDsl): BandFilterBuilder = copy(knobs = knobs.copy(env = semitones))

    /** The cutoff envelope in ONE call, its lambda shaping the stages; the text is on the script knob `adsr` (`scriptAdsr` in `klangscript-libs`). */
    fun adsr(
        attack: IgnitorDsl,
        decay: IgnitorDsl,
        sustain: IgnitorDsl,
        release: IgnitorDsl,
        configure: ((ModAdsrBuilder) -> ModAdsrBuilder)? = null,
    ): BandFilterBuilder = copy(knobs = knobs.adsr(attack, decay, sustain, release, ModAdsrBuilder().configured(configure)))
}

// ── Pitch envelope ───────────────────────────────────────────────────────────

/**
 * Builder for [IgnitorDsl.PitchEnvelope], handed to the `configure` lambda of `.pitchEnvelope(semitones, configure)`.
 * Knob: `adsr` (whose own lambda carries `curves`). Immutable: every knob returns a new builder.
 */
data class PitchEnvelopeBuilder(val node: IgnitorDsl.PitchEnvelope) {
    /**
     * The four stages in ONE call, its lambda shaping them on a [ModAdsrBuilder]; the text is on the script knob `adsr`
     * (`scriptAdsr` in `klangscript-libs`).
     *
     * THE ONE HOME of the pitch envelope's default fill, for both doors: a curve the lambda did not shape is the node's
     * default (`MOD_ENV_CURVE`), read off a fresh [IgnitorDsl.PitchEnvelope], never the curve an earlier `adsr` call
     * set. So one `adsr` call is the whole envelope, and a later call replaces an earlier one completely, curves
     * included. The depth is the door's input, so there is no companion to fill (`/dsl-design` section 4).
     */
    fun adsr(
        attack: IgnitorDsl,
        decay: IgnitorDsl,
        sustain: IgnitorDsl,
        release: IgnitorDsl,
        configure: ((ModAdsrBuilder) -> ModAdsrBuilder)? = null,
    ): PitchEnvelopeBuilder {
        val curves = ModAdsrBuilder().configured(configure)
        val defaults = IgnitorDsl.PitchEnvelope(inner = node.inner)

        return copy(
            node = node.copy(
                attack = attack,
                decay = decay,
                sustain = sustain,
                release = release,
                attackCurve = curves.attackCurve ?: defaults.attackCurve,
                decayCurve = curves.decayCurve ?: defaults.decayCurve,
                releaseCurve = curves.releaseCurve ?: defaults.releaseCurve,
            ),
        )
    }
}

// ── FM ───────────────────────────────────────────────────────────────────────

/** Builder for [IgnitorDsl.Fm], handed to the `configure` lambda of `.fm(modulator, ratio, depth, configure)`. Knob: `adsr`. */
data class FmBuilder(val node: IgnitorDsl.Fm) {
    /**
     * The modulation INDEX envelope, the four stages in one call (no lambda: the FM index envelope has no curves yet);
     * the text is on the script knob `adsr` (`scriptAdsr` in `klangscript-libs`).
     */
    fun adsr(attack: IgnitorDsl, decay: IgnitorDsl, sustain: IgnitorDsl, release: IgnitorDsl): FmBuilder =
        copy(node = node.copy(attack = attack, decay = decay, sustain = sustain, release = release))
}

// ── Phaser, shimmer ──────────────────────────────────────────────────────────

/** Builder for [IgnitorDsl.Phaser], handed to the `configure` lambda of `.phaser(...)`. Knob: `floor`. */
data class PhaserBuilder(val node: IgnitorDsl.Phaser) {
    /** Minimum dry coefficient (default 0); the text is on the script knob `floor` (`scriptFloor` in `klangscript-libs`). */
    fun floor(floor: IgnitorDsl): PhaserBuilder = copy(node = node.copy(floor = floor))
}

/** Builder for [IgnitorDsl.Shimmer], handed to the `configure` lambda of `.shimmer(...)`. Knob: `floor`. */
data class ShimmerBuilder(val node: IgnitorDsl.Shimmer) {
    /** Minimum dry coefficient (default 0); the text is on the script knob `floor` (`scriptFloor` in `klangscript-libs`). */
    fun floor(floor: IgnitorDsl): ShimmerBuilder = copy(node = node.copy(floor = floor))
}

// ── Tremolo ──────────────────────────────────────────────────────────────────

/** Builder for [IgnitorDsl.Tremolo], handed to the `configure` lambda of `.tremolo(...)`. Knobs: `shape`, `range`. */
data class TremoloBuilder(val node: IgnitorDsl.Tremolo) {
    /** Where the tremolo's swing sits; the text is on the script knob `range` (`scriptRange` in `klangscript-libs`). */
    fun range(from: IgnitorDsl, to: IgnitorDsl): TremoloBuilder = copy(node = node.copy(rangeFrom = from, rangeTo = to))

    /**
     * The LFO's waveform as its [LfoShapes] index (a constant or a slot); the text is on the script knob `shape`
     * (`scriptShape` in `klangscript-libs`).
     */
    fun shape(index: IgnitorDsl): TremoloBuilder = copy(node = node.copy(shape = index))

    /**
     * [shape] by NAME (`it.shape("square")`), through the lookup the script knob uses ([LfoShapes.indexOf]): an
     * unknown name is the sine, coerced, never thrown.
     */
    fun shape(name: String): TremoloBuilder = shape(index = LfoShapes.indexOf(name).ign)
}

// ── Eq ───────────────────────────────────────────────────────────────────────

/*
 * The eq is ONE concept with two value domains (decision A of the typed-doors rollout, `/dsl-design` section 3, point
 * 6): the same words (`band`, `tap`) on a voice builder, whose knobs are [IgnitorDsl], and on a Katalyst builder,
 * whose knobs are [KatalystKnobDsl]. Both append the same [IgnitorDsl.EqSection]s, so a section means the same on a
 * voice and on an orbit; the Katalyst one maps each knob onto the section's Ignitor leaf through ONE helper,
 * [eqSectionKnobOrNull], which is also where the refusal of an expression (Q49) is decided.
 */

/**
 * Builder for [IgnitorDsl.Eq], handed to the `configure` lambda of `.eq(...)` on a sound. Knobs: `band`, `tap`.
 * Sections are appended in written order. Immutable: every knob returns a new builder.
 */
data class EqBuilder(val node: IgnitorDsl.Eq) {

    /** Appends a peaking band, [db] decibels at [freq], [q] the width; the text is on the script knob `band` (`scriptBand` in `klangscript-libs`). */
    fun band(freq: IgnitorDsl, q: IgnitorDsl = FILTER_Q.ign, db: IgnitorDsl = EQ_BAND_DB.ign): EqBuilder =
        copy(node = node.copy(sections = node.sections + IgnitorDsl.EqSection.Bell(freq = freq, q = q, db = db)))

    /** Appends a parallel resonant boost of the eq's input, [gain] linear; the text is on the script knob `tap` (`scriptTap` in `klangscript-libs`). */
    fun tap(freq: IgnitorDsl, q: IgnitorDsl = FILTER_Q.ign, gain: IgnitorDsl = EQ_TAP_GAIN.ign): EqBuilder =
        copy(node = node.copy(sections = node.sections + IgnitorDsl.EqSection.RawTap(freq = freq, q = q, gain = gain)))
}

/**
 * Builder for the Katalyst `eq` stage's sections, handed to the `configure` lambda of `k.eq(...)` on a chain. Knobs:
 * `band`, `tap`, the voice [EqBuilder]'s words, defaults and sections. Each knob takes a number (`300.kat`) or a
 * Katalyst param, which the engine reads from the orbit's `katp` state; a Katalyst EXPRESSION (`Kat.sine(...)`,
 * `Kat.param(...).times(2.kat)`) is refused at build time with an [IllegalArgumentException] naming the knob and the
 * parameter (maintainer, Q49 of KLANG-00314 (kotlin typed doors)): a section reads a number or a slot, never a
 * moving knob (the recorded asymmetry on [eqSectionKnobOrNull]).
 */
data class KatalystEqBuilder(val sections: List<IgnitorDsl.EqSection> = emptyList()) {

    /** Appends a peaking band; the voice [EqBuilder.band]'s section, the text on the script knob `band` (`scriptBand` in `klangscript-libs`). */
    fun band(freq: KatalystKnobDsl, q: KatalystKnobDsl = FILTER_Q.kat, db: KatalystKnobDsl = EQ_BAND_DB.kat): KatalystEqBuilder =
        copy(
            sections = sections + IgnitorDsl.EqSection.Bell(
                freq = freq.toEqSectionKnob("band", "freq"),
                q = q.toEqSectionKnob("band", "q"),
                db = db.toEqSectionKnob("band", "db"),
            ),
        )

    /** Appends a parallel resonant boost; the voice [EqBuilder.tap]'s section, the text on the script knob `tap` (`scriptTap` in `klangscript-libs`). */
    fun tap(freq: KatalystKnobDsl, q: KatalystKnobDsl = FILTER_Q.kat, gain: KatalystKnobDsl = EQ_TAP_GAIN.kat): KatalystEqBuilder =
        copy(
            sections = sections + IgnitorDsl.EqSection.RawTap(
                freq = freq.toEqSectionKnob("tap", "freq"),
                q = q.toEqSectionKnob("tap", "q"),
                gain = gain.toEqSectionKnob("tap", "gain"),
            ),
        )
}

/**
 * THE ONE HELPER of the Katalyst eq builder and its script pre-check: a Katalyst knob as the [IgnitorDsl] leaf an eq section carries,
 * or `null` when it cannot be one. The residue of mixing the two DSLs that decision Q2 of
 * KLANG-00302 (katalyst knob expressions) keeps (maintainer, 2026-10-11, E2: "keep shared,
 * slots only"): the sections are [IgnitorDsl.EqSection], the one section law shared with the voice eq and with `bands`,
 * so a bus section's knob is an Ignitor leaf. A [KatalystKnobDsl.Constant] becomes an [IgnitorDsl.Constant] and a
 * [KatalystKnobDsl.Param] the [IgnitorDsl.Param] of the same name and default, which the engine reads from the
 * orbit's `katp` state (`KatalystChainBuilder.eqKnob`). Recorded asymmetry: a Katalyst expression has no section leaf
 * (`null`), where on a stage knob it listens; a Katalyst section hierarchy (E1) is its own task, for the day a song
 * wants a swept bus EQ. The Kotlin door refuses the `null` at build time ([toEqSectionKnob]); the script door
 * pre-checks it and raises a script error with the script's own words.
 */
fun KatalystKnobDsl.eqSectionKnobOrNull(): IgnitorDsl? = when (this) {
    is KatalystKnobDsl.Constant -> IgnitorDsl.Constant(value)
    is KatalystKnobDsl.Param -> IgnitorDsl.Param(name = name, default = default, description = description)

    is KatalystKnobDsl.Plus, is KatalystKnobDsl.Minus, is KatalystKnobDsl.Times, is KatalystKnobDsl.Div,
    is KatalystKnobDsl.Pow, is KatalystKnobDsl.Min, is KatalystKnobDsl.Max, is KatalystKnobDsl.Neg,
    is KatalystKnobDsl.Abs, is KatalystKnobDsl.Range,
    is KatalystKnobDsl.Sine, is KatalystKnobDsl.Saw, is KatalystKnobDsl.Ramp, is KatalystKnobDsl.Tri,
    is KatalystKnobDsl.Square, is KatalystKnobDsl.PerlinNoise, is KatalystKnobDsl.BerlinNoise,
    -> null
}

/**
 * [eqSectionKnobOrNull], refusing an expression (Q49): an [IllegalArgumentException] at build time naming the knob
 * and the [param] (`Katalyst eq band(q = ...)`). A refusal the maintainer decided, of the wrong KIND of argument, not
 * a value out of range: the Motor stays raw and no clamp could make an expression a section's number.
 */
internal fun KatalystKnobDsl.toEqSectionKnob(knob: String, param: String): IgnitorDsl =
    eqSectionKnobOrNull() ?: throw IllegalArgumentException(
        "Katalyst eq $knob($param = ...) takes a number or a Kat.param; got a Kat expression",
    )
