/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.filters

import io.peekandpoke.klang.audio_bridge.BodyMaterials
import io.peekandpoke.klang.audio_bridge.VowelBands

/**
 * The bands a [ResonatorBank] is installed from, kind-neutral: per band a centre frequency in Hz, the SVF's q (a pure
 * width control) and a LINEAR gain, in band order. What a band's gain means is decided where a catalogue row becomes
 * a band, [ofBody] and [ofVowel]; the bank knows nothing of either.
 *
 * `freq` and `q` are the rows' RAW values: the bank clamps them at install time with the SVF's own guards, because
 * the cutoff guard depends on the sample rate and a table does not.
 *
 * Immutable after construction (the arrays are never written again). The engine's tables are built once per
 * process (`ResonatorTables`), so a change of material or vowel compares two references.
 */
class ResonatorTable private constructor(
    internal val freq: DoubleArray,
    internal val q: DoubleArray,
    internal val gain: DoubleArray,
) {
    /** The number of bands. */
    val count: Int = freq.size

    companion object {
        /** A body material's modes as a table: the gain is [LowPassHighPassFilters.bodyGain] per mode. */
        fun ofBody(modes: List<BodyMaterials.Mode>): ResonatorTable = ResonatorTable(
            freq = DoubleArray(modes.size) { modes[it].freq },
            q = DoubleArray(modes.size) { modes[it].q },
            gain = DoubleArray(modes.size) { LowPassHighPassFilters.bodyGain(modes[it]) },
        )

        /** A vowel's formant bands as a table: the gain is [LowPassHighPassFilters.vowelGain] per band. */
        fun ofVowel(bands: List<VowelBands.Band>): ResonatorTable = ResonatorTable(
            freq = DoubleArray(bands.size) { bands[it].freq },
            q = DoubleArray(bands.size) { bands[it].q },
            gain = DoubleArray(bands.size) { LowPassHighPassFilters.vowelGain(bands[it]) },
        )
    }
}
