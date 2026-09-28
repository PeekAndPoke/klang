/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.filters

import io.peekandpoke.klang.audio_bridge.IgnitorDsl

/**
 * One [IgnitorDsl.EqSection] as an [EqCore] adapter needs it: the core's section type plus the
 * knob nodes that section actually carries.
 *
 * [db] is [EqCore.BELL]'s and [gain] is [EqCore.RAW_TAP]'s; both are null on every other type, so
 * an adapter that hands the core a 0.0 for the absent one is saying "this type ignores it" instead
 * of inventing a value (no dummy knobs, mirroring the sealed wire variants).
 */
internal class EqSectionSpec(
    val type: Int,
    val freq: IgnitorDsl,
    val q: IgnitorDsl,
    val db: IgnitorDsl? = null,
    val gain: IgnitorDsl? = null,
)

/**
 * The [EqSectionSpec] for one wire section: the ONE place a wire variant becomes an [EqCore] type.
 *
 * Exhaustive by construction, which is the guard [EqCore]'s append-only type order relies on: a
 * new [IgnitorDsl.EqSection] variant without an arm here fails compilation.
 *
 * Both adapters of the shared core read it, the per-voice `EqIgnitor` (through
 * `IgnitorDslRuntime`'s Eq arm, where this `when` lived until Katalyst step 4) and the orbit stage
 * `KatalystEqEffect` (through `KatalystChainBuilder`). Two copies of a variant-to-Int mapping are
 * two chances to disagree about what `band()` means on a voice and on a bus, and the whole point
 * of one core is that they cannot.
 */
internal fun eqSectionSpec(section: IgnitorDsl.EqSection): EqSectionSpec = when (section) {
    is IgnitorDsl.EqSection.Lowpass -> EqSectionSpec(EqCore.LOWPASS, section.freq, section.q)

    is IgnitorDsl.EqSection.Highpass -> EqSectionSpec(EqCore.HIGHPASS, section.freq, section.q)

    is IgnitorDsl.EqSection.Bandpass -> EqSectionSpec(EqCore.BANDPASS, section.freq, section.q)

    is IgnitorDsl.EqSection.Notch -> EqSectionSpec(EqCore.NOTCH, section.freq, section.q)

    is IgnitorDsl.EqSection.Bell -> EqSectionSpec(EqCore.BELL, section.freq, section.q, db = section.db)

    is IgnitorDsl.EqSection.RawTap -> EqSectionSpec(EqCore.RAW_TAP, section.freq, section.q, gain = section.gain)
}
