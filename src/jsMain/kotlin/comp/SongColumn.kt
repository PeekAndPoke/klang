/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.Song
import io.peekandpoke.klang.ui.comp.KlangButton
import io.peekandpoke.klang.ui.comp.KlangControlStyles
import io.peekandpoke.klang.ui.comp.KlangTextField
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import kotlinx.browser.window
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div

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
 * The open song in the workbench's column (KLANG-00349, the canvas's board A): its title to edit, copy the code, back
 * to the original for a built-in song that was changed, and what the song is made of: its file, its imports, its
 * versions. The sounds in use come with the code analysis that can name them.
 */
class SongColumn(ctx: Ctx<Props>) : Component<SongColumn.Props>(ctx) {

    companion object {
        /** An import at a line's start, also over several lines, to `from "name"` or `from 'name'`: the name. */
        private val IMPORT = Regex("""^\s*import\b[^;"']*?\bfrom\s+["']([^"'\n]+)["']""", RegexOption.MULTILINE)

        /** The libraries [code] imports, in order, each once. */
        fun importsOf(code: String): List<String> = IMPORT.findAll(code).map { it.groupValues[1] }.distinct().toList()
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

    private fun copyCode() {
        window.asDynamic().navigator.clipboard.writeText(props.ctrl.state().code)
        justCopied = true
        window.setTimeout({ justCopied = false }, 2000)
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

            section("This song")

            KlangTextField(
                label = "Song title",
                value = state.title ?: "",
                onInput = { props.ctrl.setTitle(it) },
                placeholder = "Song title",
            )

            div {
                css {
                    put("display", "flex")
                    put("flex-wrap", "wrap")
                    put("gap", "8px")
                }

                KlangButton(onClick = { copyCode() }) {
                    +if (justCopied) "Copied" else "Copy the code"
                }

                if (changed) {
                    KlangButton(onClick = { props.onReset() }) { +"Back to the original" }
                }
            }

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

    private fun FlowContent.section(title: String) {
        div(classes = KlangControlStyles.heading.toString()) {
            css { put("margin-top", "8px") }

            +title
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
