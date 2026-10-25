/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.feel

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.ui.feel.KlangLookLayers.Layer
import kotlinx.browser.document
import org.w3c.dom.HTMLElement

/** The theme puts the look on the page (KLANG-00337): each role as a CSS variable on `:root`, and on its stream. */
class KlangThemeLookSpec : StringSpec({

    fun rootVar(name: String): String = (document.documentElement as HTMLElement).style.getPropertyValue(name)

    afterTest {
        for (layer in Layer.entries) {
            KlangTheme.clearLayer(layer)
        }
    }

    "initialize writes every role to :root" {
        val style = (document.documentElement as HTMLElement).style

        for (role in KlangRole.entries) {
            style.removeProperty(role.cssVar)
        }

        KlangTheme.initialize()

        for (role in KlangRole.entries) {
            rootVar(role.cssVar) shouldBe role.default
        }
    }

    "a layer's value reaches :root, and clearing the layer brings the default back" {
        KlangTheme.setLayer(Layer.User, mapOf(KlangRole.Accent to "#123456"))

        rootVar(KlangRole.Accent.cssVar) shouldBe "#123456"
        rootVar(KlangRole.InkText.cssVar) shouldBe KlangRole.InkText.default

        KlangTheme.clearLayer(Layer.User)

        rootVar(KlangRole.Accent.cssVar) shouldBe KlangRole.Accent.default
    }

    "a value its role does not accept never reaches :root" {
        KlangTheme.setLayer(Layer.User, mapOf(KlangRole.Accent to "red; color: blue"))

        rootVar(KlangRole.Accent.cssVar) shouldBe KlangRole.Accent.default
        KlangTheme.layers[Layer.User] shouldBe emptyMap()
    }

    "the look stream carries the resolved look" {
        var seen: KlangLook? = null
        val unsubscribe = KlangTheme.look.subscribeToStream { seen = it }

        try {
            KlangTheme.setLayer(Layer.Song, mapOf(KlangRole.LampOn to "#00ff00"))

            seen?.get(KlangRole.LampOn) shouldBe "#00ff00"
        } finally {
            unsubscribe()
        }
    }
})
