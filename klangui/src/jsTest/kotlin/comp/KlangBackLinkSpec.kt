/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.peekandpoke.kraft.testing.TestBed
import kotlinx.browser.window
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.MouseEvent
import org.w3c.dom.events.MouseEventInit

/** The way back (KLANG-00370): a real link with the place's name; a plain click goes in the app, a key held its own way. */
class KlangBackLinkSpec : StringSpec({

    "it links to the place by name; a plain click navigates in the app, with Ctrl the browser has it" {
        val navigated = mutableListOf<String>()

        TestBed.preact(view = { KlangBackLink(label = "Learn", href = "#/learn", onNavigate = { navigated.add("learn") }) }) { root ->
            val link = root.single().querySelector("a") as? HTMLAnchorElement ?: root.single() as HTMLAnchorElement

            link.textContent shouldBe "Learn"
            link.getAttribute("href") shouldBe "#/learn"

            // Stopped at the window as well, so the test page never leaves; the link's own stop is what is asked
            val stoppedByLink = mutableListOf<Boolean>()
            val stopper: (Event) -> Unit = { event ->
                stoppedByLink.add(event.defaultPrevented)
                event.preventDefault()
            }

            window.addEventListener("click", stopper)

            try {
                link.dispatchEvent(MouseEvent("click", MouseEventInit(bubbles = true, cancelable = true)))
                link.dispatchEvent(MouseEvent("click", MouseEventInit(bubbles = true, cancelable = true, ctrlKey = true)))
            } finally {
                window.removeEventListener("click", stopper)
            }

            navigated shouldContainExactly listOf("learn")
            stoppedByLink shouldContainExactly listOf(true, false)
        }
    }
})
