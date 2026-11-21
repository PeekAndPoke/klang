/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.triggers

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.Timed

/** A note of a part: only when it begins. */
private class PausePlay(override val t: Double) : Timed

class PartPauseSpec : StringSpec({

    val part = listOf(PausePlay(2.0), PausePlay(5.0), PausePlay(9.0))

    "the pause a moment lies in: from the last note begun to the next, open before the first and after the last" {
        PartPause.at(plays = part, t = 6.0) shouldBe PartPause(from = 5.0, until = 9.0)
        PartPause.at(plays = part, t = 1.0) shouldBe PartPause(from = null, until = 2.0)
        PartPause.at(plays = part, t = 9.5) shouldBe PartPause(from = 9.0, until = null)
        PartPause.at(plays = emptyList(), t = 3.0) shouldBe PartPause(from = null, until = null)

        withClue("a note that begins at the moment has begun") {
            PartPause.at(plays = part, t = 5.0) shouldBe PartPause(from = 5.0, until = 9.0)
        }
    }

    "a pause lasts until a moment where no note begins before it, one just at it allowed" {
        val pause = PartPause(from = 5.0, until = 9.0)

        listOf(8.0, 9.0, 9.01).map { pause.lastsUntil(it) } shouldBe listOf(true, true, false)
        PartPause(from = 9.0, until = null).lastsUntil(1000.0) shouldBe true
    }
})
