/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.peekandpoke.klang.viz.liveact.Switch
import io.peekandpoke.klang.viz.liveact.Switches

/**
 * What stands behind the band (`?stage=mesh`, [Switch.Stage]); [word] is its name in the address.
 * - [Wall]: the wall as it was made, an ornament that turns into a wire mesh under the front lamp, and the fog on the
 *   floor.
 * - [Mesh] (KLANG-00478, to be judged by eye): a wire-mesh fence with a dark surface some 2.5 m behind it, a haze
 *   between the two in place of the fog on the floor, and a low glow behind the drummer.
 */
enum class StageLook(val word: String) {
    Wall("wall"),
    Mesh("mesh");

    companion object {
        /** The look the address asks for: `?stage=mesh` [Mesh]; any other value, or none, [Wall]. */
        fun of(switches: Switches): StageLook = entries.firstOrNull { it.word == switches[Switch.Stage] } ?: Wall
    }
}
