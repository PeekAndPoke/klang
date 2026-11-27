/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.song

import kotlinx.serialization.Serializable

/**
 * How a note's sound rises and falls, as its voice plays it: [attack], [decay] and [release] in seconds, [sustain] the
 * share of the full level it holds while the note is held. The band reads it with the note, so what it shows follows
 * what sounds (a string's light, [io.peekandpoke.klang.viz.liveact.band.instruments.strings.NoteLight]).
 */
@Serializable
data class Envelope(val attack: Double, val decay: Double, val sustain: Double, val release: Double) {
    /**
     * Whether every value is a number: one that is not says nothing (the light,
     * [io.peekandpoke.klang.viz.liveact.band.instruments.strings.NoteLight], goes by the note alone then).
     */
    val known: Boolean get() = attack.isFinite() && decay.isFinite() && sustain.isFinite() && release.isFinite()
}
