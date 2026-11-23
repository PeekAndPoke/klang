/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.members

import io.peekandpoke.klang.viz.liveact.band.Player
import io.peekandpoke.klang.viz.liveact.band.skills.GhostEyes

/** The band's six members: a sheet each, by who they are. */
object Members {
    /** Every member's sheet, by who they are, in the players' order. */
    val ALL: Map<Player, Member> = listOf(WUCHT, BEBEN, SKALPELL, SIRENE, RUNE, KLAUE).associateBy { it.player }

    /** The sheet of [player]. */
    fun of(player: Player): Member = ALL.getValue(player)

    /** Who has their eyes in their shadow (the skill [GhostEyes] on their sheet), in the players' order. */
    val ghostEyed: List<Player> get() = ALL.values.filter { GhostEyes in it.skills }.map { it.player }

    /** The first of [ghostEyed]; null where nobody has them (the page "Meet the band" shows one pair). */
    val shadowEyed: Player? get() = ghostEyed.firstOrNull()
}
