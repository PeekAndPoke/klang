/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.Switches

class LookFlipSpec : StringSpec({

    "without ?look=new today is shown and the new look kept; with it the other way round" {
        LookFlip.of(Switches.parse("")) shouldBe LookFlip(shown = LookVariants(), kept = LookVariants.NEW, new = false)
        LookFlip.of(Switches.parse("?look=new")) shouldBe LookFlip(shown = LookVariants.NEW, kept = LookVariants(), new = true)
        LookFlip.of(Switches.parse("?look=new&world=night")).shown shouldBe LookVariants.NEW.copy(world = WorldLook.Night)
        LookFlip.of(Switches.parse("?look=new&world=night")).kept shouldBe LookVariants()
        // single switches without ?look=new are today's side: x goes to the new look from them
        LookFlip.of(Switches.parse("?stage=mesh")) shouldBe
            LookFlip(shown = LookVariants(stage = StageLook.Mesh), kept = LookVariants.NEW, new = false)
    }

    "x flips between today and the new look; twice is back where it was" {
        val today = LookFlip.of(Switches.parse(""))

        today.flip() shouldBe LookFlip(shown = LookVariants.NEW, kept = LookVariants(), new = true)
        today.flip().flip() shouldBe today
    }

    "a variant's own key tunes the side shown; x back and x again finds each side as it was last tuned" {
        val atNight = LookFlip.of(Switches.parse("")).flip().let { it.tune(it.shown.copy(world = WorldLook.Night)) }

        atNight.shown shouldBe LookVariants.NEW.copy(world = WorldLook.Night)
        atNight.kept shouldBe LookVariants()

        val back = atNight.flip()

        back.shown shouldBe LookVariants()
        back.new shouldBe false

        // today tuned too: the fence before the plain look
        val todayMesh = back.tune(back.shown.nextStage())

        todayMesh.flip().shown shouldBe LookVariants.NEW.copy(world = WorldLook.Night)
        todayMesh.flip().flip().shown shouldBe LookVariants(stage = StageLook.Mesh)
    }

    "its line says which side is shown" {
        LookFlip.of(Switches.parse("")).line() shouldBe "look: today"
        LookFlip.of(Switches.parse("")).flip().line() shouldBe "look: new"
        LookFlip.of(Switches.parse("?look=new")).line() shouldBe "look: new"
    }
})
