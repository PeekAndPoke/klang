/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.members

import io.peekandpoke.klang.viz.liveact.band.Player

/** The band's six members: a sheet each, by who they are. */
object Members {
    /** Every member's sheet, by who they are, in the players' order. */
    val ALL: Map<Player, Member> = listOf(WUCHT, BEBEN, SKALPELL, SIRENE, RUNE, KLAUE).associateBy { it.player }

    /** The sheet of [player]. */
    fun of(player: Player): Member = ALL.getValue(player)
}
