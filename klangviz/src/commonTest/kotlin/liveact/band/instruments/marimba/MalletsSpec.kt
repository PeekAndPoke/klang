/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.marimba

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class MalletsSpec : StringSpec({

    "none and one" {
        malletShare(places = emptyList(), reach = 0.3) shouldBe MalletShare(low = emptyList(), high = emptyList())
        malletShare(places = listOf(0.4), reach = 0.3) shouldBe MalletShare(low = listOf(0), high = emptyList())
    }

    "two notes: one for each hand, the lower for the hand on the low notes, in whatever order they come" {
        malletShare(places = listOf(0.1, 0.3), reach = 0.3) shouldBe MalletShare(low = listOf(0), high = listOf(1))
        malletShare(places = listOf(0.3, 0.1), reach = 0.3) shouldBe MalletShare(low = listOf(1), high = listOf(0))
    }

    "three: the two that lie nearer together go to one hand" {
        malletShare(places = listOf(0.0, 0.1, 0.5), reach = 0.3) shouldBe MalletShare(low = listOf(0, 1), high = listOf(2))
        malletShare(places = listOf(0.0, 0.4, 0.5), reach = 0.3) shouldBe MalletShare(low = listOf(0), high = listOf(1, 2))
        malletShare(places = listOf(0.5, 0.0, 0.4), reach = 0.3) shouldBe MalletShare(low = listOf(1), high = listOf(2, 0))
    }

    "four: two each; of more, the two lowest and the two highest" {
        malletShare(places = listOf(0.0, 0.1, 0.5, 0.6), reach = 0.3) shouldBe MalletShare(low = listOf(0, 1), high = listOf(2, 3))
        malletShare(places = listOf(0.0, 0.1, 0.3, 0.5, 0.6), reach = 0.3) shouldBe MalletShare(low = listOf(0, 1), high = listOf(3, 4))
    }

    "two notes wider apart than a hand's mallets open: the hand plays the outer one alone" {
        malletShare(places = listOf(0.0, 0.5, 0.6, 1.2), reach = 0.3) shouldBe MalletShare(low = listOf(0), high = listOf(3))
        malletShare(places = listOf(0.0, 0.4, 1.0), reach = 0.3) shouldBe MalletShare(low = listOf(0), high = listOf(2))
        malletShare(places = listOf(0.0, 0.6, 1.0), reach = 0.3) shouldBe MalletShare(low = listOf(0), high = listOf(2))
        // just within it: both
        malletShare(places = listOf(0.0, 0.3, 1.0), reach = 0.3) shouldBe MalletShare(low = listOf(0, 1), high = listOf(2))
    }
})
