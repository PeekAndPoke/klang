/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.state

import kotlinx.serialization.Serializable

/**
 * This machine's audio output as it is stored (KLANG-00422): the master [volume], a linear gain from 0.0 (silence) to
 * 1.0 (the song's own level, KLANG-00330).
 */
@Serializable
data class AudioOutput(val volume: Double = 1.0) {

    /** The volume held in 0.0 to 1.0; one that is no number starts at full, as a first visit does. */
    fun inRange(): AudioOutput = when {
        // NaN-guard: an unreadable gain is full
        volume != volume -> copy(volume = 1.0)
        else -> copy(volume = volume.coerceIn(0.0, 1.0))
    }
}
