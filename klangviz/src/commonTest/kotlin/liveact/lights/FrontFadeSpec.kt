/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.lights

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe

class FrontFadeSpec : StringSpec({

    "it starts at 0 and rises at its own pace, never past the target" {
        val fade = FrontFade(rise = 1.0, fall = 2.0)

        fade.share shouldBe 0.0
        fade.follow(target = 1.0, dt = 0.25) shouldBe (0.25 plusOrMinus 1e-12)
        fade.follow(target = 1.0, dt = 0.5) shouldBe (0.75 plusOrMinus 1e-12)
        fade.follow(target = 1.0, dt = 0.5) shouldBe 1.0
        fade.follow(target = 0.6, dt = 0.1) shouldBe (0.95 plusOrMinus 1e-12)
        fade.follow(target = 0.6, dt = 5.0) shouldBe 0.6
    }

    "it falls at its own pace: a lamp turned off in one frame is followed over the fall's seconds" {
        val fade = FrontFade(rise = 1.0, fall = 1.6)

        fade.follow(target = 1.0, dt = 3.0)
        fade.follow(target = 0.0, dt = 0.8) shouldBe (0.5 plusOrMinus 1e-12)
        fade.follow(target = 0.0, dt = 0.4) shouldBe (0.25 plusOrMinus 1e-12)
        fade.follow(target = 0.0, dt = 0.4) shouldBe 0.0
        fade.follow(target = 0.0, dt = 0.4) shouldBe 0.0
    }

    "a target outside 0 to 1 is held to it; no number is the lamp off; a time below 0 or of no number moves nothing" {
        val fade = FrontFade(rise = 1.0, fall = 1.0)

        fade.follow(target = 7.0, dt = 2.0) shouldBe 1.0
        fade.follow(target = Double.NaN, dt = 0.5) shouldBe (0.5 plusOrMinus 1e-12)
        fade.follow(target = -3.0, dt = 0.1) shouldBe (0.4 plusOrMinus 1e-12)
        fade.follow(target = 1.0, dt = -1.0) shouldBe (0.4 plusOrMinus 1e-12)
        fade.follow(target = 1.0, dt = Double.NaN) shouldBe (0.4 plusOrMinus 1e-12)
    }

    "the lamp's level: its own, held to 0 to 1; 1 all through while it strobes; no number is off" {
        FrontFade.level(FrontLight(level = 0.3, strobing = false)) shouldBe 0.3
        FrontFade.level(FrontLight(level = 1.4, strobing = false)) shouldBe 1.0
        FrontFade.level(FrontLight(level = Strobe.LOW, strobing = true)) shouldBe 1.0
        FrontFade.level(FrontLight(level = Double.NaN, strobing = false)) shouldBe 0.0
        FrontFade.level(FrontLight.off) shouldBe 0.0
    }

    "the lamp is on from half its level, and while it strobes" {
        FrontFade.on(FrontLight(level = 0.49, strobing = false)) shouldBe 0.0
        FrontFade.on(FrontLight(level = 0.5, strobing = false)) shouldBe 1.0
        FrontFade.on(FrontLight(level = 1.0, strobing = false)) shouldBe 1.0
        FrontFade.on(FrontLight(level = Strobe.LOW, strobing = true)) shouldBe 1.0
        FrontFade.on(FrontLight(level = Double.NaN, strobing = false)) shouldBe 0.0
    }
})
