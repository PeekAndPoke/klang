/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.state

import kotlinx.serialization.Serializable

/** The MIDI play code, as it is kept (KLANG-00422): the user's, one document. */
@Serializable
data class MidiPlay(
    /** The play code the user applied last; null for one who never applied one, who plays the app's own. */
    val code: String? = null,
)

/**
 * This machine's MIDI setup, as it is kept (KLANG-00422): what belongs to the keyboard on the desk, not to the user
 * wherever they sit.
 */
@Serializable
data class MidiDesk(
    /** The instruments the instrument keys step through, in their order, as their codes (`MidiLoadout.codes`). */
    val loadout: List<String> = emptyList(),
    /** The laptop keys' layout chosen by hand, a layout's name; null for "Automatic". */
    val keyLayout: String? = null,
)
