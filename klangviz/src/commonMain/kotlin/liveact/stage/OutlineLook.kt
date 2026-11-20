/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.peekandpoke.klang.viz.liveact.Switch
import io.peekandpoke.klang.viz.liveact.Switches

/**
 * A working aid, not part of the show: a thin bright line on the floor along the stage's edge ([StageEdge]), so it can
 * be seen from every camera where the stage ends and the mirror with it (`?edge=on`, [Switch.Edge]; the key j turns it
 * over; the maintainer, 2026-11-20: "can we temporarily draw an outline around the stage, so we see where it ends").
 * [word] is its name in the address and in the line at the top. On the plain floor as on the mirror floor; it is not
 * mirrored.
 */
enum class OutlineLook(val word: String) {
    Off("off"),
    On("on");

    companion object {
        /** The outline the address asks for: `?edge=on` [On]; any other value, or none, [Off]. */
        fun of(switches: Switches): OutlineLook = entries.firstOrNull { it.word == switches[Switch.Edge] } ?: Off
    }
}
