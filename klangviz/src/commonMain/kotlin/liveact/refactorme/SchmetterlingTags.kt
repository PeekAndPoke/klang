/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.refactorme

/**
 * The tags of Der Schmetterling's parts, as its code names them and its recording keeps them: the live act reads a
 * song by these names. A bridge between a song and the stage would have the song say which of its parts is which
 * (the kick, the snare, the cymbals, a tune, a strung instrument), whatever it calls them. See `SchmetterlingCast`
 * for what this package is.
 */
object SchmetterlingTags {
    /** The kick: the drummer's feet, and where the bass drum runs fast the stroboscope. */
    const val KICK = "kick"

    /** The snare. */
    const val SNARE = "snare"

    /** The cymbals: the hats, the crash and the ride, told apart by their sound's name. */
    const val HATS = "hats"

    /** The count-in, played on the cymbals. */
    const val COUNT_IN = "countin"

    /** The bass. */
    const val BASS = "bass"

    /** The lead guitar. */
    const val GUITAR1 = "guitar1"

    /** The rhythm guitar's two parts. */
    const val GUITAR2 = "guitar2"
    const val GUITAR3 = "guitar3"

    /** The marimba's tune. */
    const val LEAD = "lead"

    /** The "Gummieimer", which sounds like a held melody: played on the trumpet. */
    const val TROMMEL = "trommel"
}
