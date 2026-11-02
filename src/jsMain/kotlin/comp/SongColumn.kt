/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.Song
import io.peekandpoke.klang.spine.KlangCodePlaybackCtrl
import io.peekandpoke.klang.ui.comp.KlangControlStyles
import io.peekandpoke.klang.ui.comp.KlangTextField
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.semanticui.icon
import kotlinx.browser.window
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.span
import kotlinx.html.title

@Suppress("FunctionName")
fun Tag.SongColumn(
    ctrl: KlangCodePlaybackCtrl,
    builtIn: Song?,
    onReset: () -> Unit,
) = comp(
    SongColumn.Props(ctrl = ctrl, builtIn = builtIn, onReset = onReset)
) {
    SongColumn(it)
}

/**
 * The open song in the workbench's column (KLANG-00349, the canvas's board A): its title to edit, copy the code, revert
 * a built-in song that was changed to its original, and what the song is made of: its file, its imports, its
 * versions. The sounds in use come with the code analysis that can name them.
 */
class SongColumn(ctx: Ctx<Props>) : Component<SongColumn.Props>(ctx) {

    companion object {
        /** An import at a line's start, also over several lines, to `from "name"` or `from 'name'`: the name. */
        private val IMPORT = Regex("""^\s*import\b[^;"']*?\bfrom\s+["']([^"'\n]+)["']""", RegexOption.MULTILINE)

        /** The libraries [code] imports, in order, each once. */
        fun importsOf(code: String): List<String> = IMPORT.findAll(code).map { it.groupValues[1] }.distinct().toList()

        /** A key's padding in a heading's line: close together (the maintainer, 2026-11-02: "Reduce the spacing"). */
        private const val HEADING_KEY_PADDING = "6px 4px"
    }

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(
        val ctrl: KlangCodePlaybackCtrl,
        /** The built-in song this one started from; null for a new song. */
        val builtIn: Song?,
        /** Back to the built-in song's code, title and rpm (the page also tells the editor). */
        val onReset: () -> Unit,
    )

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private val state by subscribingTo(props.ctrl.state)

    /** "Copied" on the copy key for a moment after a copy. */
    private var justCopied by value(false)

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    /** Copies the code; "Copied" only once it is. Outside a secure context (a dev URL over http) there is no clipboard. */
    private fun copyCode() {
        val clipboard = window.navigator.asDynamic().clipboard ?: return

        clipboard.writeText(props.ctrl.state().code).then(
            {
                justCopied = true
                window.setTimeout({ justCopied = false }, 2000)
            },
            { e: dynamic -> console.warn("SongColumn: could not copy the code", e) },
        )
    }

    override fun VDom.render() {
        val builtIn = props.builtIn
        val changed = builtIn != null && builtIn.code != state.code

        div {
            css {
                put("display", "flex")
                put("flex-direction", "column")
                put("gap", "10px")
                put("padding", "16px 14px")
                put("color", "var(--kl-ink-text)")
            }

            // Its keys up in the heading's line, at the right, quiet as a code example's copy key (the maintainer,
            // 2026-11-02: "move these buttons up, right aligned and in the 'copy code' style"); Copy last, where it
            // stays whether Revert shows or not
            section("This song") {
                if (changed) {
                    button(type = ButtonType.button, classes = "kl-quiet-key") {
                        title = "Back to the original"
                        onClick { props.onReset() }
                        css { put("padding", HEADING_KEY_PADDING) }

                        icon.undo()
                        +"Revert"
                    }
                }

                button(type = ButtonType.button, classes = "kl-quiet-key") {
                    title = "Copy the code"
                    onClick { copyCode() }
                    css { put("padding", HEADING_KEY_PADDING) }

                    icon.copy_outline()
                    +if (justCopied) "Copied" else "Copy"
                }
            }

            KlangTextField(
                label = "Song title",
                value = state.title ?: "",
                onInput = { props.ctrl.setTitle(it) },
                placeholder = "Song title",
            )

            section("Files")
            line("song.klang")

            section("Imports")

            for (name in importsOf(state.code)) {
                line(name, note = "built in")
            }

            section("Versions")
            line("working copy", note = "in this browser")
            line("versions", note = "with the backend, later")
        }
    }

    /** A section's heading; [keys], when given, at the right end of its line. */
    private fun FlowContent.section(title: String, keys: (FlowContent.() -> Unit)? = null) {
        div(classes = KlangControlStyles.lookHeading.toString()) {
            css { put("margin-top", "8px") }

            div(classes = KlangControlStyles.heading.toString()) { +title }

            if (keys != null) {
                span {
                    css {
                        put("display", "flex")
                        put("align-items", "center")
                        // The keys' own padding is their room: the line keeps the heading's height, the last key's word
                        // ends at the column's edge
                        put("margin", "-6px -4px -6px 0")
                    }

                    keys()
                }
            }
        }
    }

    /** One line of the song's make-up, its [note] beside it, quieter. */
    private fun FlowContent.line(text: String, note: String? = null) {
        div {
            css {
                put("display", "flex")
                put("justify-content", "space-between")
                put("gap", "8px")
                put("font-size", "13px")
            }

            div { +text }

            note?.let {
                div {
                    css {
                        put("font-size", "11px")
                        put("color", "var(--kl-ink-label)")
                    }

                    +it
                }
            }
        }
    }
}
