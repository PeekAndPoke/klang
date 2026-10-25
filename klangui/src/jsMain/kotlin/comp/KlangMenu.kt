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
import io.peekandpoke.ultra.html.onFocus
import io.peekandpoke.ultra.html.onKeyDown
import kotlinx.html.ButtonType
import kotlinx.html.Tag
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.span
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.KeyboardEvent

@Suppress("FunctionName")
fun Tag.KlangMenu(
    label: String,
    items: List<KlangMenu.Item>,
    onClose: () -> Unit = {},
    focusOnOpen: Boolean = true,
) = comp(
    KlangMenu.Props(label = label, items = items, onClose = onClose, focusOnOpen = focusOnOpen)
) {
    KlangMenu(it)
}

/**
 * A menu (KLANG-00339): a riveted panel holding a `menu` of `menuitem`s, each with an optional key hint.
 *
 * The keys a menu takes: Up and Down move between the items that can be chosen (round the ends), Home and End go to
 * the first and the last, Enter and Space choose (they are buttons), Esc closes it, Tab closes it and moves on.
 * Choosing an item runs it and closes the menu. One item is in the tab order, the last one focused (at first the
 * first that can be chosen), so Tab reaches the menu and comes back to where it was. Opened, it focuses that item,
 * unless [Props.focusOnOpen] is off (a menu shown in place, on the kitchen sink). Its size and place are the caller's.
 */
class KlangMenu(ctx: Ctx<Props>) : Component<KlangMenu.Props>(ctx) {

    /** One entry: its [label], a printed [hint] (the key that does the same), whether it can be chosen. */
    data class Item(
        val label: String,
        val onSelect: () -> Unit,
        val hint: String? = null,
        val enabled: Boolean = true,
    )

    data class Props(
        /** What the menu is for, for screen readers ("Code actions"). */
        val label: String,
        val items: List<Item>,
        val onClose: () -> Unit,
        val focusOnOpen: Boolean,
    )

    /** The item in the tab order, as its index in [Props.items]; null before the first render picks one. */
    private var tabStop: Int? by value(null)

    init {
        lifecycle {
            onMount {
                if (props.focusOnOpen) {
                    enabledItems().firstOrNull()?.focus()
                }
            }
        }
    }

    /** The items that can be chosen, as the browser holds them, in order. */
    private fun enabledItems(): List<HTMLElement> {
        val list = dom?.querySelectorAll("[role=menuitem]:not([disabled])") ?: return emptyList()

        return (0 until list.length).mapNotNull { list.item(it) as? HTMLElement }
    }

    private fun focusAt(index: Int) {
        val items = enabledItems()

        if (items.isEmpty()) {
            return
        }

        items[index.mod(items.size)].focus()
    }

    private fun key(e: KeyboardEvent) {
        val items = enabledItems()
        val at = items.indexOfFirst { it == e.target }

        // Tab leaves the menu and closes it; the focus moves on as Tab moves it
        if (e.key == "Tab") {
            props.onClose()

            return
        }

        when (e.key) {
            "ArrowDown" -> focusAt(at + 1)
            "ArrowUp" -> focusAt(if (at < 0) items.size - 1 else at - 1)
            "Home" -> focusAt(0)
            "End" -> focusAt(items.size - 1)
            "Escape" -> props.onClose()
            else -> return
        }

        e.preventDefault()
        e.stopPropagation()
    }

    override fun VDom.render() {
        val styles = KlangControlStyles

        // The tab stop: the one last focused, else the first item that can be chosen
        val stop = tabStop?.takeIf { props.items.getOrNull(it)?.enabled == true }
            ?: props.items.indexOfFirst { it.enabled }

        KlangPanel(tight = true) {
            div {
                attributes["role"] = "menu"
                attributes["aria-label"] = props.label

                onKeyDown { e -> key(e) }

                for ((index, item) in props.items.withIndex()) {
                    button(type = ButtonType.button, classes = styles.menuItem.toString()) {
                        attributes["role"] = "menuitem"
                        attributes["tabindex"] = if (index == stop) "0" else "-1"

                        onFocus { tabStop = index }

                        if (!item.enabled) {
                            disabled = true
                        }

                        onClick {
                            item.onSelect()
                            props.onClose()
                        }

                        span { +item.label }

                        item.hint?.let { hint -> span(classes = styles.menuHint.toString()) { +hint } }
                    }
                }
            }
        }
    }
}
