/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.ui.feel.KlangLookLayers.Layer
import io.peekandpoke.klang.ui.feel.KlangLookPreset
import io.peekandpoke.klang.ui.feel.KlangLookShelf
import io.peekandpoke.klang.ui.feel.KlangLooks
import io.peekandpoke.klang.ui.feel.KlangRole
import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.kraft.testing.TestBed
import io.peekandpoke.kraft.testing.attr
import io.peekandpoke.kraft.testing.eventually
import io.peekandpoke.kraft.testing.selectCss
import org.w3c.dom.HTMLElement

/** The look picker (KLANG-00339, KLANG-00382): the equipped looks, the chosen one pressed, a press chooses. */
class KlangLookPickerSpec : StringSpec({

    afterTest {
        KlangTheme.choose(KlangLooks.Default)
        KlangLookShelf.restore(own = emptyList(), slots = null)
    }

    "it shows the equipped looks, the chosen one pressed, the empty slot as its dashed place" {
        KlangTheme.choose(KlangLooks.Cream)

        TestBed.preact(view = { KlangLookPicker() }) { root ->
            root.selectCss("[role=group]").attr("aria-label") shouldContainExactly listOf("Look")

            val buttons = root.selectCss("button")

            buttons.map { it.textContent } shouldContainExactly listOf("Default", "Copper", "Cream", "Army", "Forest", "Sun", "Asphalt")
            buttons.attr("aria-pressed") shouldContainExactly listOf("false", "false", "true", "false", "false", "false", "false")
        }
    }

    "a press chooses the look and hands it on" {
        val handed = mutableListOf<KlangLookPreset>()

        TestBed.preact(view = { KlangLookPicker(onChosen = { handed.add(it) }) }) { root ->
            (root.selectCss("button")[1] as HTMLElement).click()

            eventually {
                KlangTheme.chosen shouldBe KlangLooks.Copper
                root.selectCss("button").attr("aria-pressed") shouldContainExactly listOf("false", "true", "false", "false", "false", "false", "false")
            }

            handed shouldContainExactly listOf(KlangLooks.Copper)
        }
    }

    "with an editor, the Edit key opens it, an empty slot opens it with its index" {
        val edits = mutableListOf<Int?>()

        KlangLookShelf.restore(own = emptyList(), slots = listOf("default", null, "sun"))

        TestBed.preact(view = { KlangLookPicker(onEdit = { edits.add(it) }) }) { root ->
            val buttons = root.selectCss("button")

            buttons.map { it.textContent?.trim() } shouldContainExactly
                listOf("\u270EEdit", "Default", "+", "Sun", "+", "+", "+", "+", "+")

            (buttons[0] as HTMLElement).click()
            (buttons[2] as HTMLElement).click()
            (buttons[8] as HTMLElement).click()

            edits shouldContainExactly listOf(null, 1, 7)
        }
    }

    "a look of the user's own in a slot shows and is chosen as any other" {
        val rust = KlangLookPreset(id = "own-1", name = "Rust", values = mapOf(KlangRole.Accent to "#aa5533"), own = true)

        KlangLookShelf.restore(own = listOf(rust), slots = listOf("own-1"))

        TestBed.preact(view = { KlangLookPicker() }) { root ->
            (root.selectCss("button")[0] as HTMLElement).click()

            eventually {
                KlangTheme.chosen shouldBe rust
                KlangTheme.layers[Layer.User] shouldBe rust.values
            }
        }
    }

    "the beacon lights the look it names, or the Edit key" {
        val beacon = KlangControlStyles.beacon.toString()

        TestBed.preact(view = { KlangLookPicker(onEdit = {}, beacon = "sun") }) { root ->
            val lit = root.selectCss("button").filter { it.classList.contains(beacon) }

            lit.map { it.textContent?.trim() } shouldContainExactly listOf("Sun")
        }

        TestBed.preact(view = { KlangLookPicker(onEdit = {}, beacon = KlangLookPicker.EDIT) }) { root ->
            val lit = root.selectCss("button").filter { it.classList.contains(beacon) }

            lit.map { it.textContent?.trim() } shouldContainExactly listOf("\u270EEdit")
        }
    }
})
