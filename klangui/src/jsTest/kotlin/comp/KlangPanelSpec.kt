/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.peekandpoke.kraft.testing.TestBed
import io.peekandpoke.kraft.testing.attr
import io.peekandpoke.kraft.testing.classes
import io.peekandpoke.kraft.testing.selectCss
import io.peekandpoke.kraft.testing.textContent

/** The riveted panel (KLANG-00337): a rivet in each corner, hidden from screen readers, around the content. */
class KlangPanelSpec : StringSpec({

    val styles = KlangControlStyles

    "a panel has one rivet in each corner and its content" {
        TestBed.preact(view = { KlangPanel { +"Move it?" } }) { root ->
            val panel = root.selectCss(".${styles.panel}")
            val rivets = root.selectCss(".${styles.rivet}")

            panel.textContent() shouldBe "Move it?"

            rivets.attr("aria-hidden") shouldContainExactly listOf("true", "true", "true", "true")
            rivets.classes().map { it - styles.rivet.toString() } shouldContainExactly listOf(
                setOf(styles.rivetTopLeft.toString()),
                setOf(styles.rivetTopRight.toString()),
                setOf(styles.rivetBottomLeft.toString()),
                setOf(styles.rivetBottomRight.toString()),
            )
        }
    }

    "without its bottom rivets the panel keeps the top pair" {
        TestBed.preact(view = { KlangPanel(bottomRivets = false) { +"Dialog" } }) { root ->
            root.selectCss(".${styles.rivet}").classes().map { it - styles.rivet.toString() } shouldContainExactly listOf(
                setOf(styles.rivetTopLeft.toString()),
                setOf(styles.rivetTopRight.toString()),
            )
        }
    }
})
