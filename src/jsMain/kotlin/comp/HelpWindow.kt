/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.script.types.KlangSymbol
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.streams.Stream
import kotlinx.html.Tag
import kotlinx.html.div

@Suppress("FunctionName")
fun Tag.HelpWindow(
    entry: Stream<HelpWindow.Entry?>,
) = comp(
    HelpWindow.Props(entry = entry)
) {
    HelpWindow(it)
}

/**
 * The help of a name, in the song page's Help tool window (KLANG-00349; the maintainer: Alt+Q on a name "would open
 * the help panel as designed"): the same docs as the editor's quick help, which no longer shows on hover unless the
 * user wants it. Before the first Alt+Q it says how to call it.
 */
class HelpWindow(ctx: Ctx<Props>) : Component<HelpWindow.Props>(ctx) {

    /** A name's help: its [symbol], and a local's [declarationCode] (null for a library name). */
    data class Entry(val symbol: KlangSymbol, val declarationCode: String?)

    data class Props(
        val entry: Stream<Entry?>,
    )

    private val entry by subscribingTo(props.entry)

    override fun VDom.render() {
        val shown = entry

        if (shown == null) {
            div {
                css {
                    put("font-size", "13px")
                    put("color", "var(--kl-ink-label)")
                }

                +"Put the cursor on a name in the code and press Alt+Q: its help shows here."
            }
        } else {
            KlangSymbolDocsComp(
                symbol = shown.symbol,
                declarationCode = shown.declarationCode,
                onNavigate = { doc, event -> navToSymbolDocs(doc = doc, event = event) },
            )
        }
    }
}
