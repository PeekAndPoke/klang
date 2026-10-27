/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.kraft.testing.TestBed
import kotlinx.browser.window
import kotlinx.css.px
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement

/** The spinner (KLANG-00347): what screen readers find, its size, a ring that turns. */
class KlangSpinnerSpec : StringSpec({

    "a status whose text is its label, at its size, its ring turning" {
        TestBed.preact(view = { KlangSpinner(label = "Warming up", size = 24.px) }) { root ->
            val spinner = root[0].querySelector("[role=status]") as HTMLElement

            spinner.textContent shouldBe "Warming up"
            spinner.style.width shouldBe "24px"

            val ring = spinner.querySelector("svg")!!

            window.getComputedStyle(ring).animationName shouldBe "kl-spin"
        }
    }

    "its arc is in the ink of the text around it, so it shows on a silver button too" {
        TestBed.preact(view = { KlangSpinner(label = "Loading", size = 14.px) }) { root ->
            val spinner = root[0].querySelector("[role=status]") as HTMLElement

            spinner.style.color = "rgb(17, 17, 17)"

            val arc = spinner.querySelectorAll("circle").item(1) as Element

            window.getComputedStyle(arc).getPropertyValue("stroke") shouldBe "rgb(17, 17, 17)"
        }
    }
})
