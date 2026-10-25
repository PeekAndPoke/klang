/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.feel

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.ui.feel.KlangLookLayers.Layer

/** The built-in looks (KLANG-00339): valid presets, found by id, chosen onto the user's layer. */
class KlangLooksSpec : StringSpec({

    afterTest {
        KlangTheme.clearLayer(Layer.User)
    }

    "the presets are Default, Copper and Cream, each found by its id; an unknown id finds none" {
        KlangLooks.all.map { it.id } shouldContainExactly listOf("default", "copper", "cream")

        for (preset in KlangLooks.all) {
            KlangLooks.byId(preset.id) shouldBe preset
        }

        KlangLooks.byId("neon") shouldBe null
    }

    "every value of every preset fits its role, so none is dropped when it is chosen" {
        for (preset in KlangLooks.all) {
            for ((role, value) in preset.values) {
                withClue("${preset.id}: ${role.name} = $value") {
                    role.accepts(value) shouldBe true
                }
            }
        }
    }

    "choosing a preset puts it on the user's layer and makes it the chosen one; Default clears the layer" {
        KlangTheme.choose(KlangLooks.Copper)

        KlangTheme.layers[Layer.User] shouldBe KlangLooks.Copper.values
        KlangTheme.chosen shouldBe KlangLooks.Copper

        KlangTheme.choose(KlangLooks.Default)

        KlangTheme.layers[Layer.User] shouldBe emptyMap()
        KlangTheme.chosen shouldBe KlangLooks.Default
    }

    "a user's layer that matches no preset has none chosen" {
        KlangTheme.setLayer(Layer.User, mapOf(KlangRole.Accent to "#123456"))

        KlangTheme.chosen shouldBe null
    }

    "a swatch is the preset's metal, lit to dark, falling back to the defaults where it has none or a bad one" {
        KlangLooks.Copper.swatch shouldContainExactly listOf("#f6d6bd", "#c98a5e", "#7a4a2c")

        KlangLooks.Default.swatch shouldContainExactly listOf(
            KlangRole.MetalLight.default,
            KlangRole.MetalMid.default,
            KlangRole.MetalDark.default,
        )

        val odd = KlangLookPreset(
            id = "odd",
            name = "Odd",
            values = mapOf(KlangRole.MetalLight to "url(x)", KlangRole.MetalDark to "#000000"),
        )

        odd.swatch shouldContainExactly listOf(KlangRole.MetalLight.default, KlangRole.MetalMid.default, "#000000")
    }
})
