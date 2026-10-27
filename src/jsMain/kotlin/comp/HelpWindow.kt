/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.script.intel.LocalUsage
import io.peekandpoke.klang.script.types.KlangSymbol
import io.peekandpoke.klang.storage.EditorSettings
import io.peekandpoke.klang.ui.comp.KlangKeyHint
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.streams.Stream
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div

@Suppress("FunctionName")
fun Tag.HelpWindow(
    entry: Stream<HelpWindow.Entry?>,
    onJump: (pos: Int) -> Unit,
    options: (FlowContent.() -> Unit)? = null,
    onLexikon: ((slug: String) -> Unit)? = null,
) = comp(
    HelpWindow.Props(entry = entry, onJump = onJump, options = options, onLexikon = onLexikon)
) {
    HelpWindow(it)
}

/**
 * The help of a name, in the song page's Quick help panel (KLANG-00349; the maintainer: Alt+Q on a name "would open
 * the help panel as designed"): the same docs as the editor's quick help, in the bottom panel under the editor, wide
 * enough for signatures and parameters (the maintainer, 2026-10-27: "always shown in the help" panel, never as the
 * popup, and the bottom rather than the right side). Alt+Q and a hover fill it. The host's [Props.options] (how to
 * call it, the hover's switch) sit at the end of a name's headline, so no row of their own takes room above it; before
 * the first name they stand alone at the top, over what to do.
 */
class HelpWindow(ctx: Ctx<Props>) : Component<HelpWindow.Props>(ctx) {

    /** A name's help: its [symbol], a local's [declarationCode] and [usages] (null and none for a library name). */
    data class Entry(val symbol: KlangSymbol, val declarationCode: String?, val usages: List<LocalUsage> = emptyList())

    data class Props(
        val entry: Stream<Entry?>,
        /** Puts the cursor in the code at an offset: a use clicked in the "Used on" list. */
        val onJump: (pos: Int) -> Unit,
        /** The host's options for the help, at the right of the headline. */
        val options: (FlowContent.() -> Unit)?,
        /** Gets a Lexikon link clicked in the help, in place of the Lexikon page; null, the link goes there. */
        val onLexikon: ((slug: String) -> Unit)?,
    )

    private val entry by subscribingTo(props.entry)

    /** Whether a hover fills the help: the empty state says only what works. */
    private val hoverHelp by subscribingTo(EditorSettings.hoverHelp)

    override fun VDom.render() {
        val shown = entry

        if (shown == null) {
            props.options?.let { options ->
                div {
                    css {
                        put("display", "flex")
                        put("justify-content", "flex-end")
                        put("align-items", "center")
                        put("gap", "12px")
                    }

                    options()
                }
            }

            // Before the first name: how to call it, in the middle of the panel, the first thing a new user reads
            // there (the maintainer, 2026-10-27: "more prominent and centered in the tool")
            div {
                css {
                    put("display", "flex")
                    put("flex-direction", "column")
                    put("align-items", "center")
                    put("justify-content", "center")
                    put("gap", "10px")
                    // Room above and below, but never more than the panel's least height holds (no scroll bar)
                    put("padding", "32px 0")
                    put("text-align", "center")
                }

                div {
                    css {
                        put("font-size", "18px")
                        put("color", "var(--kl-ink-strong)")
                    }

                    +if (hoverHelp) "Rest the mouse on a name in the code" else "Put the cursor on a name in the code"
                }

                div {
                    css {
                        put("font-size", "14px")
                        put("color", "var(--kl-ink-label)")
                    }

                    +if (hoverHelp) "or put the cursor on it and press " else "and press "
                    with(KlangKeyHint) { caps(parts = listOf("Alt", "Q")) }
                    +" and its help shows here."
                }
            }
        } else {
            div {
                key = "help-${shown.symbol.name}"

                QuickHelpView(
                    symbol = shown.symbol,
                    declarationCode = shown.declarationCode,
                    onNavigate = { doc, event -> navToSymbolDocs(doc = doc, event = event) },
                    usages = shown.usages,
                    onJump = props.onJump,
                    headlineEnd = props.options,
                    onLexikon = props.onLexikon,
                )
            }
        }
    }
}
