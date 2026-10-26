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
})
