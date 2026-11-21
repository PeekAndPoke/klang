/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.song

/**
 * Which of a song's tags the drummer plays, and as what: the [kick] (both feet, and where it runs fast the
 * stroboscope), the [snare], and the [cymbals] (the hats, the crash and the ride, told apart by their sound's name;
 * a count-in played on them is one of them). A song names its parts as it likes; its score says which is which (see
 * [Song.drumHits]).
 */
data class DrumTags(val kick: String, val snare: String, val cymbals: List<String>) {
    /** The tags of the beat itself, the kick's and the snare's: when the drummer is seen to play. */
    val beat: List<String> get() = listOf(kick, snare)
}
