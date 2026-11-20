/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

class SwitchSpec : StringSpec({

    "no two switches share a key" {
        Switch.entries.groupBy { it.key }.filterValues { it.size > 1 }.keys.shouldBeEmpty()
    }

    "every key is lower case letters and digits only" {
        Switch.entries.map { it.key }.filterNot { it.matches(Regex("[a-z0-9]+")) }.shouldBeEmpty()
    }

    // The keys are in page addresses the maintainer and the test plans use: a renamed or dropped switch is noticed here.
    "the keys are the ones the addresses use" {
        Switch.entries.map { it.key } shouldBe listOf(
            "seed", "t", "test",
            "nohud", "oversample", "sharp", "shadows",
            "still", "move", "view", "cam", "at", "head", "from", "down", "far", "outline",
            "warm", "snakes", "nosnakes", "straight", "eyes", "skin",
            "rig", "fcolor", "front", "nostrobe",
            "nosky", "nofog", "fog", "fullfog", "stage", "floor", "haze", "world", "drift",
        )
    }

    "a switch is read by its key" {
        val switches = Switches.parse("?nosky&straight=0.4&view=stadium")

        switches.has(Switch.NoSky) shouldBe true
        switches.has(Switch.NoFog) shouldBe false
        switches.double(Switch.Straight) shouldBe 0.4
        switches[Switch.View] shouldBe "stadium"
        switches[Switch.Time] shouldBe null
    }
})
