/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.peekandpoke.klang.viz.liveact.Switch
import io.peekandpoke.klang.viz.liveact.Switches

/**
 * The haze behind the fence (`?haze=parts`, [Switch.Haze]); [word] is its name in the address and in the line at the
 * top. Only the fence has the haze ([StageLook.Mesh]); before the wall it changes nothing.
 * - [Whole]: the haze as it was made, the same in the band's shadows as around them.
 * - [Parts] (KLANG-00478, to be judged by eye; the maintainer, 2026-11-19: "the fog could give way to the
 *   'shadows'"): where the front lamp throws the band's shadows onto the surface behind the fence, the haze in front
 *   of them draws back, so each shadow stands in a clearing; when the lamp goes off the haze closes again.
 */
enum class HazeLook(val word: String) {
    Whole("whole"),
    Parts("parts");

    companion object {
        /** The haze the address asks for: `?haze=parts` [Parts]; any other value, or none, [Whole]. */
        fun of(switches: Switches): HazeLook = entries.firstOrNull { it.word == switches[Switch.Haze] } ?: Whole
    }
}
