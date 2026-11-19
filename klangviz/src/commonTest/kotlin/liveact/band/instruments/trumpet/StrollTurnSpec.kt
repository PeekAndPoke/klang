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
import kotlin.math.sin

class StrollTurnSpec : StringSpec({

    "she turns to where she wants to face, never faster than a body walking a bend, and stays there" {
        val turn = StrollTurn(from = 10.0, to = 60.0) { 2.0 }

        withClue("half a second in: 120 degrees a second at most (six steps are worked out by then)") {
            turn.at(10.5) shouldBe (1.26 plusOrMinus 1e-9)
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

    "a turn handed over to a stroll that replaces its own (its end changed) is the same at the hand-over, then follows the new way" {
        val old = StrollTurn(from = 10.0, to = 70.0) { x -> 1.5 * sin(x / 3) }
        val new = StrollTurn(from = 10.0, to = 50.0, handOver = old, at = 23.37) { -1.0 }

        withClue("at the hand-over, and before it") {
            new.at(23.37) shouldBe old.at(23.37)
            new.at(17.0) shouldBe old.at(17.0)
        }

        withClue("well after it, where the new way wants it") {
            new.at(45.0) shouldBe (-1.0 plusOrMinus 1e-3)
        }

        withClue("a hand-over before the stroll begins takes nothing over") {
            StrollTurn(from = 10.0, to = 50.0, handOver = old, at = 5.0) { -1.0 }.at(10.5) shouldBe
                StrollTurn(from = 10.0, to = 50.0) { -1.0 }.at(10.5)
        }
    }
})
