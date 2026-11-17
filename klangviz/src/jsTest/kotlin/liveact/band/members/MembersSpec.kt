/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.members

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.band.Player

/** The band's sheets: a check for the future, for whoever adds a member or edits a sheet. */
class MembersSpec : StringSpec({

    "every player has one sheet, filed under who they are, in the players' order" {
        Members.ALL.keys.toList() shouldBe Player.entries

        for (player in Player.entries) {
            Members.of(player).player shouldBe player
            Members.of(player).name shouldBe player.name
        }
    }

    "a sheet's figure is the file the figure script writes for its player" {
        Members.of(Player.Sirene).figure shouldBe "sirene.glb"
    }
})
