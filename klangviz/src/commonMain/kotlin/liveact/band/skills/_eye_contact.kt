/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.skills

import io.peekandpoke.klang.viz.liveact.band.Player

/**
 * Who of the two in an eye contact turns their head, of [first] (who starts it) and [second], in that order: each
 * only where their [skills] hold [EyeContact]. One without it does not turn; the other turns all the same.
 */
fun turning(first: Player, second: Player, skills: (Player) -> List<Skill>): List<Player> =
    listOf(first, second).filter { player -> EyeContact in skills(player) }
