/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.semanticui.icon
import kotlinx.html.ButtonType
import kotlinx.html.DIV
import kotlinx.html.Tag
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.section
import kotlinx.html.span
import kotlinx.html.title

@Suppress("FunctionName")
fun Tag.KlangToolWindow(
    title: String,
    pinned: Boolean,
    chip: String? = null,
    onPinnedChange: ((Boolean) -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    content: DIV.() -> Unit,
) = comp(
    KlangToolWindow.Props(
        title = title,
        pinned = pinned,
        chip = chip,
        onPinnedChange = onPinnedChange,
        onClose = onClose,
        content = content,
    )
) {
    KlangToolWindow(it)
}

/**
 * A tool window (KLANG-00339, KLANG-00334 item 9): a header with its title, a chip (what it follows, "follows the
 * cursor"), a pin and a close button, over its content.
 *
 * Unpinned it flies in over the editor: a shadow to its left, rounded where it meets the editor, a short slide in
 * (still under "reduce motion"). Pinned it docks beside the editor, flat. Its size and place are the shell's.
 */
class KlangToolWindow(ctx: Ctx<Props>) : Component<KlangToolWindow.Props>(ctx) {

    data class Props(
        val title: String,
        val pinned: Boolean,
        val chip: String?,
        /** Shows the pin when given; it gets the new state. */
        val onPinnedChange: ((Boolean) -> Unit)?,
        /** Shows the close button when given. */
        val onClose: (() -> Unit)?,
        val content: DIV.() -> Unit,
    )

    override fun VDom.render() {
        val styles = KlangControlStyles
        val classes = listOfNotNull(styles.toolWindow, if (props.pinned) null else styles.toolWindowFloating)

        section(classes = classes.joinToString(" ")) {
            attributes["aria-label"] = props.title

            div(classes = styles.toolWindowHeader.toString()) {
                span(classes = styles.toolWindowTitle.toString()) { +props.title }

                props.chip?.let { chip -> span(classes = styles.toolWindowChip.toString()) { +chip } }

                div { attributes["style"] = "flex: 1" }

                props.onPinnedChange?.let { change ->
                    val pinClasses = listOfNotNull(styles.toolWindowAction, if (props.pinned) styles.toolWindowActionOn else null)

                    button(type = ButtonType.button, classes = pinClasses.joinToString(" ")) {
                        attributes["aria-label"] = "Pin beside the editor"
                        attributes["aria-pressed"] = props.pinned.toString()
                        title = if (props.pinned) "Pinned beside the editor" else "Flies over the editor; pin it beside"
                        onClick { change(!props.pinned) }
                        icon.thumbtack.render { attributes["aria-hidden"] = "true" }
                    }
                }

                props.onClose?.let { close ->
                    button(type = ButtonType.button, classes = styles.toolWindowAction.toString()) {
                        attributes["aria-label"] = "Close ${props.title}"
                        onClick { close() }
                        icon.times.render { attributes["aria-hidden"] = "true" }
                    }
                }
            }

            div(classes = styles.toolWindowBody.toString()) {
                props.content(this)
            }
        }
    }
}
