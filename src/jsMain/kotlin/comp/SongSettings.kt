/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.storage.EditorSettings
import io.peekandpoke.klang.ui.comp.KlangButton
import io.peekandpoke.klang.ui.comp.KlangControlStyles
import io.peekandpoke.klang.ui.comp.KlangLamp
import io.peekandpoke.klang.ui.comp.KlangValueKnob
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import kotlinx.html.Tag
import kotlinx.html.div

@Suppress("FunctionName")
fun Tag.SongSettings() = comp {
    SongSettings(it)
}

/**
 * The song page's settings, in its Settings tool window (KLANG-00349): the code highlights on or off, first (the
 * maintainer, 2026-11-05: "As the first setting in the side-tool", KLANG-00411), the highlights a sound event draws
 * in the code (once "EVT" in the header) and how strong they show (KLANG-00411), and the quick help on hover, on by
 * default (Alt+Q shows a name's help in the Quick help panel, hover or not). Kept in the browser ([EditorSettings]);
 * the Quick help's header has the same switch.
 */
class SongSettings(ctx: NoProps) : PureComponent(ctx) {

    private val highlights by subscribingTo(EditorSettings.highlightsPerEvent)
    private val highlightsOn by subscribingTo(EditorSettings.highlightsOn)
    private val strength by subscribingTo(EditorSettings.highlightStrength)
    private val hoverHelp by subscribingTo(EditorSettings.hoverHelp)

    override fun VDom.render() {
        val range = EditorSettings.HIGHLIGHTS_PER_EVENT_RANGE
        val strengthRange = EditorSettings.HIGHLIGHT_STRENGTH_RANGE

        div {
            css {
                put("display", "flex")
                put("flex-direction", "column")
                put("gap", "12px")
            }

            div(classes = KlangControlStyles.heading.toString()) { +"Code highlights" }

            KlangButton(
                onClick = { EditorSettings.setHighlightsOn(!highlightsOn) },
                pressed = highlightsOn,
                lamp = if (highlightsOn) KlangLamp.State.On else KlangLamp.State.Off,
            ) {
                +"While a song plays"
            }

            div(classes = KlangControlStyles.heading.toString()) {
                css { put("margin-top", "8px") }

                +"Highlights per sound"
            }

            KlangValueKnob(
                name = "Highlights per sound event",
                value = highlights.toDouble(),
                range = range.first.toDouble()..range.last.toDouble(),
                step = 1.0,
                onChange = { EditorSettings.setHighlightsPerEvent(it.toInt()) },
                unit = "EVT",
                decimals = 0,
                perPixel = 0.25,
            )

            div(classes = KlangControlStyles.heading.toString()) {
                css { put("margin-top", "8px") }

                +"Highlight strength"
            }

            KlangValueKnob(
                name = "Highlight strength",
                value = strength.toDouble(),
                range = strengthRange.first.toDouble()..strengthRange.last.toDouble(),
                step = 1.0,
                onChange = { EditorSettings.setHighlightStrength(it.toInt()) },
                unit = "%",
                decimals = 0,
                perPixel = 0.5,
            )

            div(classes = KlangControlStyles.heading.toString()) {
                css { put("margin-top", "8px") }

                +"Quick help"
            }

            KlangButton(
                onClick = { EditorSettings.setHoverHelp(!hoverHelp) },
                pressed = hoverHelp,
                lamp = if (hoverHelp) KlangLamp.State.On else KlangLamp.State.Off,
            ) {
                +"On hover"
            }

            div {
                css {
                    put("font-size", "12px")
                    put("color", "var(--kl-ink-label)")
                }

                +"Alt+Q on a name shows its help, hover or not."
            }
        }
    }
}
