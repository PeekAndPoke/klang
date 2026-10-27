/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.kraft.testing.TestBed
import io.peekandpoke.kraft.testing.selectCss
import kotlinx.browser.window
import kotlinx.html.span
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.NodeList

/** The screen (KLANG-00349): its content inside the well, its four cut corners laid over it and out of the way. */
class KlangScreenSpec : StringSpec({

    val styles = KlangControlStyles

    "the content sits in the screen's surface, on the surface's colour" {
        TestBed.preact(view = { KlangScreen(surface = "rgb(1, 2, 3)") { span { +"CODE" } } }) { root ->
            val surface = root.selectCss(".${styles.screenSurface}").single() as HTMLElement

            surface.textContent shouldBe "CODE"
            surface.style.background shouldBe "rgb(1, 2, 3)"
        }
    }

    "four corners, cut by the size's amount, laid over the content, never catching the mouse, hidden from readers" {
        TestBed.preact(
            view = {
                KlangScreen(size = KlangScreen.Size.Small) { span { +"a" } }
                KlangScreen(size = KlangScreen.Size.Large) { span { +"b" } }
            },
        ) { root ->
            val screens = root.selectCss(".${styles.screen}")

            screens.map { cornerWidths(it) } shouldContainExactly listOf(List(4) { "8px" }, List(4) { "14px" })

            for (corner in screens[1].querySelectorAll(".${styles.screenCorner}").asList()) {
                val style = window.getComputedStyle(corner)

                style.getPropertyValue("pointer-events") shouldBe "none"
                style.position shouldBe "absolute"
                corner.getAttribute("aria-hidden") shouldBe "true"
            }

            // The content is not a child of a corner: the corners are laid over it
            screens[1].querySelector(".${styles.screenCorner} .${styles.screenContent}") shouldBe null
            screens[1].querySelector(".${styles.screenContent}") shouldNotBe null
        }
    }

    "the corners take the bezel's colour, the ground round the screen" {
        TestBed.preact(view = { KlangScreen(bezel = "rgb(9, 8, 7)") { span { +"x" } } }) { root ->
            val screen = root.selectCss(".${styles.screen}").single() as HTMLElement

            screen.style.getPropertyValue("--kl-screen-bezel") shouldBe "rgb(9, 8, 7)"
        }
    }
})

private fun cornerWidths(screen: Element): List<String> =
    screen.querySelectorAll(".${KlangControlStyles.screenCorner}").asList().map {
        window.getComputedStyle(it as Element).width
    }

private fun NodeList.asList(): List<Element> = (0 until length).mapNotNull { item(it) as? Element }
