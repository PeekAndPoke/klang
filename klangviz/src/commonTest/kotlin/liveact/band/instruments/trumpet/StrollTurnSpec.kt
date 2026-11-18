/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.trumpet

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import kotlin.math.PI

class StrollTurnSpec : StringSpec({

    "she turns to where she wants to face, never faster than a calm turn, and stays there" {
        val turn = StrollTurn(from = 10.0, to = 60.0) { 2.0 }

        withClue("a second in: 57 degrees a second at most (eleven steps are worked out by then)") {
            turn.at(11.0) shouldBe (1.1 plusOrMinus 1e-9)
        }

        withClue("there after a while") {
            turn.at(30.0) shouldBe (2.0 plusOrMinus 1e-3)
        }
    }

    "she turns by the shorter way round: from nearly behind on one side to nearly behind on the other, through behind" {
        val turn = StrollTurn(from = 0.0, to = 60.0) { x -> if (x < 20.0) 3.0 else -3.0 }

        turn.at(19.9) shouldBe (3.0 plusOrMinus 1e-3)

        withClue("on through behind her (a little more than 3.0), not back through the front") {
            turn.at(30.0) shouldBe (2 * PI - 3.0 plusOrMinus 1e-3)
        }
    }

    "a long stroll is followed to its end: an hour in she still turns with her way" {
        val turn = StrollTurn(from = 15.0, to = 3615.0) { x -> if (x < 3000.0) 0.0 else 1.0 }

        withClue("before her way turns") {
            turn.at(2990.0) shouldBe (0.0 plusOrMinus 1e-9)
        }

        withClue("after 25 minutes of one stroll the turn stayed where it was") {
            turn.at(3050.0) shouldBe (1.0 plusOrMinus 1e-3)
        }
    }

    "a stroll that ends before it begins, or as it begins, has no turn and does not fail" {
        withClue("ended four seconds before it began") {
            StrollTurn(from = 112.0, to = 108.0) { 2.0 }.let { turn ->
                listOf(108.0, 111.0, 112.5).map { turn.at(it) } shouldBe listOf(0.0, 0.0, 0.0)
            }
        }

        withClue("ended as it began, or a step before") {
            StrollTurn(from = 5.0, to = 5.0) { 2.0 }.at(5.0) shouldBe 0.0
            StrollTurn(from = 5.0, to = 4.9) { 2.0 }.at(5.0) shouldBe 0.0
        }
    }
})
