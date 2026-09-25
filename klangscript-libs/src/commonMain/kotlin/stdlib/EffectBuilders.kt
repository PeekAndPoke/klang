/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:KlangScript.Library(KlangScriptLibraries.STDLIB)

package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.AdsrCurves
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.LfoShapes
import io.peekandpoke.klang.audio_bridge.band
import io.peekandpoke.klang.audio_bridge.constants.MOD_ENV_CURVE
import io.peekandpoke.klang.audio_bridge.tap
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries

/*
 * Builders for the wrappers that carry secondary knobs: the four filters, the equalizer's
 * sections, the pitch envelope, fm's index envelope, the envelopes' own `adsr` builders, the dry
 * floor of phaser and shimmer, and the tremolo's LFO knobs. Same
 * shape as the oscillator builders (`IgnitorBuilders.kt`): immutable values, one knob = one
 * `@KlangScript.Function` extension. The door keeps the stage's musical inputs, the builder the
 * rest (`/dsl-design` section 2).
 *
 *     Osc.saw().eq(e => e.band(300, 1.0, -4).tap(850, 0.707, 1.7)).lowpass(5000)
 *     Osc.saw().lowpass(800, 1.2, x => x.passes(2).env(24).adsr(0.005, 0.3, 0.2, 0.2, e => e.curves("lin", "exp", "exp")))
 *     Osc.saw().adsr(0.01, 0.3, 0.5, 0.2, e => e.curves("square", "exp", "exp").declick(0.0005))
 *     Osc.saw().phaser(0.3, 0.5, x => x.floor(0.2))
 */

// ── Envelopes ────────────────────────────────────────────────────────────────

/**
 * Builder for the chain `.adsr(attackSec, decaySec, sustainLevel, releaseSec, configure)`, the
 * amplitude envelope. Knobs: `curves`, `declick` (phase 3 step 3c, maintainer, 2026-09-25; they
 * replaced the reach-back chain methods `adsrCurves` and `declickSeconds`). `null` = not named: the
 * node keeps its default. Immutable: every knob returns a new builder.
 */
data class AdsrBuilder(
    val attackCurve: IgnitorDsl? = null,
    val decayCurve: IgnitorDsl? = null,
    val releaseCurve: IgnitorDsl? = null,
    val declickSeconds: IgnitorDsl? = null,
)

/**
 * Builder for the `adsr(attackSec, decaySec, sustainLevel, releaseSec, configure)` INSIDE a
 * modulation envelope's builder: the four filters' cutoff envelope and the pitch envelope. Knob:
 * `curves`. No `declick`: those envelopes have no de-click stage, and a knob that does nothing is
 * not offered. `null` = not named: the node keeps its default.
 */
data class ModAdsrBuilder(
    val attackCurve: IgnitorDsl? = null,
    val decayCurve: IgnitorDsl? = null,
    val releaseCurve: IgnitorDsl? = null,
)

/**
 * One `curves` argument as the curve knob it sets, for an envelope whose default is [fallback]:
 * a NAME through [AdsrCurves] (an unknown one is [fallback]), a number or a slot as it is, and an
 * OMITTED one as [fallback] too. So every `curves` call sets all three stages, and a later call
 * replaces an earlier one completely: the rule `adsrCurves` always had.
 */
internal fun curveKnob(value: IgnitorDslLike?, fallback: AdsrCurve): IgnitorDsl =
    catalogueIndex(value, IgnitorDsl.Constant(AdsrCurves.indexOf(fallback))) { AdsrCurves.indexOf(it, fallback) }

/**
 * Shapes the amplitude envelope's stages: `"exp"` (the default, every amplitude envelope's),
 * `"linear"` (`lin`), `"square"` (`sq`/`quad`), `"cube"` (`cb`), `"scurve"` (`s`/`smooth`/`sigmoid`)
 * or `"invsquare"` (`inv`/`concave`). An omitted argument and an unknown name both mean `"exp"`, so
 * every call sets all three stages. Each argument may also be the curve's index in that list, or a
 * slot carrying it; it is chosen once per note. Every exponential stage bends at the engine's one
 * curvature (`ADSR_EXP_K`).
 */
@KlangScript.Function
fun AdsrBuilder.curves(
    attack: IgnitorDslLike? = null,
    decay: IgnitorDslLike? = null,
    release: IgnitorDslLike? = null,
): AdsrBuilder = copy(
    attackCurve = curveKnob(attack, AdsrCurve.Default),
    decayCurve = curveKnob(decay, AdsrCurve.Default),
    releaseCurve = curveKnob(release, AdsrCurve.Default),
)

/**
 * De-clicks the envelope's gain by [seconds]: a one-pole low-pass that rounds the corners at the
 * segment joins (the attack peak, the gate's end, the cutoff), removing the low-note "plop". `0` is
 * off, which is this envelope's default; a gentle value is about 0.0005 to 0.001.
 */
@KlangScript.Function
fun AdsrBuilder.declick(seconds: IgnitorDslLike): AdsrBuilder = copy(declickSeconds = seconds.toIgnitorDsl())

/**
 * Shapes a modulation envelope's stages (a filter's cutoff envelope, the pitch envelope), with the
 * chain's six curves and their short names. Unshaped stages are `"exp"`, the house curve (decision
 * D3; they were linear before); an omitted argument and an unknown name both mean it, so every
 * call sets all three stages. A number or a slot is the curve's index, chosen once per note.
 */
@KlangScript.Function
fun ModAdsrBuilder.curves(
    attack: IgnitorDslLike? = null,
    decay: IgnitorDslLike? = null,
    release: IgnitorDslLike? = null,
): ModAdsrBuilder = copy(
    attackCurve = curveKnob(attack, MOD_ENV_CURVE),
    decayCurve = curveKnob(decay, MOD_ENV_CURVE),
    releaseCurve = curveKnob(release, MOD_ENV_CURVE),
)

// ── Filters ──────────────────────────────────────────────────────────────────

/**
 * The knobs a filter lambda collected. Not a node: the cutoff envelope's compound fill needs to
 * know which knobs the call NAMED (`null` = not named), and it runs once, after the lambda, in the
 * Kotlin door (`fillFilterEnvelope` behind `IgnitorDsl.lowpass` and its siblings).
 */
data class FilterKnobs(
    val passes: Double = 1.0,
    val analog: IgnitorDsl = IgnitorDsl.Constant(0.0),
    val humanize: Boolean = false,
    val env: IgnitorDsl? = null,
    val attackSec: IgnitorDsl? = null,
    val decaySec: IgnitorDsl? = null,
    val sustainLevel: IgnitorDsl? = null,
    val releaseSec: IgnitorDsl? = null,
    val attackCurve: IgnitorDsl? = null,
    val decayCurve: IgnitorDsl? = null,
    val releaseCurve: IgnitorDsl? = null,
) {
    /**
     * The builder's `adsr`: the four stages AND the curves its own lambda set (unshaped without
     * one). One `adsr` call is the whole envelope, so a later call replaces an earlier one
     * completely, curves included.
     */
    internal fun adsr(
        a: IgnitorDslLike,
        d: IgnitorDslLike,
        s: IgnitorDslLike,
        r: IgnitorDslLike,
        configure: ((ModAdsrBuilder) -> ModAdsrBuilder)?,
    ): FilterKnobs {
        val curves = ModAdsrBuilder().configuredBy("adsr", configure)

        return copy(
            attackSec = a.toIgnitorDsl(), decaySec = d.toIgnitorDsl(), sustainLevel = s.toIgnitorDsl(), releaseSec = r.toIgnitorDsl(),
            attackCurve = curves.attackCurve, decayCurve = curves.decayCurve, releaseCurve = curves.releaseCurve,
        )
    }
}

/**
 * Builder for `.lowpass(freq, q, configure)` and `.highpass(freq, q, configure)`. Knobs: `passes`,
 * `analog`, `humanize`, `env`, `adsr` (whose own lambda carries `curves`). Immutable: every knob
 * returns a new builder.
 */
data class FilterBuilder(val knobs: FilterKnobs = FilterKnobs())

/**
 * Builder for `.bandpass(freq, q, configure)` and `.notch(freq, q, configure)`: the knobs of
 * [FilterBuilder] without `passes`.
 */
data class BandFilterBuilder(val knobs: FilterKnobs = FilterKnobs())

/**
 * Cascade count: `2` = 24 dB/oct, `3` = 36. Rounded, coerced to 1..16. The per-stage q is
 * STAGGERED (Butterworth ladder scaled by `q/0.707`), so at the default q the cascade stays -3 dB at
 * the cutoff; a resonant q COMPOUNDS across stages (`q = 1.0, passes = 2` sits +3 dB there).
 */
@KlangScript.Function
fun FilterBuilder.passes(n: Double): FilterBuilder = copy(knobs = knobs.copy(passes = n))

/**
 * Analog character, 0..10. `0` (the default) is the clean linear filter; higher values engage
 * OB-X-style state-dependent damping that compresses the resonance peak, 1 to 3 is Diva-default
 * warmth. It also scales [humanize]. Every cascade stage gets the full drive.
 */
@KlangScript.Function
fun FilterBuilder.analog(amount: IgnitorDslLike): FilterBuilder = copy(knobs = knobs.copy(analog = amount.toIgnitorDsl()))

/**
 * Per-voice analog character, scaled by `analog`: a cutoff tolerance drawn once per note plus a
 * slow drift lane. Off by default; at `analog = 0` it changes nothing. Accepts `true`/`false` or a
 * number (`1` on, `0` off); anything else reads as ON (coerced, never thrown).
 */
@KlangScript.Function
fun FilterBuilder.humanize(on: Any = true): FilterBuilder = copy(knobs = knobs.copy(humanize = coerceFlag(on)))

/**
 * Cutoff-envelope DEPTH in semitones: `cutoff = freq * 2^(env/12 * envelope)`. `env` and `adsr` are
 * a compound pair: naming either switches the envelope on and the other fills from
 * `audio_bridge/constants/FilterEnvelopeDefaults.kt` (the fill runs after the lambda, once). A
 * non-leaf EXPRESSION here is unreadable at build and switches the envelope OFF; write a number or
 * a slot (`OscSlot.lpf.env`).
 */
@KlangScript.Function
fun FilterBuilder.env(semitones: IgnitorDslLike): FilterBuilder = copy(knobs = knobs.copy(env = semitones.toIgnitorDsl()))

/**
 * The cutoff envelope, ONE call, the chain `adsr`'s shape: attack, decay, sustain (a share of
 * `env`, 0 to 1) and release in seconds, and a lambda on a [ModAdsrBuilder] that shapes the stages
 * with `curves` (unshaped stages are `"exp"`, decision D3):
 * `.lowpass(800, 1.2, x => x.env(24).adsr(0.01, 0.3, 0.2, 0.5, e => e.curves("lin", "exp", "exp")))`.
 * Switches the envelope on; without `env` the depth fills from the constants. A later `adsr` call
 * replaces an earlier one completely, curves included. The release does NOT extend the voice's life.
 *
 * @param configure receives the [ModAdsrBuilder] (knob: `curves`) and returns it.
 */
@KlangScript.Function
fun FilterBuilder.adsr(
    attackSec: IgnitorDslLike,
    decaySec: IgnitorDslLike,
    sustainLevel: IgnitorDslLike,
    releaseSec: IgnitorDslLike,
    configure: ((ModAdsrBuilder) -> ModAdsrBuilder)? = null,
): FilterBuilder = copy(knobs = knobs.adsr(attackSec, decaySec, sustainLevel, releaseSec, configure))

/**
 * The analog SATURATION is not implemented for this tap and the value does not reach it. The value
 * is NOT inert, though: it scales [humanize]'s per-voice tolerance and drift lane, exactly as on the
 * voice strip's `SvfBPF`, so `analog = 0` on a humanized band filter switches the lane off and stops
 * its four build-time rng draws, which shifts every later noise source on that voice.
 */
@KlangScript.Function
fun BandFilterBuilder.analog(amount: IgnitorDslLike): BandFilterBuilder = copy(knobs = knobs.copy(analog = amount.toIgnitorDsl()))

/** Per-voice cutoff tolerance and drift lane, scaled by `analog`; see `FilterBuilder.humanize`. */
@KlangScript.Function
fun BandFilterBuilder.humanize(on: Any = true): BandFilterBuilder = copy(knobs = knobs.copy(humanize = coerceFlag(on)))

/** Cutoff-envelope depth in semitones, compound with `adsr`; see `FilterBuilder.env`. */
@KlangScript.Function
fun BandFilterBuilder.env(semitones: IgnitorDslLike): BandFilterBuilder = copy(knobs = knobs.copy(env = semitones.toIgnitorDsl()))

/**
 * The cutoff envelope, one call with its `curves` lambda; see `FilterBuilder.adsr`.
 *
 * @param configure receives the [ModAdsrBuilder] (knob: `curves`) and returns it.
 */
@KlangScript.Function
fun BandFilterBuilder.adsr(
    attackSec: IgnitorDslLike,
    decaySec: IgnitorDslLike,
    sustainLevel: IgnitorDslLike,
    releaseSec: IgnitorDslLike,
    configure: ((ModAdsrBuilder) -> ModAdsrBuilder)? = null,
): BandFilterBuilder = copy(knobs = knobs.adsr(attackSec, decaySec, sustainLevel, releaseSec, configure))

// ── Pitch envelope ───────────────────────────────────────────────────────────

/** Builder for [IgnitorDsl.PitchEnvelope], handed to the `configure` lambda of `.pitchEnvelope(...)`. Knob: `adsr` (whose own lambda carries `curves`). */
data class PitchEnvelopeBuilder(val node: IgnitorDsl.PitchEnvelope)

/**
 * The pitch envelope, ONE call, the chain `adsr`'s shape: the pitch rises to `semitones` over the
 * attack, falls to the sustain (a share of `semitones`, default 0 = the note) over the decay, holds,
 * and from the gate's end returns to the note over the release. The lambda shapes the stages with
 * `curves`, the filters' rules (unshaped stages are `"exp"`, decision D3):
 * `.pitchEnvelope(24, x => x.adsr(0.001, 0.04, 0, 0, e => e.curves("lin", "exp", "exp")))`.
 * Defaults without this call: `adsr(0.01, 0.1, 0, 0)`, exponential. A later `adsr` call replaces an
 * earlier one completely, curves included. The release does NOT extend the voice's life.
 *
 * @param configure receives the [ModAdsrBuilder] (knob: `curves`) and returns it.
 */
@KlangScript.Function
fun PitchEnvelopeBuilder.adsr(
    attackSec: IgnitorDslLike,
    decaySec: IgnitorDslLike,
    sustainLevel: IgnitorDslLike,
    releaseSec: IgnitorDslLike,
    configure: ((ModAdsrBuilder) -> ModAdsrBuilder)? = null,
): PitchEnvelopeBuilder {
    val curves = ModAdsrBuilder().configuredBy("adsr", configure)
    val defaults = IgnitorDsl.PitchEnvelope(inner = node.inner)

    return copy(
        node = node.copy(
            attackSec = attackSec.toIgnitorDsl(),
            decaySec = decaySec.toIgnitorDsl(),
            sustainLevel = sustainLevel.toIgnitorDsl(),
            releaseSec = releaseSec.toIgnitorDsl(),
            attackCurve = curves.attackCurve ?: defaults.attackCurve,
            decayCurve = curves.decayCurve ?: defaults.decayCurve,
            releaseCurve = curves.releaseCurve ?: defaults.releaseCurve,
        ),
    )
}

// ── FM ───────────────────────────────────────────────────────────────────────

/** Builder for [IgnitorDsl.Fm], handed to the `configure` lambda of `.fm(...)`. Knob: `adsr`. */
data class FmBuilder(val node: IgnitorDsl.Fm)

/**
 * The modulation INDEX envelope: the depth rises over the attack, falls to the sustain (a share of
 * `depth`) over the decay, holds, and releases from the gate's end. Without this call the depth is
 * constant, which is `adsr(0, 0, 1, 0)`. The built-in `sgbell` is `adsr(0.001, 0.5, 0, 0.05)`. Its
 * stages are exponential (decision D3), and it takes no lambda yet: the FM index envelope has no curve
 * support (a filed follow-up adds `curves` when it has), and a knob that does nothing is not offered.
 */
@KlangScript.Function
fun FmBuilder.adsr(
    attackSec: IgnitorDslLike,
    decaySec: IgnitorDslLike,
    sustainLevel: IgnitorDslLike,
    releaseSec: IgnitorDslLike,
): FmBuilder = copy(
    node = node.copy(
        envAttackSec = attackSec.toIgnitorDsl(),
        envDecaySec = decaySec.toIgnitorDsl(),
        envSustainLevel = sustainLevel.toIgnitorDsl(),
        envReleaseSec = releaseSec.toIgnitorDsl(),
    ),
)

// ── Eq ───────────────────────────────────────────────────────────────────────

/**
 * Builder for [IgnitorDsl.Eq], handed to the `configure` lambda of `.eq(...)`. Knobs: `band`,
 * `tap`. Sections are appended in written order. Immutable: every knob returns a new builder.
 */
data class EqBuilder(val node: IgnitorDsl.Eq)

/**
 * Adds a peaking band: [db] decibels of gain at [freq], [q] the width. Bands apply one after
 * another, so two overlapping bands compound.
 *
 * `db = 0` is exactly transparent, negative [db] cuts, and a cut mirrors the same boost exactly,
 * so a `+6` and a `-6` band at the same [freq] and [q] cancel out. The width does NOT move as
 * [db] changes (that is the point of this parameterization): at the default [q] it holds at
 * 1.90 octaves all the way from -6 to -34 dB. Past that the internal width hits its floor and
 * deeper cuts DO start narrowing (1.39 octaves at -40 dB, about 0.45 at -60).
 *
 * The second positional argument is [q], NOT gain: `band(1200, 6)` sets a width of 6 and leaves
 * gain at 0, which is silent. Write `band(freq = 1200, db = 6)` when you mean gain: KlangScript
 * forbids mixing positional and named args, so name them all.
 *
 * All three values are read once per block and shape the filter coefficients, [db] included,
 * so an LFO on [db] zippers just like an LFO on a cutoff. For a smooth gain ride use a VCA
 * instead. See [tap] for the parallel alternative.
 */
@KlangScript.Function
fun EqBuilder.band(freq: IgnitorDslLike, q: IgnitorDslLike = 0.707, db: IgnitorDslLike = 0.0): EqBuilder =
    copy(node = node.band(freq.toIgnitorDsl(), q.toIgnitorDsl(), db.toIgnitorDsl()))

/**
 * Adds a parallel resonant boost: takes the sound going INTO the equalizer, keeps only the band
 * around [freq], scales it by [gain] and mixes it back in. The short way to write
 * `signal.add(signal.bandpass(freq, q).mul(gain))`, in one pass.
 *
 * [gain] is a plain multiplier, not decibels: 1.0 mixes the band back in at full strength, 0 is
 * silent. [q] is the ordinary bandpass width, the same number `.bandpass()` takes. Taps mix with
 * the original sound rather than stacking on each other, so several taps stay predictable where
 * several [band] calls would compound. The engine bandpass is UNITY-peak at [freq], so [q] is a
 * pure WIDTH control: the boost at [freq] is `1 + gain` for ANY q. The default `tap(freq)` is
 * NOT silent: `1 + 1 = 2`, a lift of about 6 dB, where the default `band(freq)` is transparent.
 *
 * Give [gain] a number or an osc-param, not a moving signal: it is re-read only once per block,
 * so a swept tap gain steps instead of gliding. For that, use the chained
 * `signal.add(signal.bandpass(...).mul(lfo))` form, which is smooth.
 */
@KlangScript.Function
fun EqBuilder.tap(freq: IgnitorDslLike, q: IgnitorDslLike = 0.707, gain: IgnitorDslLike = 1.0): EqBuilder =
    copy(node = node.tap(freq.toIgnitorDsl(), q.toIgnitorDsl(), gain.toIgnitorDsl()))

// ── Phaser ───────────────────────────────────────────────────────────────────

/** Builder for [IgnitorDsl.Phaser], handed to the `configure` lambda of `.phaser(...)`. Knob: `floor`. */
data class PhaserBuilder(val node: IgnitorDsl.Phaser)

/**
 * Minimum dry coefficient of the phaser (default 0): the dry signal never drops below this share.
 * A knob on effect builders only, where it cannot meet the round-down `floor()` of a signal.
 */
@KlangScript.Function
fun PhaserBuilder.floor(floor: IgnitorDslLike): PhaserBuilder = copy(node = node.copy(floor = floor.toIgnitorDsl()))

// ── Tremolo ──────────────────────────────────────────────────────────────────

/**
 * Builder for [IgnitorDsl.Tremolo], handed to the `configure` lambda of `.tremolo(...)`. Knobs:
 * `shape`, `skew`, `phase`, the LFO knobs the voice strip's tremolo always had (phase 3 step 3b,
 * 2026-09-25).
 */
data class TremoloBuilder(val node: IgnitorDsl.Tremolo)

/**
 * The LFO's waveform: `"sine"` (default), `"triangle"`, `"square"`, `"sawtooth"` or `"ramp"`, with the
 * oscillator aliases (`"tri"`, `"sqr"`, `"pulse"`, `"saw"`, `"sin"`); an unknown name is sine. [name]
 * may also be the index in that list as a number, or a slot carrying it, and the door is the only
 * way to write one. Chosen once per note, so give it a name, a number or a slot: a moving signal
 * has no value to choose by and reads as sine. A `square` at full depth is silence for half of
 * every cycle, which is the point.
 */
@KlangScript.Function
fun TremoloBuilder.shape(name: IgnitorDslLike): TremoloBuilder =
    copy(node = node.copy(shape = catalogueIndex(name, node.shape, LfoShapes::indexOf)))

/**
 * The LFO's skew, -1 to +1 (default 0, symmetric): positive keeps the level HIGH for more of each
 * cycle, negative LOW, on every shape. Read once per block.
 */
@KlangScript.Function
fun TremoloBuilder.skew(amount: IgnitorDslLike): TremoloBuilder = copy(node = node.copy(skew = amount.toIgnitorDsl()))

/**
 * Where the LFO starts in its own cycle, in cycles (default 0; 0.25 is a quarter cycle, 3.25 the
 * same quarter). Set once, when the note starts, so give it a number or a slot: a moving signal
 * reads as 0.
 */
@KlangScript.Function
fun TremoloBuilder.phase(cycles: IgnitorDslLike): TremoloBuilder = copy(node = node.copy(phase = cycles.toIgnitorDsl()))

// ── Shimmer ──────────────────────────────────────────────────────────────────

/** Builder for [IgnitorDsl.Shimmer], handed to the `configure` lambda of `.shimmer(...)`. Knob: `floor`. */
data class ShimmerBuilder(val node: IgnitorDsl.Shimmer)

/** Minimum dry coefficient of the shimmer (default 0); see `PhaserBuilder.floor`. */
@KlangScript.Function
fun ShimmerBuilder.floor(floor: IgnitorDslLike): ShimmerBuilder = copy(node = node.copy(floor = floor.toIgnitorDsl()))
