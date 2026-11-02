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
import kotlinx.css.px
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
    lit: String? = current,
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
        lit = lit,
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
 * icon, its label and its second line ("The button like 'Songs' can then also be bigger", the maintainer, 2026-11-01).
 * Below them two halves: what one comes back to ([Props.pickUp]) and the loadouts ([Props.loadouts], the look picker);
 * one given alone takes the whole width. The current destination is drawn pressed and announced as `aria-current`; the
 * lit one ([Props.lit], the current one unless told otherwise) has its lamp on, the others' are off; one that is not
 * there yet ([Entry.later]) is a dashed tile with an unlit lamp that cannot be picked. A tile's lamp sits before its
 * label, its second line under the label's text, its icon at the right end. The drawer fills the size it is given: its
 * size and where it opens are the shell's.
 */
class KlangDrawer(ctx: Ctx<Props>) : Component<KlangDrawer.Props>(ctx) {

    companion object {
        /** A tile's lamp, before its label; the second line is indented by it and the gap after it. */
        const val LAMP_PX = 8
        const val LAMP_GAP_PX = 8
    }

    /** One destination: [id] is what [Props.onPick] gets, [sub] a short line under the [label], [icon] at the right. */
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
        /** The id of the destination the user is at, drawn pressed; null for none. */
        val current: String?,
        val onPick: (String) -> Unit,
        /**
         * The id of the destination whose lamp is lit: the one the user's page belongs to, also where the tile is not
         * [current] (the maintainer, 2026-11-02: "when the user is in the learn section turn on the led on the learn
         * button"); null for none.
         */
        val lit: String?,
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
                    ) {
                        // The lamp before the label, the second line under the label's text, the icon at the right end
                        // (the maintainer, 2026-11-02: "Put the led left of the headline. Put the icon on the right")
                        div(classes = styles.drawerTile.toString()) {
                            div(classes = styles.drawerTileText.toString()) {
                                span(classes = styles.drawerTileHead.toString()) {
                                    KlangLamp(
                                        state = when {
                                            entry.later -> KlangLamp.State.Unlit
                                            entry.id == props.lit -> KlangLamp.State.On
                                            else -> KlangLamp.State.Off
                                        },
                                        size = LAMP_PX.px,
                                    )
                                    span(classes = styles.drawerLabel.toString()) { +entry.label }
                                }

                                entry.sub?.let { sub -> span(classes = styles.drawerTileSub.toString()) { +sub } }
                            }

                            entry.icon?.let { iconFn ->
                                icon.iconFn().render { attributes["aria-hidden"] = "true" }
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
