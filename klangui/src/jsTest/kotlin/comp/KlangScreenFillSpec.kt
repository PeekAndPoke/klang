/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.ui.KlangKeyBindings
import io.peekandpoke.klang.workshop.keys.KeyPlatform
import io.peekandpoke.klang.workshop.keys.KeysManager
import io.peekandpoke.klang.workshop.keys.keys
import io.peekandpoke.kraft.components.ComponentRef
import io.peekandpoke.kraft.testing.TestBed
import io.peekandpoke.kraft.testing.eventually
import io.peekandpoke.ultra.html.css
import kotlinx.browser.document
import kotlinx.coroutines.delay
import kotlinx.html.div
import kotlinx.html.span
import org.w3c.dom.HTMLElement
import kotlin.math.abs

private val fillRef = ComponentRef.Tracker<KlangWorkbench>()

/**
 * A check for the future: the height chain of a page like the song page in the workbench (KLANG-00349), built here
 * from klangui's parts: the page, its form, the screen's frame, the screen, the editor's box filling it. With the
 * bottom panel pinned the editor's box still fills the screen. The double scroll bar the maintainer saw (2026-10-27)
 * came from the code highlights' canvas, which this cannot see; its fix lives in `CodeMirrorHighlightBuffer`.
 */
class KlangScreenFillSpec : StringSpec({

    "with the bottom panel pinned, what fills the screen fills it still" {
        val manager = KeysManager(platform = KeyPlatform.Pc, hintDelayMs = 10).apply { define(KlangKeyBindings) }

        try {
            TestBed.preact(
                appSetup = { keys(manager) },
                view = {
                    div {
                        css { put("height", "800px") }

                        KlangWorkbench(
                            column = {},
                            motor = { span { +"MOTOR" } },
                            content = {
                                // The page and its form, as the song page draws them
                                div {
                                    css {
                                        put("display", "flex")
                                        put("flex-direction", "column")
                                        put("height", "100%")
                                    }

                                    div {
                                        css {
                                            put("display", "flex")
                                            put("flex-direction", "column")
                                            put("flex", "1 1 auto")
                                            put("min-height", "0")
                                        }

                                        div { css { put("height", "48px") } }

                                        div {
                                            css {
                                                put("flex", "1 1 auto")
                                                put("min-height", "0")
                                                put("display", "flex")
                                                put("flex-direction", "column")
                                                put("margin", "0 10px 10px")
                                            }

                                            KlangScreen(size = KlangScreen.Size.Large) {
                                                div {
                                                    attributes["data-test"] = "fill"
                                                    css {
                                                        put("flex", "1 1 auto")
                                                        put("min-height", "0")
                                                        put("overflow", "hidden")
                                                        put("display", "flex")
                                                        put("flex-direction", "column")
                                                    }

                                                    div {
                                                        attributes["data-test"] = "editor"
                                                        css { put("height", "100%") }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            bottom = KlangWorkbench.Bottom(title = "Quick help", icon = { question_circle }) { +"HELP" },
                        ).track(fillRef)
                    }
                },
            ) { root ->
                fun h(test: String) = (document.querySelector("[data-test=$test]") as HTMLElement).getBoundingClientRect().height

                eventually { (h("editor") > 0.0) shouldBe true }

                fillRef { it.showBottom() }
                eventually { (document.querySelector("button[aria-label='Pin under the editor']") != null) shouldBe true }
                (document.querySelector("button[aria-label='Pin under the editor']") as HTMLElement).click()
                delay(100)

                (abs(h("editor") - h("fill")) < 1.0) shouldBe true
            }
        } finally {
            manager.dispose()
        }
    }
})
