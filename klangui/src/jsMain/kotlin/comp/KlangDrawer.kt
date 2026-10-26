/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.semanticui.icon
import kotlinx.html.DIV
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.span

@Suppress("FunctionName")
fun Tag.KlangDrawer(
    title: String,
    entries: List<KlangDrawer.Entry>,
    current: String?,
    onPick: (String) -> Unit,
    onClose: (() -> Unit)? = null,
    closeOnEsc: Boolean = false,
    search: (DIV.() -> Unit)? = null,
    footer: (DIV.() -> Unit)? = null,
) = comp(
    KlangDrawer.Props(
        title = title,
        entries = entries,
        current = current,
        onPick = onPick,
        onClose = onClose,
        closeOnEsc = closeOnEsc,
        search = search,
        footer = footer,
    )
) {
    KlangDrawer(it)
}

/**
 * The menu drawer (KLANG-00339): what the Motor button opens (KLANG-00334, layout A). A riveted panel with a printed
 * title and a close button (with the pill "Close: Esc" when Esc closes it), a search field when one is given, the
 * destinations as tiles in two columns, and a footer (the look picker).
 *
 * The current destination is drawn pressed with its lamp on and announced as `aria-current`; the others' lamps are
 * off; one that is not there yet ([Entry.later]) is a dashed tile without a lamp that cannot be picked. The drawer
 * fills the width it is given: its size and where it opens (above the Motor) are the shell's.
 */
class KlangDrawer(ctx: Ctx<Props>) : Component<KlangDrawer.Props>(ctx) {

    /** One destination: [id] is what [Props.onPick] gets, [sub] a short line under the [label]. */
    data class Entry(
        val id: String,
        val label: String,
        val sub: String? = null,
        val later: Boolean = false,
    )

    data class Props(
        val title: String,
        val entries: List<Entry>,
        /** The id of the destination the user is at; null for none. */
        val current: String?,
        val onPick: (String) -> Unit,
        /** Shows a close button when given. */
        val onClose: (() -> Unit)?,
        /** Esc closes it, through the key manager, with its pill "Close: Esc" beside the close button ([KlangCloseKey]). */
        val closeOnEsc: Boolean,
        val search: (DIV.() -> Unit)?,
        val footer: (DIV.() -> Unit)?,
    )

    override fun VDom.render() {
        val styles = KlangControlStyles

        KlangPanel {
            attributes["role"] = "navigation"
            attributes["aria-label"] = props.title

            div(classes = styles.drawerHeader.toString()) {
                span(classes = styles.heading.toString()) { +props.title }

                props.onClose?.let { close ->
                    span(classes = styles.drawerHeaderEnd.toString()) {
                        if (props.closeOnEsc) {
                            KlangCloseKey(onClose = close)
                        }

                        KlangButton(onClick = close, shape = KlangButton.Shape.Round, ariaLabel = "Close") {
                            icon.times.render { attributes["aria-hidden"] = "true" }
                        }
                    }
                }
            }

            props.search?.invoke(this)

            div(classes = styles.drawerGrid.toString()) {
                for (entry in props.entries) {
                    val isCurrent = !entry.later && entry.id == props.current

                    KlangButton(
                        onClick = { props.onPick(entry.id) },
                        shape = KlangButton.Shape.Tile,
                        current = isCurrent,
                        availability = if (entry.later) KlangButton.Availability.Later else KlangButton.Availability.Enabled,
                        lamp = when {
                            entry.later -> null
                            isCurrent -> KlangLamp.State.On
                            else -> KlangLamp.State.Off
                        },
                    ) {
                        span { +entry.label }
                        entry.sub?.let { sub -> span(classes = styles.drawerSub.toString()) { +sub } }
                    }
                }
            }

            props.footer?.let { footer ->
                div(classes = styles.drawerFooter.toString()) { footer() }
            }
        }
    }
}
