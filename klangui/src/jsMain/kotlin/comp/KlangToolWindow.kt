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
import kotlinx.html.FlowContent
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
    edge: KlangToolWindow.Edge = KlangToolWindow.Edge.Right,
    header: (FlowContent.() -> Unit)? = null,
    content: DIV.() -> Unit,
) = comp(
    KlangToolWindow.Props(
        title = title,
        pinned = pinned,
        chip = chip,
        onPinnedChange = onPinnedChange,
        onClose = onClose,
        closeOnEsc = closeOnEsc,
        edge = edge,
        header = header,
        content = content,
    )
) {
    KlangToolWindow(it)
}

/**
 * A tool window (KLANG-00339, KLANG-00334 item 9): a header with its title, a chip (what it follows, "follows the
 * cursor"), a pin and a close button, over its content; one that closes on Esc shows the key's pill left of the pin.
 *
 * Unpinned it flies in over the editor from its [Props.edge]: a shadow on the editor's side, rounded where it meets
 * the editor, a short slide in (still under "reduce motion"). Pinned it docks beside or under the editor, flat. Its
 * size and place are the shell's. [Props.header] is what its header holds besides its title (the song page's Help:
 * "Show on hover").
 */
class KlangToolWindow(ctx: Ctx<Props>) : Component<KlangToolWindow.Props>(ctx) {

    /** Where it flies in from: the strip's side, or under the editor (the bottom panel, KLANG-00349). */
    enum class Edge(val pinLabel: String) {
        Right(pinLabel = "Pin beside the editor"),
        Bottom(pinLabel = "Pin under the editor"),
    }

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
        val edge: Edge,
        val header: (FlowContent.() -> Unit)?,
        val content: DIV.() -> Unit,
    )

    override fun VDom.render() {
        val styles = KlangControlStyles
        val floating = when {
            props.pinned -> null
            props.edge == Edge.Bottom -> styles.toolWindowFloatingBottom
            else -> styles.toolWindowFloating
        }
        val classes = listOfNotNull(styles.toolWindow, floating)

        section(classes = classes.joinToString(" ")) {
            attributes["aria-label"] = props.title

            div(classes = styles.toolWindowHeader.toString()) {
                span(classes = styles.toolWindowTitle.toString()) { +props.title }

                props.chip?.let { chip -> span(classes = styles.toolWindowChip.toString()) { +chip } }

                div { attributes["style"] = "flex: 1" }

                props.header?.let { header ->
                    div(classes = styles.toolWindowHeaderEnd.toString()) {
                        header()
                    }
                }

                props.onClose?.takeIf { props.closeOnEsc }?.let { close -> KlangCloseKey(onClose = close) }

                props.onPinnedChange?.let { change ->
                    KlangIconToggle(
                        icon = { thumbtack },
                        label = props.edge.pinLabel,
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
