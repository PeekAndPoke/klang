/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.ultra.html.css
import kotlinx.html.FlowContent
import kotlinx.html.div

/**
 * What a bottom panel's tab shows before it has anything: a [headline] and a quieter [line] under it, in the middle of
 * the panel, the first thing a new user reads there (the maintainer, 2026-10-27: "more prominent and centered in the
 * tool", on the Quick help; 2026-10-28: the Lexikon and the Sprudel tab the same, "also be vertically centered").
 * It lies over the whole tab, centred in what the tab shows, whatever else is in it (the maintainer, 2026-10-28: "with
 * regards to the total visible area of the tab, no matter if there is the "Alt+Q" hint on the top right or the
 * lexicon index on the left"): the tab's wrapper is its positioned ground, and clicks go through to what is under it.
 */
fun FlowContent.panelEmptyHint(headline: String, line: FlowContent.() -> Unit) {
    div {
        css {
            put("display", "flex")
            put("flex-direction", "column")
            put("align-items", "center")
            put("justify-content", "center")
            put("gap", "10px")
            put("position", "absolute")
            put("inset", "0")
            put("pointer-events", "none")
            put("text-align", "center")
        }

        div {
            css {
                put("font-size", "18px")
                put("color", "var(--kl-ink-strong)")
            }

            +headline
        }

        div {
            css {
                put("font-size", "14px")
                put("color", "var(--kl-ink-label)")
            }

            line()
        }
    }
}
