/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries

/**
 * Band methods for the equalizer opened by `.eq()`. Each returns a new [IgnitorDsl.Eq], so they
 * chain. They exist ONLY on an equalizer (the same shape as the supersaw config methods):
 * `.band(...)` on a plain oscillator is an error, `.eq()` is the entry point.
 *
 * Two kinds of band, and the difference is audible:
 *
 * - [band] is an ordinary EQ band. Bands are applied one after another, so each one works on
 *   what the previous one produced, and two overlapping boosts add up (a +6 and a +6 give
 *   roughly +12 where they overlap). Gain is in decibels.
 * - [tap] takes the sound going INTO the equalizer, filters that, and mixes it back in. Taps do
 *   not stack on each other, they all mix with the original. Gain is a plain multiplier.
 *
 * Reach for [tap] when you are adding resonant boosts on top of a sound (the classic guitar
 * mids-plus-presence lift), and for [band] when you are shaping with EQ bands.
 */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(IgnitorDsl.Eq::class)
object KlangScriptEqExtensions {

    /**
     * Adds a peaking band: [db] decibels of gain at [freq], [q] the width. Bands apply one
     * after another, so two overlapping bands compound.
     *
     * `db = 0` is exactly transparent, negative [db] cuts, and a cut mirrors the same boost
     * exactly, so a `+6` and a `-6` band at the same [freq] and [q] cancel out. The width
     * does NOT move as [db] changes (that is the point of this parameterization): measured
     * at the default [q] it holds at 1.90 octaves all the way from -6 to -34 dB. Past that
     * the internal width hits its floor and deeper cuts DO start narrowing (1.39 octaves at
     * -40 dB, about 0.45 at -60), so a very deep cut turns surgical instead of broad.
     *
     * ⚠ The second positional argument is [q], NOT gain: `.band(1200, 6)` sets a width of 6
     * and leaves gain at 0, which is silent. Write `.band(freq = 1200, db = 6)` when you mean
     * gain: KlangScript forbids mixing positional and named args, so name them all.
     *
     * All three values are read once per block and shape the filter coefficients, [db]
     * included, so an LFO on [db] zippers just like an LFO on a cutoff. For a smooth gain
     * ride use a VCA instead. See [tap] for the parallel alternative.
     */
    @KlangScript.Method
    fun band(
        self: IgnitorDsl.Eq,
        freq: IgnitorDslLike,
        q: IgnitorDslLike = 0.707,
        db: IgnitorDslLike = 0.0,
    ): IgnitorDsl.Eq = self.copy(
        sections = self.sections + IgnitorDsl.EqSection.Bell(
            freqHz = freq.toIgnitorDsl(), q = q.toIgnitorDsl(), db = db.toIgnitorDsl(),
        ),
    )

    /**
     * Adds a parallel resonant boost: takes the sound going INTO the equalizer, keeps only the
     * band around [freq], scales it by [gain] and mixes it back in. The short way to write
     * `signal.add(signal.bandpass(freq, q).mul(gain))`, in one pass.
     *
     * [gain] is a plain multiplier, not decibels: 1.0 mixes the band back in at full strength,
     * 0 is silent. [q] is the ordinary bandpass width, the same number `.bandpass()` takes.
     * Taps mix with the original sound rather than stacking on each other, so several taps stay
     * predictable where several [band] calls would compound.
     *
     * ⚠ On a tap, [q] sets the LEVEL as well as the width: the boost at [freq] is `1 + gain·q`,
     * so raising [q] narrows the band AND lifts it at the same time (at gain 1.0: q 0.5 is
     * +3.5 dB over ~3.0 octaves, q 4.0 is +14.0 dB over ~0.8). To tighten a tap without it
     * getting louder, lower [gain] as you raise [q]. On [band] the gain is [db] alone and the
     * width does not move with it. This is also why the default `tap(freq)` is NOT silent:
     * `1 + 1·1 = 2`, a lift of about 6 dB, where the default `band(freq)` is transparent.
     *
     * ⚠ Give [gain] a number or an osc-param, not a moving signal. A moving [gain] is re-read
     * only once per block, so a swept tap gain steps instead of gliding; for that, use the
     * chained `signal.add(signal.bandpass(...).mul(lfo))` form, which is smooth.
     */
    @KlangScript.Method
    fun tap(
        self: IgnitorDsl.Eq,
        freq: IgnitorDslLike,
        q: IgnitorDslLike = 1.0,
        gain: IgnitorDslLike = 1.0,
    ): IgnitorDsl.Eq = self.copy(
        sections = self.sections + IgnitorDsl.EqSection.RawTap(
            freqHz = freq.toIgnitorDsl(), q = q.toIgnitorDsl(), gain = gain.toIgnitorDsl(),
        ),
    )
}
