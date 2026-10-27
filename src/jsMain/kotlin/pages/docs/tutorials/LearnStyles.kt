/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages.docs.tutorials

import io.peekandpoke.klang.ui.comp.KlangControlStyles
import io.peekandpoke.kraft.addons.styling.RawStyleSheet
import io.peekandpoke.kraft.addons.styling.StyleSheet

/**
 * The Learn area's own styles, as Kraft's mountable style sheet (the maintainer, 2026-10-27: "please consider using the
 * mountable style sheets that Kraft provides"; the app's plain `klang.css` classes move here in a sweep, KLANG-00373).
 */
object LearnStyles : StyleSheet("klang-learn") {

    /**
     * A track on the overview as a panel, the whole of it a link to the track's page (KLANG-00370): sunk a little into
     * the page, its edge brighter under the mouse.
     */
    val trackPanel by rule {
        put("display", "flex")
        put("flex-direction", "column")
        put("gap", "10px")
        put("padding", "16px 18px")
        put("border-radius", "var(--kl-radius)")
        put("background", "var(--kl-ground-panel)")
        put("border", "1px solid var(--kl-edge)")
        put("box-shadow", "inset 0 1px 0 rgba(255, 255, 255, 0.04)")

        rule("&:hover") {
            put("border-color", "var(--kl-ink-label)")
        }

        rule("&:focus-visible") {
            put("outline", "2px solid var(--kl-focus)")
            put("outline-offset", "2px")
        }
    }

    /**
     * Under "reduce motion" the Gui-dance's beacon round a panel stands still, a lit ring. Raw, as klangui's motion
     * rules: a Kraft rule has no media query. One class more than the beacon's own rule, so it wins.
     */
    @Suppress("unused")
    private val motion = RawStyleSheet(
        css = """
            @media (prefers-reduced-motion: reduce) {
                .$trackPanel.${KlangControlStyles.beacon}::after { animation: none; box-shadow: 0 0 0 2px var(--kl-lamp-try); }
            }
        """.trimIndent()
    )
}
