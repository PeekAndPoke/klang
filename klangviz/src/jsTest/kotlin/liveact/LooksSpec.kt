/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.band.Player
import io.peekandpoke.klang.viz.liveact.band.StagePlot
import io.peekandpoke.klang.viz.liveact.camera.CLEAR
import io.peekandpoke.klang.viz.liveact.camera.looksOf
import io.peekandpoke.klang.viz.liveact.camera.looksOn
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** The band's own looks against the band's own places: a check for the future, for whoever moves a player or sets a look. */
class LooksSpec : StringSpec({

    val homes = StagePlot.PLACES
    val looks = looksOn(homes)

    "every player has their looks, as many as are set" {
        looks.keys shouldBe Player.entries.toSet()

        for (player in Player.entries) {
            looks.getValue(player).size shouldBe looksOf(player).size
        }
    }

    "no look's camera stands in another player: each is clear of where every other one stands, or above their heads" {
        var moved = 0

        for ((player, theirs) in looks) {
            val home = homes.getValue(player)

            for ((index, look) in theirs.withIndex()) {
                val set = looksOf(player)[index]

                if (look != set) {
                    moved++
                }

                // a look keeps what it looks at, its height and its lens
                look.at shouldBe set.at
                look.pos.second shouldBe set.pos.second
                look.fov shouldBe set.fov

                if (look.pos.second + home.y >= 2.1) {
                    look shouldBe set

                    continue
                }

                // the camera on the stage, with the player at home
                val x = home.x + look.pos.first * cos(home.turn) + look.pos.third * sin(home.turn)
                val z = home.z - look.pos.first * sin(home.turn) + look.pos.third * cos(home.turn)

                // it is moved no further than it has to be, and never onto the player it looks at
                hypot(look.pos.first - set.pos.first, look.pos.third - set.pos.third) shouldBeLessThan 1.1
                hypot(x - home.x, z - home.z) shouldBeGreaterThan CLEAR

                for ((other, place) in homes) {
                    if (other != player) {
                        hypot(x - place.x, z - place.z) shouldBeGreaterThan CLEAR - 0.02
                    }
                }
            }
        }

        // some of the looks as they are set do stand too near someone: the clearing is not idle
        (moved > 0) shouldBe true
    }
})
