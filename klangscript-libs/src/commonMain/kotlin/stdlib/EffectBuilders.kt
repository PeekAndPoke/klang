/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:KlangScript.Library(KlangScriptLibraries.STDLIB)

package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.band
import io.peekandpoke.klang.audio_bridge.tap
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries

/*
 * Builders for the effect wrappers that carry their own knobs: the equalizer's sections and the
 * wet knob of phaser and shimmer. Same shape as the oscillator builders (`IgnitorBuilders.kt`):
 * immutable value wrappers, one knob = one `@KlangScript.Function` extension, the Kotlin door
 * and the script door in one place.
 *
 *     Osc.saw().eq(e => e.band(300, 1.0, -4).tap(850, 0.707, 1.7)).lowpass(5000)
 *     Osc.saw().phaser(0.3, x => x.wet(0.3))
 */

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

/** Builder for [IgnitorDsl.Phaser], handed to the `configure` lambda of `.phaser(...)`. Knobs: `wet`, `dryFloor`. */
data class PhaserBuilder(val node: IgnitorDsl.Phaser)

/** Wet/dry balance of the phaser, 0..1 (default 0.5). */
@KlangScript.Function
fun PhaserBuilder.wet(wet: IgnitorDslLike): PhaserBuilder = copy(node = node.copy(wet = wet.toIgnitorDsl()))

/** Minimum dry coefficient of the phaser (default 0): the dry signal never drops below this share. */
@KlangScript.Function
fun PhaserBuilder.dryFloor(dryFloor: IgnitorDslLike): PhaserBuilder = copy(node = node.copy(dryFloor = dryFloor.toIgnitorDsl()))

// ── Shimmer ──────────────────────────────────────────────────────────────────

/** Builder for [IgnitorDsl.Shimmer], handed to the `configure` lambda of `.shimmer(...)`. Knobs: `wet`, `dryFloor`. */
data class ShimmerBuilder(val node: IgnitorDsl.Shimmer)

/** Wet/dry balance of the shimmer, 0..1 (default 0.5). */
@KlangScript.Function
fun ShimmerBuilder.wet(wet: IgnitorDslLike): ShimmerBuilder = copy(node = node.copy(wet = wet.toIgnitorDsl()))

/** Minimum dry coefficient of the shimmer (default 0): the dry signal never drops below this share. */
@KlangScript.Function
fun ShimmerBuilder.dryFloor(dryFloor: IgnitorDslLike): ShimmerBuilder = copy(node = node.copy(dryFloor = dryFloor.toIgnitorDsl()))
