/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.semanticui.SemanticIconFn
import io.peekandpoke.ultra.semanticui.icon
import kotlinx.html.DIV
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.section
import kotlinx.html.span

@Suppress("FunctionName")
fun Tag.KlangDrawer(
    title: String,
    entries: List<KlangDrawer.Entry>,
    current: String?,
    onPick: (String) -> Unit,
    beacon: String? = null,
    onClose: (() -> Unit)? = null,
    closeOnEsc: Boolean = false,
    search: (DIV.() -> Unit)? = null,
    pickUp: (DIV.() -> Unit)? = null,
    loadouts: (DIV.() -> Unit)? = null,
) = comp(
    KlangDrawer.Props(
        title = title,
        entries = entries,
        current = current,
        onPick = onPick,
        beacon = beacon,
        onClose = onClose,
        closeOnEsc = closeOnEsc,
        search = search,
        pickUp = pickUp,
        loadouts = loadouts,
    )
) {
    KlangDrawer(it)
}

/**
 * The Motor's menu (KLANG-00339, made large in KLANG-00403): what the Motor button opens over everything right of the
 * column. A riveted panel; its header holds the printed title, the search field when one is given, and the close button
 * (with the pill "Close: Esc" when Esc closes it). Then the destinations as large tiles in four columns, each with its
 * icon, its label and its second line ("The button like 'Songs' can then also be bigger", the maintainer,
 * 2026-11-01). Below them two halves: what one comes back to ([Props.pickUp]) and the loadouts ([Props.loadouts], the
 * look picker); one given alone takes the whole width.
 *
 * The current destination is drawn pressed with its lamp on and announced as `aria-current`; the others' lamps are
 * off; one that is not there yet ([Entry.later]) is a dashed tile without a lamp that cannot be picked. The drawer
 * fills the size it is given: its size and where it opens are the shell's.
 */
class KlangDrawer(ctx: Ctx<Props>) : Component<KlangDrawer.Props>(ctx) {

    /** One destination: [id] is what [Props.onPick] gets, [sub] a short line under the [label], [icon] beside them. */
    data class Entry(
        val id: String,
        val label: String,
        val sub: String? = null,
        val later: Boolean = false,
        val icon: SemanticIconFn? = null,
    )

    data class Props(
        val title: String,
        val entries: List<Entry>,
        /** The id of the destination the user is at; null for none. */
        val current: String?,
        val onPick: (String) -> Unit,
        /** The id of the destination the first-visit trail lights, its "try me" (KLANG-00350); null for none. */
        val beacon: String?,
        /** Shows a close button when given. */
        val onClose: (() -> Unit)?,
        /** Esc closes it, through the key manager, with its pill "Close: Esc" beside the close button ([KlangCloseKey]). */
        val closeOnEsc: Boolean,
        val search: (DIV.() -> Unit)?,
        /** The left half under the destinations: what one comes back to (the last songs); null for none. */
        val pickUp: (DIV.() -> Unit)?,
        /** The right half under the destinations: the loadouts (the looks); null for none. */
        val loadouts: (DIV.() -> Unit)?,
    )

    override fun VDom.render() {
        val styles = KlangControlStyles

        KlangPanel {
            attributes["role"] = "navigation"
            attributes["aria-label"] = props.title

            div(classes = styles.drawerHeader.toString()) {
                span(classes = styles.heading.toString()) { +props.title }

                props.search?.let { search ->
                    div(classes = styles.drawerSearch.toString()) { search() }
                }

                props.onClose?.let { close ->
                    span(classes = styles.drawerHeaderEnd.toString()) {
                        if (props.closeOnEsc) {
                            KlangCloseKey(onClose = close)
                        }

                        KlangCloseButton(label = "Close", onClick = close)
                    }
                }
            }

            div(classes = styles.drawerGrid.toString()) {
                for (entry in props.entries) {
                    val isCurrent = !entry.later && entry.id == props.current

                    KlangButton(
                        onClick = { props.onPick(entry.id) },
                        shape = KlangButton.Shape.Tile,
                        current = isCurrent,
                        availability = if (entry.later) KlangButton.Availability.Later else KlangButton.Availability.Enabled,
                        beacon = entry.id == props.beacon,
                        lamp = when {
                            entry.later -> null
                            isCurrent -> KlangLamp.State.On
                            else -> KlangLamp.State.Off
                        },
                    ) {
                        div(classes = styles.drawerTile.toString()) {
                            entry.icon?.let { iconFn ->
                                icon.iconFn().render { attributes["aria-hidden"] = "true" }
                            }

                            div(classes = styles.drawerTileText.toString()) {
                                span(classes = styles.drawerLabel.toString()) { +entry.label }
                                entry.sub?.let { sub -> span(classes = styles.drawerTileSub.toString()) { +sub } }
                            }
                        }
                    }
                }
            }

            val pickUp = props.pickUp
            val loadouts = props.loadouts

            if (pickUp != null || loadouts != null) {
                val split = if (pickUp != null && loadouts != null) styles.drawerBelowSplit else null

                div(classes = listOfNotNull(styles.drawerBelow, split).joinToString(" ")) {
                    pickUp?.let { half ->
                        section(classes = styles.drawerSection.toString()) { div { half() } }
                    }

                    loadouts?.let { half ->
                        section(classes = styles.drawerSection.toString()) { div { half() } }
                    }
                }
            }
        }
    }
}
