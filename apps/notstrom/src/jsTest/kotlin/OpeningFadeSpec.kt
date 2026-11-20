/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.notstrom

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.notstrom.OpeningFade.Layers
import io.peekandpoke.klang.notstrom.OpeningFade.Step

class OpeningFadeSpec : StringSpec({

    "after the click the screen fades out to black in 0.8 s, eased at both ends; the black is there at once" {
        OpeningFade.layers(step = Step.Click, since = 0.0) shouldBe Layers(screen = 1.0, line = 0.0, black = 1.0)
        // a quarter of the way: 0.25 * 0.25 * (3 - 0.5) of it gone
        OpeningFade.layers(step = Step.Click, since = 0.2).screen shouldBe (1.0 - 0.15625 plusOrMinus 1e-12)
        OpeningFade.layers(step = Step.Click, since = 0.4).screen shouldBe (0.5 plusOrMinus 1e-12)
        OpeningFade.layers(step = Step.Click, since = 0.8) shouldBe Layers(screen = 0.0, line = 0.0, black = 1.0)
    }

    "the readying words come up on the black only after a second, at the screen's pace, and stay" {
        OpeningFade.layers(step = Step.Click, since = 1.0).line shouldBe 0.0
        OpeningFade.layers(step = Step.Click, since = 1.2).line shouldBe (0.15625 plusOrMinus 1e-12)
        OpeningFade.layers(step = Step.Click, since = 1.4).line shouldBe (0.5 plusOrMinus 1e-12)
        OpeningFade.layers(step = Step.Click, since = 1.8) shouldBe Layers(screen = 0.0, line = 1.0, black = 1.0)
        OpeningFade.layers(step = Step.Click, since = 600.0) shouldBe Layers(screen = 0.0, line = 1.0, black = 1.0)
    }

    "once the band stands the black goes in a moment, 0.3 s, eased (the stage is pitch black by itself); no screen, no words" {
        OpeningFade.layers(step = Step.Standing, since = 0.0) shouldBe Layers(screen = 0.0, line = 0.0, black = 1.0)
        OpeningFade.layers(step = Step.Standing, since = 0.075).black shouldBe (1.0 - 0.15625 plusOrMinus 1e-12)
        OpeningFade.layers(step = Step.Standing, since = 0.15).black shouldBe (0.5 plusOrMinus 1e-12)
        OpeningFade.layers(step = Step.Standing, since = 0.3) shouldBe Layers(screen = 0.0, line = 0.0, black = 0.0)
        OpeningFade.layers(step = Step.Standing, since = 60.0) shouldBe Layers(screen = 0.0, line = 0.0, black = 0.0)
    }

    "a time below 0, or no number, is the step's beginning" {
        listOf(-1.0, Double.NaN, Double.NEGATIVE_INFINITY).forEach { since ->
            OpeningFade.layers(step = Step.Click, since = since) shouldBe Layers(screen = 1.0, line = 0.0, black = 1.0)
            OpeningFade.layers(step = Step.Standing, since = since) shouldBe Layers(screen = 0.0, line = 0.0, black = 1.0)
        }
    }

    "the fade counts frame by frame, a long frame (a hitch) as a quarter of a second, no frame of no number" {
        OpeningFade.advance(since = 1.0, frame = 0.016) shouldBe (1.016 plusOrMinus 1e-12)
        OpeningFade.advance(since = 1.0, frame = 3.0) shouldBe 1.25
        OpeningFade.advance(since = 1.0, frame = -0.5) shouldBe 1.0
        OpeningFade.advance(since = 1.0, frame = Double.NaN) shouldBe 1.0
    }

    "each step settles when its last layer has arrived, and nothing changes after" {
        OpeningFade.settles(Step.Click) shouldBe 1.8
        OpeningFade.settles(Step.Standing) shouldBe 0.3

        Step.entries.forEach { step ->
            OpeningFade.layers(step = step, since = OpeningFade.settles(step)) shouldBe
                OpeningFade.layers(step = step, since = 1000.0)
            OpeningFade.layers(step = step, since = OpeningFade.settles(step) - 0.05) shouldNotBe
                OpeningFade.layers(step = step, since = 1000.0)
        }
    }
})
