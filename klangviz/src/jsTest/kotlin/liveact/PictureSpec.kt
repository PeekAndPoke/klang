/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.stage.LookFlip
import io.peekandpoke.klang.viz.liveact.stage.LookVariants
import io.peekandpoke.klang.viz.liveact.stage.ReflectionQuality
import io.peekandpoke.klang.viz.liveact.stage.WorldLook

class PictureSpec : StringSpec({

    "the look's choices reach the live act that has the handle, and nothing while none has it" {
        val picture = Picture()
        val chosen = mutableListOf<LookVariants>()
        var flips = 0

        picture.chooseLook(LookVariants(world = WorldLook.Night))
        picture.flipLook()

        picture.attach(
            take = {},
            takeFog = {},
            takeShadows = {},
            takeReflections = {},
            takeLook = { chosen.add(it) },
            flipLook = { flips++ },
        )
        picture.chooseLook(LookVariants(world = WorldLook.Night))
        picture.flipLook()
        chosen shouldBe listOf(LookVariants(world = WorldLook.Night))
        flips shouldBe 1

        picture.detach()
        picture.chooseLook(LookVariants())
        picture.flipLook()
        chosen.size shouldBe 1
        flips shouldBe 1
    }

    "the reflections' choice reaches the live act that has the handle, and nothing while none has it" {
        val picture = Picture()
        val chosen = mutableListOf<ReflectionQuality>()

        picture.chooseReflections(ReflectionQuality.Off)
        picture.attach(take = {}, takeFog = {}, takeShadows = {}, takeReflections = { chosen.add(it) }, takeLook = {}, flipLook = {})
        picture.chooseReflections(ReflectionQuality.Off)
        chosen shouldBe listOf(ReflectionQuality.Off)

        picture.detach()
        picture.chooseReflections(ReflectionQuality.On)
        chosen shouldBe listOf(ReflectionQuality.Off)
    }

    "the reflections the live act tells are the reflections' stream; on until one tells" {
        val picture = Picture()
        val seen = mutableListOf<ReflectionQuality>()
        val unsubscribe = picture.reflections.subscribeToStream { seen.add(it) }

        picture.tellReflections(ReflectionQuality.Off)
        unsubscribe()

        seen shouldBe listOf(ReflectionQuality.On, ReflectionQuality.Off)
    }

    "the look the live act tells is the look's stream; today's until one tells" {
        val picture = Picture()
        val seen = mutableListOf<LookFlip>()
        val unsubscribe = picture.look.subscribeToStream { seen.add(it) }
        val new = LookFlip.of(Switches.parse("?look=new"))

        picture.tellLook(new)
        unsubscribe()

        seen shouldBe listOf(LookFlip.of(Switches()), new)
    }
})
