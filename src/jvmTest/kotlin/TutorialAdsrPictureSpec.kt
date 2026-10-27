/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.pages.docs.tutorials.Block
import io.peekandpoke.klang.pages.docs.tutorials.TutorialSection

/** A tutorial's ADSR picture (KLANG-00371): the players it follows, and the shape it shows before a note sounds. */
class TutorialAdsrPictureSpec : StringSpec({

    val picture = Block.Visual.Adsr()

    "a picture follows every player right after it, up to the first block that is not one" {
        val section = TutorialSection(
            blocks = listOf(
                Block.Markdown("intro"),
                picture,
                Block.Code(code = "note(\"a3\")"),
                Block.Code(code = "note(\"c4\")"),
                Block.Code(lang = "text", code = "not a player"),
                Block.Code(code = "note(\"e4\")"),
                picture,
                Block.Markdown("between"),
                Block.Code(code = "note(\"g4\")"),
            ),
        )

        section.playersAfterPictures() shouldBe mapOf(2 to 1, 3 to 1)
    }

    "without values of its own: what the example writes, on any of its lines, its comments aside" {
        val example = """
            note("a3 ~ c4 ~")          // the leap
              .sound("saw")            // .adsr(9, 9, 9, 9) only named in a remark
              .adsr(0.3, 0.1, 1, 0.05) // a slow attack
        """.trimIndent()

        picture.numbers(example = example) shouldBe listOf(0.3, 0.1, 1.0, 0.05)
    }

    "an example that writes no envelope gives a picture nothing to show: it has none (lint-enforced)" {
        picture.numbers(example = "note(\"a3\")\n  .sound(\"saw\")  // .adsr(9, 9, 9, 9) in a remark") shouldBe null
    }

    "its curves: what the example writes in adsrCurves, by place or by name; the default for the rest" {
        val exp = AdsrCurve.Exponential

        picture.curves(example = "note(\"a3\")\n  .adsr(0.3, 0.1, 1, 0.05)") shouldBe listOf(exp, exp, exp)
        picture.curves(example = "note(\"a3\")\n  .adsrCurves(\"linear\", \"cube\", \"scurve\")  // three") shouldBe
            listOf(AdsrCurve.Linear, AdsrCurve.Cube, AdsrCurve.SCurve)
        picture.curves(example = "note(\"a3\").adsrCurves(release = \"invsquare\")") shouldBe listOf(exp, exp, AdsrCurve.InvSquare)
        picture.curves(example = "note(\"a3\")  // .adsrCurves(\"linear\") in a remark") shouldBe listOf(exp, exp, exp)
    }

    "values of its own win; four numbers or nothing" {
        Block.Visual.Adsr("0.001:0.4:0:0.05").numbers(example = ".adsr(0.3, 0.1, 1, 0.05)") shouldBe listOf(0.001, 0.4, 0.0, 0.05)
        Block.Visual.Adsr("0.001:0.4:0").numbers(example = null) shouldBe null
        picture.numbers(example = "note(\"a3\").adsr(0.3, slow)") shouldBe null
    }
})
