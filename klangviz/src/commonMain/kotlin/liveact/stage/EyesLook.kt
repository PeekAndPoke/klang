/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.peekandpoke.klang.viz.liveact.Switch
import io.peekandpoke.klang.viz.liveact.Switches

/**
 * Where Sirene's eyes glow while the front lamp throws the band's shadows (`?eyes=shadow`, [Switch.Eyes]); [word] is
 * its name in the address and in the line at the top.
 * - [Face]: in her face, always, as she was made.
 * - [Shadow] (KLANG-00478, to be judged by eye; the maintainer, 2026-11-19: "when the front light is on Sirene eyes
 *   move to the shadow"): while the front lamp is on, her eyes leave her face and glow in the head of her shadow, on
 *   the wall or on the surface behind the fence; with the lamp off they are back in her face.
 */
enum class EyesLook(val word: String) {
    Face("face"),
    Shadow("shadow");

    companion object {
        /** Where the address asks for her eyes: `?eyes=shadow` [Shadow]; any other value, or none, [Face]. */
        fun of(switches: Switches): EyesLook = entries.firstOrNull { it.word == switches[Switch.Eyes] } ?: Face
    }
}
