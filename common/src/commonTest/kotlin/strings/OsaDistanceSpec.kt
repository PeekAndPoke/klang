/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.common.strings

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * [osaDistance] exists for one reason: a transposition must cost ONE edit, so "did you mean" can
 * suggest `ignp` for `ingp` at a threshold tight enough to stay useful on short names.
 */
class OsaDistanceSpec : StringSpec({

    "an adjacent transposition costs ONE edit (plain Levenshtein charges two)" {
        "ingp".osaDistance("ignp") shouldBe 1
        "gian".osaDistance("gain") shouldBe 1
        // The contrast that motivates this function existing at all: plain Levenshtein scores both pairs 2.
    }

    "the ordinary edits still cost one each" {
        "lpff".osaDistance("lpf") shouldBe 1
        "lpf".osaDistance("lpff") shouldBe 1
        "lqf".osaDistance("lpf") shouldBe 1
    }

    "identical strings are distance zero" {
        "ignp".osaDistance("ignp") shouldBe 0
        "".osaDistance("") shouldBe 0
    }

    "an empty side costs the other side's length" {
        "".osaDistance("gain") shouldBe 4
        "gain".osaDistance("") shouldBe 4
    }

    "it is symmetric" {
        "ingp".osaDistance("ignp") shouldBe "ignp".osaDistance("ingp")
        "kitten".osaDistance("sitting") shouldBe "sitting".osaDistance("kitten")
    }

    "unrelated names stay far apart" {
        "zzzzqqq".osaDistance("gain") shouldBe 7
    }

    "a NON-adjacent swap is not a transposition and costs two" {
        // Guards the adjacency condition: only neighbours may swap for one edit.
        "abc".osaDistance("cba") shouldBe 2
    }
})
