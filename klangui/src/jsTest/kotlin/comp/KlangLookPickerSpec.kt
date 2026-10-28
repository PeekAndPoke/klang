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
import io.peekandpoke.klang.ui.feel.KlangLooks
import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.kraft.testing.TestBed
import io.peekandpoke.kraft.testing.attr
import io.peekandpoke.kraft.testing.eventually
import io.peekandpoke.kraft.testing.selectCss
import org.w3c.dom.HTMLElement

/** The look picker (KLANG-00339): the presets and "Custom" later, the chosen one pressed, a press chooses. */
class KlangLookPickerSpec : StringSpec({

    afterTest {
        KlangTheme.clearLayer(Layer.User)
    }

    "it shows the seven presets and Custom, the chosen one pressed, Custom not there yet" {
        KlangTheme.choose(KlangLooks.Cream)

        TestBed.preact(view = { KlangLookPicker() }) { root ->
            root.selectCss("[role=group]").attr("aria-label") shouldContainExactly listOf("Look")

            val buttons = root.selectCss("button")

            buttons.map { it.textContent } shouldContainExactly listOf("Default", "Copper", "Cream", "Army", "Forest", "Sun", "Asphalt", "Custom")
            buttons.attr("aria-pressed") shouldContainExactly listOf("false", "false", "true", "false", "false", "false", "false", null)
            buttons.attr("aria-disabled") shouldContainExactly listOf(null, null, null, null, null, null, null, "true")
        }
    }

    "a press chooses the preset and hands it on; Custom chooses nothing" {
        val handed = mutableListOf<KlangLookPreset>()

        TestBed.preact(view = { KlangLookPicker(onChosen = { handed.add(it) }) }) { root ->
            val buttons = root.selectCss("button")

            (buttons[1] as HTMLElement).click()

            eventually {
                KlangTheme.chosen shouldBe KlangLooks.Copper
                root.selectCss("button").attr("aria-pressed") shouldContainExactly listOf("false", "true", "false", "false", "false", "false", "false", null)
            }

            handed shouldContainExactly listOf(KlangLooks.Copper)

            (root.selectCss("button")[7] as HTMLElement).click()

            KlangTheme.chosen shouldBe KlangLooks.Copper
            handed.size shouldBe 1
        }
    }
})
