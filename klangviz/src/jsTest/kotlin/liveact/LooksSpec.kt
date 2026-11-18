/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.band.Player
import io.peekandpoke.klang.viz.liveact.band.StagePlot
import io.peekandpoke.klang.viz.liveact.camera.CLEAR
import io.peekandpoke.klang.viz.liveact.camera.Ground
import io.peekandpoke.klang.viz.liveact.camera.TOTAL_FROM
import io.peekandpoke.klang.viz.liveact.camera.arcOf
import io.peekandpoke.klang.viz.liveact.camera.looksOf
import io.peekandpoke.klang.viz.liveact.camera.looksOn
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** How far the line from an arc's camera to its player stays from where every other one stands, in metres. */
private const val SIGHT = 0.6

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

    "no arc passes through another player: its camera is clear of where every other one stands, and so is its line of sight" {
        for ((player, home) in homes) {
            val arc = arcOf(player)

            // an arc is a way round, not a place: it goes a fair part of the round
            (arc.to - arc.from) shouldBeGreaterThan 0.5

            // (the camera, in the player's space, on the stage)
            fun onStage(x: Double, z: Double) =
                Ground(x = home.x + x * cos(home.turn) + z * sin(home.turn), z = home.z - x * sin(home.turn) + z * cos(home.turn))

            val target = onStage(x = arc.at.first, z = arc.at.third)

            for (way in listOf(1, -1)) {
                for (step in 0..100) {
                    val angle = arc.angle(way = way, u = step / 100.0)
                    val camera = onStage(x = arc.far * sin(angle), z = arc.far * cos(angle))

                    for ((other, place) in homes) {
                        if (other == player) {
                            continue
                        }

                        withClue("the arc round $player at $angle, the camera from $other") {
                            hypot(camera.x - place.x, camera.z - place.z) shouldBeGreaterThan CLEAR
                        }

                        // the nearest point to the other one on the line from the camera to what it looks at
                        val dx = target.x - camera.x
                        val dz = target.z - camera.z
                        val along = (((place.x - camera.x) * dx + (place.z - camera.z) * dz) / (dx * dx + dz * dz)).coerceIn(0.0, 1.0)

                        withClue("the arc round $player at $angle, the line of sight from $other") {
                            hypot(camera.x + along * dx - place.x, camera.z + along * dz - place.z) shouldBeGreaterThan SIGHT
                        }
                    }
                }
            }
        }
    }

    "from an arc's end the straight way back to the total is clear of every other player, or that arc cuts back" {
        var cuts = 0

        for ((player, home) in homes) {
            val arc = arcOf(player)

            if (arc.cutsBack) {
                cuts++

                continue
            }

            for (angle in listOf(arc.from, arc.to)) {
                val x = arc.far * sin(angle)
                val z = arc.far * cos(angle)
                val camera = Ground(
                    x = home.x + x * cos(home.turn) + z * sin(home.turn),
                    z = home.z - x * sin(home.turn) + z * cos(home.turn),
                )
                val dx = TOTAL_FROM.x - camera.x
                val dz = TOTAL_FROM.z - camera.z

                for ((other, place) in homes) {
                    if (other == player) {
                        continue
                    }

                    val along = (((place.x - camera.x) * dx + (place.z - camera.z) * dz) / (dx * dx + dz * dz)).coerceIn(0.0, 1.0)

                    withClue("the way back from the arc round $player at $angle, from $other") {
                        hypot(camera.x + along * dx - place.x, camera.z + along * dz - place.z) shouldBeGreaterThan CLEAR
                    }
                }
            }
        }

        // (the drummer's arc ends behind the front row)
        cuts shouldBe 1
    }

    "an arc goes from its one end to its other, and back the other way" {
        val arc = arcOf(Player.Wucht)

        arc.angle(way = 1, u = 0.0) shouldBe (arc.from plusOrMinus 1e-12)
        arc.angle(way = 1, u = 1.0) shouldBe (arc.to plusOrMinus 1e-12)
        arc.angle(way = -1, u = 0.0) shouldBe (arc.to plusOrMinus 1e-12)
        arc.angle(way = -1, u = 0.25) shouldBe (arc.to - 0.25 * (arc.to - arc.from) plusOrMinus 1e-12)
    }
})
