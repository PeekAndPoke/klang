/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.visuals

import io.peekandpoke.klang.KlangGuiDance
import io.peekandpoke.klang.ui.comp.KlangButton
import io.peekandpoke.klang.ui.comp.KlangWorkbench
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import kotlinx.html.div

/**
 * The bottom panel's Visuals tab, on every page (KLANG-00409): a card per visual, each opening it over the whole
 * window (the maintainer, 2026-10-29). Before, it was the help panel's and showed only on the help pages.
 */
object VisualsTab {

    /** The tab's id, kept with the workbench's layout. */
    const val TAB_ID = "visuals"

    /** The tab; its key is the Visuals walkthrough's second step. */
    val tab = KlangWorkbench.Tab(id = TAB_ID, title = "Visuals", beaconStep = KlangGuiDance.VISUALS_TAB) {
        div {
            key = "visuals-tab"
            css {
                put("display", "grid")
                put("grid-template-columns", "repeat(auto-fill, minmax(220px, 1fr))")
                put("gap", "10px")
            }

            // Klang Amp tiles is the Visuals walkthrough's third step: its card wears the beacon while lit
            val guidedLit = KlangGuiDance.dance.lit() == KlangGuiDance.VISUAL_KLANGAMP_TILES

            for (visual in VisualsScreen.Visual.entries) {
                val guided = visual == VisualsScreen.Visual.KlangampTiles

                KlangButton(
                    onClick = {
                        if (guided) {
                            KlangGuiDance.dance.used(KlangGuiDance.VISUAL_KLANGAMP_TILES)
                        }

                        VisualsScreen.shown(visual)
                    },
                    surface = KlangButton.Surface.Domed,
                    shape = KlangButton.Shape.Tile,
                    beacon = guided && guidedLit,
                ) {
                    // A small picture of the visual (the maintainer, 2026-10-29: "Add a preview to the cards")
                    VisualPreview(visual)

                    div {
                        css {
                            put("font-size", "14px")
                            put("font-weight", "700")
                        }
                        +visual.title
                    }
                    div {
                        css {
                            put("font-size", "12px")
                            put("opacity", "0.8")
                        }
                        +visual.description
                    }
                }
            }
        }
    }
}
