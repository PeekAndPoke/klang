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

@Suppress("FunctionName")
fun Tag.KlangToolWindow(
    title: String,
    pinned: Boolean,
    chip: String? = null,
    onPinnedChange: ((Boolean) -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    closeOnEsc: Boolean = false,
    content: DIV.() -> Unit,
) = comp(
    KlangToolWindow.Props(
        title = title,
        pinned = pinned,
        chip = chip,
        onPinnedChange = onPinnedChange,
        onClose = onClose,
        closeOnEsc = closeOnEsc,
        content = content,
    )
) {
    KlangToolWindow(it)
}

/**
 * A tool window (KLANG-00339, KLANG-00334 item 9): a header with its title, a chip (what it follows, "follows the
 * cursor"), a pin and a close button, over its content; one that closes on Esc shows the key's pill left of the pin.
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
        /** Esc closes it, through the key manager, with its pill "Close: Esc" in the header ([KlangCloseKey]). */
        val closeOnEsc: Boolean,
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

                props.onClose?.takeIf { props.closeOnEsc }?.let { close -> KlangCloseKey(onClose = close) }

                props.onPinnedChange?.let { change ->
                    KlangIconToggle(
                        icon = { thumbtack },
                        label = "Pin beside the editor",
                        on = props.pinned,
                        onClick = { change(!props.pinned) },
                    )
                }

                props.onClose?.let { close ->
                    button(type = ButtonType.button, classes = styles.iconToggle.toString()) {
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
