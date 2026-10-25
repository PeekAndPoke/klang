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
import io.peekandpoke.kraft.testing.selectCss
import io.peekandpoke.kraft.testing.textContent
import org.w3c.dom.EventInit
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event

/** The search field (KLANG-00339): a named search input showing its value, typing reported, a key cap printed. */
class KlangSearchFieldSpec : StringSpec({

    val styles = KlangControlStyles

    "a named search input with its value; typing goes to onInput; the key cap is printed, hidden from readers" {
        val typed = mutableListOf<String>()

        TestBed.preact(
            view = { KlangSearchField(label = "Search songs", value = "kok", onInput = { typed.add(it) }, keyHint = "Mod K") },
        ) { root ->
            val input = root.selectCss("input").single() as HTMLInputElement

            input.type shouldBe "search"
            input.getAttribute("aria-label") shouldBe "Search songs"
            input.value shouldBe "kok"

            input.value = "kokon"
            input.dispatchEvent(Event("input", EventInit(bubbles = true)))

            typed shouldContainExactly listOf("kokon")

            val cap = root.selectCss(".${styles.keyCap}")

            cap.textContent() shouldBe "Mod K"
            cap.attr("aria-hidden") shouldContainExactly listOf("true")
        }
    }

    "without a key hint there is no cap" {
        TestBed.preact(view = { KlangSearchField(label = "s", value = "", onInput = {}) }) { root ->
            root.selectCss(".${styles.keyCap}").size shouldBe 0
        }
    }
})
