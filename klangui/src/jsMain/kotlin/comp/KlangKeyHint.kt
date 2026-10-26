/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.klang.workshop.keys.KeyCapGlyphs.keyCapGlyph
import io.peekandpoke.klang.workshop.keys.KeyChord
import io.peekandpoke.klang.workshop.keys.KeyHintRenderer
import io.peekandpoke.klang.workshop.keys.KeyOverviewRenderer
import io.peekandpoke.klang.workshop.keys.KeyOverviewState
import io.peekandpoke.klang.workshop.keys.KeyWaitingRenderer
import kotlinx.html.FlowContent
import kotlinx.html.div
import kotlinx.html.kbd
import kotlinx.html.span
import kotlinx.html.style

/**
 * Klang's look of keys (KLANG-00325), set for the whole app: each key in a small key cap (`Ctrl` `⇧` `↵`, the compact
 * signs of [KeyChord.keyParts], drawn as sharp vector icons), on an opaque frame in
 * the menu's dark with the accent's edge. [render] is a button's hint, which the key hint stage places; [overview] is a
 * panel listing a bag's keys, which its place on the page positions. While an action cannot act, its keys are muted.
 */
object KlangKeyHint {

    /** The CSS class of a hint's element. */
    const val CLASS = "klang-key-hint"

    /** The CSS class of an overview panel's element. */
    const val OVERVIEW_CLASS = "klang-key-overview"

    /** The CSS class of the chip of chords in a row waiting for their next key. */
    const val WAITING_CLASS = "klang-key-waiting"

    /** The CSS class of one key cap. */
    const val KEY_CLASS = "klang-key-cap"

    /** The frame both a hint and an overview stand on. */
    private val frameStyle: List<String>
        get() = listOf(
            "border-radius: 5px",
            "border: 1px solid ${KlangTheme.Hex.accentMuted}",
            "background: ${KlangTheme.Hex.menuBackground}",
        )

    /** One way to press a key: its parts side by side, each in a key cap. */
    fun FlowContent.caps(parts: List<String>, enabled: Boolean = true) {
        val text = if (enabled) KlangTheme.Hex.textPrimary else KlangTheme.Hex.textTertiary

        span {
            style = "display: inline-flex; align-items: center; gap: 3px; white-space: nowrap"

            parts.forEach { part ->
                kbd(classes = KEY_CLASS) {
                    style = listOf(
                        "display: inline-flex",
                        "align-items: center",
                        "justify-content: center",
                        "box-sizing: border-box",
                        "height: 20px",
                        "min-width: 20px",
                        "padding: 0 4px",
                        "border: 1px solid ${KlangTheme.Hex.textTertiary}",
                        // A key cap: the lower edge a little heavier
                        "border-bottom-width: 2px",
                        "border-radius: 3px",
                        "background: ${KlangTheme.Hex.cardBackground}",
                        "color: $text",
                        "font-family: inherit",
                        "font-size: 12px",
                        "font-weight: normal",
                        "line-height: 1.5",
                        "text-align: center",
                    ).joinToString("; ")

                    keyCapGlyph(part = part, sizePx = 12)
                }
            }
        }
    }

    /** One way to press a key, step by step: each step's caps, a small gap between the steps (`Shift` `Shift`). */
    fun FlowContent.capsGroups(groups: List<List<String>>, enabled: Boolean = true) {
        span {
            style = "display: inline-flex; align-items: center; gap: 6px; white-space: nowrap"

            groups.forEach { group -> caps(parts = group, enabled = enabled) }
        }
    }

    val render: KeyHintRenderer = { state ->
        span(classes = CLASS) {
            style = (listOf("display: inline-flex", "padding: 3px 5px") + frameStyle).joinToString("; ")

            capsGroups(groups = state.keyGroups(compact = true), enabled = state.enabled)
        }
    }

    /**
     * The chip of chords in a row waiting for their next key (Ctrl+K, then …): the chords pressed so far and an
     * ellipsis, on Klang's frame. The key hint stage puts it at the foot of the window.
     */
    val waiting: KeyWaitingRenderer = { state ->
        span(classes = WAITING_CLASS) {
            style = (
                listOf(
                    "display: inline-flex",
                    "align-items: center",
                    "gap: 6px",
                    "padding: 4px 8px",
                    "color: ${KlangTheme.Hex.textSecondary}",
                    "font-size: 12px",
                    "box-shadow: 0 4px 16px rgba(0, 0, 0, 0.5)",
                ) + frameStyle
                ).joinToString("; ")

            capsGroups(groups = state.keyGroups())
            +"…"
        }
    }

    /** A hint that names its action after the keys ("Esc Close"): for a key that belongs to a whole box, not a button. */
    val renderWithTitle: KeyHintRenderer = { state ->
        span(classes = CLASS) {
            style = (
                listOf(
                    "display: inline-flex",
                    "align-items: center",
                    "gap: 6px",
                    "padding: 3px 7px 3px 5px",
                    "color: ${KlangTheme.Hex.textSecondary}",
                    "font-size: 12px",
                    "font-weight: normal",
                ) + frameStyle
                ).joinToString("; ")

            capsGroups(groups = state.keyGroups(compact = true), enabled = state.enabled)

            +state.action.title
        }
    }

    /**
     * A panel of keys in Klang's look: the frame, an optional bold [title] over the whole width, and columns that
     * [content] fills cell by cell: [columns] of their content's width, or [template] (a CSS grid template, for a column
     * that may shrink). The keys overview and the editor's bookmarks panel use it.
     */
    fun FlowContent.panel(title: String?, columns: Int, template: String? = null, content: FlowContent.() -> Unit) {
        div(classes = OVERVIEW_CLASS) {
            style = (
                listOf(
                    "display: grid",
                    "grid-template-columns: ${template ?: "repeat($columns, auto)"}",
                    "min-width: 0",
                    "column-gap: 16px",
                    "row-gap: 5px",
                    "align-items: center",
                    "padding: 8px 10px",
                    "color: ${KlangTheme.Hex.textSecondary}",
                    "font-size: 12px",
                    "box-shadow: 0 4px 16px rgba(0, 0, 0, 0.5)",
                ) + frameStyle
                ).joinToString("; ")

            title?.let {
                div {
                    style = "grid-column: 1 / -1; color: ${KlangTheme.Hex.textPrimary}; font-weight: bold; margin-bottom: 2px"
                    +it
                }
            }

            content()
        }
    }

    /**
     * The keys overview in Klang's look: one panel, its sections side by side (each a heading over its rows, the title
     * left and the keys right), so a long overview stays short and reads by function. Without sections, one column.
     */
    val overview: KeyOverviewRenderer = { state ->
        panel(title = state.title, columns = 1) {
            div {
                style = "display: flex; flex-wrap: wrap; align-items: flex-start; gap: 6px 20px"

                state.sections.forEach { section ->
                    div {
                        style = "display: grid; grid-template-columns: auto auto; column-gap: 16px; row-gap: 5px; align-items: center"

                        section.title?.let { heading ->
                            div {
                                style = "grid-column: 1 / -1; color: ${KlangTheme.Hex.accent}; font-size: 11px; " +
                                    "text-transform: uppercase; letter-spacing: 0.05em"
                                +heading
                            }
                        }

                        section.entries.forEach { entry -> entryRow(entry) }
                    }
                }
            }
        }
    }

    /** One row of an overview: its title, and its keys right-aligned, the ways to press it apart by a slash. */
    private fun FlowContent.entryRow(entry: KeyOverviewState.Entry) {
        span { +entry.title }

        span {
            style = "display: inline-flex; align-items: center; gap: 6px; justify-self: end"

            entry.keys.forEachIndexed { index, groups ->
                if (index > 0) {
                    span {
                        style = "color: ${KlangTheme.Hex.textTertiary}"
                        +"/"
                    }
                }

                capsGroups(groups = groups)
            }
        }
    }
}
