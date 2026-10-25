/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages

import io.peekandpoke.klang.Nav
import io.peekandpoke.klang.comp.Motoer
import io.peekandpoke.klang.comp.PlayerWarehouseStats
import io.peekandpoke.klang.storage.LookStorage
import io.peekandpoke.klang.ui.comp.KlangCaption
import io.peekandpoke.klang.ui.comp.KlangCard
import io.peekandpoke.klang.ui.comp.KlangControlStyles
import io.peekandpoke.klang.ui.comp.KlangDrawer
import io.peekandpoke.klang.ui.comp.KlangLookPicker
import io.peekandpoke.klang.ui.comp.KlangSearchField
import io.peekandpoke.klang.ui.comp.KlangWorkbench
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.routing.Router.Companion.router
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import kotlinx.html.DIV
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.ul

@Suppress("FunctionName")
fun Tag.WorkbenchLabPage() = comp {
    WorkbenchLabPage(it)
}

/**
 * The workbench in the lab (KLANG-00340, `/debug/workbench`): the shell of layout A with the real Motor and sample parts,
 * to try before the app's pages move into it.
 *
 * The drawer's destinations navigate for real; the column, the tool windows and the bottom panel hold samples.
 */
class WorkbenchLabPage(ctx: NoProps) : PureComponent(ctx) {

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private var query: String by value("")

    //  DESTINATIONS  ///////////////////////////////////////////////////////////////////////////////////////////

    private val entries = listOf(
        KlangDrawer.Entry(id = "songs", label = "Songs", sub = "Yours and the built-ins"),
        KlangDrawer.Entry(id = "learn", label = "Learn", sub = "Tutorials and tracks"),
        KlangDrawer.Entry(id = "docs", label = "Docs", sub = "Lexikon, Sprudel, Stdlib"),
        KlangDrawer.Entry(id = "samples", label = "Samples", sub = "The library"),
        KlangDrawer.Entry(id = "midi", label = "MIDI", sub = "Playground, devices"),
        KlangDrawer.Entry(id = "credits", label = "Credits", sub = "Who we build on"),
        KlangDrawer.Entry(id = "community", label = "Community", sub = "With the backend", later = true),
        KlangDrawer.Entry(id = "account", label = "Account", sub = "With the backend", later = true),
    )

    private fun go(id: String) {
        val uri = when (id) {
            "songs" -> Nav.newSongCode()
            "learn" -> Nav.tutorials()
            "docs" -> Nav.manuals()
            "samples" -> Nav.samplesLibrary()
            "midi" -> Nav.midiPlayground()
            "credits" -> Nav.credits()
            else -> return
        }

        router.navToUri(uri)
    }

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        div {
            key = "workbench-lab"
            css {
                put("width", "100%")
                put("height", "100vh")
            }

            KlangWorkbench(
                column = { renderProject() },
                motor = { menu -> Motoer(showTitle = false, menu = menu) },
                content = { renderContent() },
                tools = listOf(
                    KlangWorkbench.Tool(id = "docs", title = "Docs", icon = { book }, chip = "follows the cursor") {
                        p {
                            css { put("margin", "0 0 10px") }
                            +"The lowpass filter: cutoff, resonance, cascade and the cutoff envelope."
                        }
                        p {
                            css { put("margin", "0") }
                            +"Only frequencies below the cutoff pass, so lower values sound darker."
                        }
                    },
                    KlangWorkbench.Tool(id = "keys", title = "Keys", icon = { keyboard }, chip = "this page") {
                        ul {
                            css {
                                put("margin", "0")
                                put("padding-left", "18px")
                                put("line-height", "24px")
                            }
                            li { +"Play / Update: Mod Shift Enter" }
                            li { +"Stop: Mod Shift ." }
                            li { +"Search everywhere: Mod K, Shift Shift (coming)" }
                        }
                    },
                    KlangWorkbench.Tool(id = "motor", title = "Motor", icon = { tachometer_alternate }, chip = "warehouse") {
                        PlayerWarehouseStats()
                    },
                ),
                bottom = KlangWorkbench.Bottom(title = "Analysis", icon = { chart_bar }) {
                    p {
                        css { put("margin", "0") }
                        +"The mix curve of KLANG-00196 goes here: bands integrated over cycles, relative to pink noise."
                    }
                },
                drawer = { close ->
                    KlangDrawer(
                        title = "Where to",
                        entries = entries,
                        current = null,
                        onPick = { id ->
                            close()
                            go(id)
                        },
                        onClose = close,
                        search = {
                            KlangSearchField(
                                label = "Search songs, docs, samples and actions",
                                value = query,
                                onInput = { query = it },
                                placeholder = "Songs, docs, samples, actions",
                                keyHint = "Mod K",
                            )
                        },
                        footer = { KlangLookPicker(onChosen = { LookStorage.save(it) }) },
                    )
                },
                mark = "KLANGMOTOR",
            )
        }
    }

    private fun DIV.renderProject() {
        div {
            css { put("padding", "18px") }

            div(classes = KlangControlStyles.heading.toString()) { +"Project" }

            div {
                css {
                    put("margin", "8px 0 4px")
                    put("font-size", "18px")
                    put("font-weight", "700")
                    put("color", "var(--kl-ink-strong)")
                }
                +"Der Schmetterling"
            }

            // Enough rows to show the column scrolling on its own while the Motor stays put
            for (file in listOf("main.klang", "guitar-rig.klang", "drums.klang") + (1..30).map { "sketch-$it.klang" }) {
                div {
                    css {
                        put("padding", "6px 8px")
                        put("font-family", "monospace")
                        put("font-size", "13px")
                    }
                    +file
                }
            }
        }
    }

    private fun DIV.renderContent() {
        div {
            css {
                put("max-width", "720px")
                put("margin", "0 auto")
                put("padding", "36px 24px")
            }

            h1 {
                css { put("color", "var(--kl-ink-strong)") }
                +"The workbench, in the lab"
            }

            KlangCard {
                p {
                    css { put("margin", "0") }
                    +"The Motor sits bottom left and never moves; the column above it scrolls. The strip on the right opens the"
                    +" tool windows (they fly in, the pin docks them) and the bottom panel. The Motor's menu button opens the"
                    +" drawer: its destinations lead into today's app, its looks change the look and keep it."
                }
                KlangCaption(title = "Layout A of the canvas", note = "KLANG-00340, klangui/comp/KlangWorkbench.kt")
            }
        }
    }
}
