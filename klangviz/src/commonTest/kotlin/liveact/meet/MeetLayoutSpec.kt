/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.meet

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.band.Player
import kotlin.math.PI
import kotlin.math.hypot

class MeetLayoutSpec : StringSpec({

    "the six stand round the hexagon in the maintainer's order, each once" {
        MeetLayout.ORDER shouldBe listOf(Player.Sirene, Player.Skalpell, Player.Beben, Player.Wucht, Player.Rune, Player.Klaue)
        MeetLayout.ORDER.toSet() shouldBe Player.entries.toSet()
    }

    "the turn for a member brings them to the front: at the world's origin, facing the camera" {
        for (player in Player.entries) {
            val turn = MeetLayout.turnFor(player)
            val spot = MeetLayout.spotOf(player = player, turn = turn)

            MeetLayout.offFront(MeetLayout.facing(player = player, turn = turn)) shouldBe (0.0 plusOrMinus 1e-12)
            spot.x shouldBe (0.0 plusOrMinus 1e-12)
            spot.z shouldBe (0.0 plusOrMinus 1e-12)
        }
    }

    "with Sirene at the front, Skalpell stands to the right and Klaue to the left, a step round, facing out" {
        val skalpell = MeetLayout.spotOf(player = Player.Skalpell, turn = 0.0)
        val klaue = MeetLayout.spotOf(player = Player.Klaue, turn = 0.0)

        skalpell.x shouldBeGreaterThan 0.0
        klaue.x shouldBeLessThan 0.0
        skalpell.z shouldBe (klaue.z plusOrMinus 1e-12)
        skalpell.facing shouldBe (PI / 3 plusOrMinus 1e-12)
        // a step round the hexagon: the place as far from the middle as the front one
        hypot(skalpell.x, skalpell.z - MeetLayout.MIDDLE_Z) shouldBe (MeetLayout.PLACE plusOrMinus 1e-12)
    }

    "from the middle out: the surface behind, the mesh, the member, the fixture, the lamp" {
        MeetLayout.BACK shouldBeLessThan MeetLayout.MESH
        MeetLayout.MESH shouldBeLessThan MeetLayout.PLACE
        MeetLayout.PLACE shouldBeLessThan MeetLayout.FIXTURE
        MeetLayout.FIXTURE shouldBeLessThan MeetLayout.LAMP
    }

    "the next member stands to the right and comes in from the right; after the last the first, and back" {
        MeetLayout.next(Player.Sirene) shouldBe Player.Skalpell
        MeetLayout.next(Player.Klaue) shouldBe Player.Sirene
        MeetLayout.previous(Player.Sirene) shouldBe Player.Klaue
        MeetLayout.previous(Player.Skalpell) shouldBe Player.Sirene
        // the turn for the next one is a step to smaller angles: the carousel turns so they come from the right
        MeetLayout.turnFor(MeetLayout.next(Player.Sirene)) shouldBe (MeetLayout.turnFor(Player.Sirene) - MeetLayout.STEP plusOrMinus 1e-12)
        MeetLayout.spotOf(player = MeetLayout.next(Player.Sirene), turn = MeetLayout.turnFor(Player.Sirene)).x shouldBeGreaterThan 0.0

        for (player in Player.entries) {
            MeetLayout.previous(MeetLayout.next(player)) shouldBe player
        }
    }

    "how far from the front: either way, whatever the rounds" {
        MeetLayout.offFront(0.3) shouldBe (0.3 plusOrMinus 1e-12)
        MeetLayout.offFront(-0.3) shouldBe (0.3 plusOrMinus 1e-12)
        MeetLayout.offFront(4 * PI + 0.3) shouldBe (0.3 plusOrMinus 1e-9)
        MeetLayout.offFront(PI) shouldBe (PI plusOrMinus 1e-12)
    }

    "a fixture glows full at the front, less as its member turns away, and not at the next place" {
        MeetLayout.glow(0.0) shouldBe 1.0
        MeetLayout.glow(0.2) shouldBe (MeetLayout.glow(-0.2) plusOrMinus 1e-12)
        MeetLayout.glow(0.2) shouldBeLessThan 1.0
        MeetLayout.glow(0.4) shouldBeLessThan MeetLayout.glow(0.2)
        MeetLayout.glow(MeetLayout.STEP) shouldBe 0.0
        MeetLayout.glowOf(player = Player.Sirene, turn = 0.0) shouldBe 1.0
        MeetLayout.glowOf(player = Player.Rune, turn = 0.0) shouldBe 0.0
    }

    "the lamp is full with a member at the front, and dips halfway between two" {
        MeetLayout.dip(0.0) shouldBe 1.0
        MeetLayout.dip(-3 * MeetLayout.STEP) shouldBe (1.0 plusOrMinus 1e-12)
        MeetLayout.dip(MeetLayout.STEP / 2) shouldBe (1.0 - MeetLayout.DIP plusOrMinus 1e-12)
        MeetLayout.dip(MeetLayout.STEP / 4) shouldBeGreaterThan 1.0 - MeetLayout.DIP
        MeetLayout.dip(MeetLayout.STEP / 4) shouldBeLessThan 1.0
    }

    "on a tall picture the camera looks lower: the frame's top stays, the floor gets the height shown more" {
        MeetLayout.lookHigh(16.0 / 9.0) shouldBe MeetLayout.LOOK_HIGH
        MeetLayout.lookHigh(1.0) shouldBe MeetLayout.LOOK_HIGH

        val phone = 390.0 / 844.0
        val shown = MeetLayout.FRAME_WIDE / phone

        MeetLayout.lookHigh(phone) shouldBeLessThan MeetLayout.LOOK_HIGH
        MeetLayout.lookHigh(phone) + shown / 2 shouldBe (MeetLayout.LOOK_HIGH + MeetLayout.FRAME_HIGH / 2 plusOrMinus 1e-12)
        MeetLayout.lookHigh(Double.NaN) shouldBe MeetLayout.LOOK_HIGH
    }

    "the lens fits the frame by the narrower side: a wide picture by its height, a tall one by its width" {
        val byHeight = MeetLayout.fov(16.0 / 9.0)

        MeetLayout.fov(21.0 / 9.0) shouldBe (byHeight plusOrMinus 1e-12)
        MeetLayout.fov(390.0 / 844.0) shouldBeGreaterThan byHeight
        MeetLayout.fov(390.0 / 844.0) shouldBeLessThan 90.0
        // no number, or none at all, counts as square
        MeetLayout.fov(Double.NaN) shouldBe (MeetLayout.fov(1.0) plusOrMinus 1e-12)
        MeetLayout.fov(0.0) shouldBe (MeetLayout.fov(1.0) plusOrMinus 1e-12)
    }
})
