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
    tabs: List<KlangToolWindow.Tab> = emptyList(),
    selectedTab: String? = null,
    onTab: ((String) -> Unit)? = null,
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
        tabs = tabs,
        selectedTab = selectedTab,
        onTab = onTab,
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
 * size and place are the shell's.
 *
 * With [Props.tabs] its header holds them in its title's place (KLANG-00365; the maintainer, 2026-10-27: "the tabs
 * should be here", and "only pin and close stay there"): the window is one, its pin and close are the window's, a
 * tab only picks what it shows. A tab's own options are in its content.
 */
class KlangToolWindow(ctx: Ctx<Props>) : Component<KlangToolWindow.Props>(ctx) {

    /** Where it flies in from: the strip's side, or under the editor (the bottom panel, KLANG-00349). */
    enum class Edge(val pinLabel: String) {
        Right(pinLabel = "Pin beside the editor"),
        Bottom(pinLabel = "Pin under the editor"),
    }

    /** One of the window's tabs: its [id], and its [title] in the header. */
    data class Tab(val id: String, val title: String)

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
        /** The tabs in the header, in the title's place; none for the title. */
        val tabs: List<Tab>,
        /** The tab shown; the first when null or unknown. */
        val selectedTab: String?,
        /** Gets the tab pressed. */
        val onTab: ((String) -> Unit)?,
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
                if (props.tabs.isEmpty()) {
                    span(classes = styles.toolWindowTitle.toString()) { +props.title }
                } else {
                    renderTabs()
                }

                props.chip?.let { chip -> span(classes = styles.toolWindowChip.toString()) { +chip } }

                div { attributes["style"] = "flex: 1" }

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

    /**
     * The tabs, the one shown pressed; a press picks it. Keys in a group, as the strip's toggles are: the tab pattern
     * (`tablist`) promises arrow keys and one tab stop, which these do not have yet.
     */
    private fun FlowContent.renderTabs() {
        val styles = KlangControlStyles
        val shown = props.tabs.firstOrNull { it.id == props.selectedTab } ?: props.tabs.first()

        div(classes = styles.toolWindowTabs.toString()) {
            attributes["role"] = "group"
            attributes["aria-label"] = props.title

            for (tab in props.tabs) {
                button(type = ButtonType.button, classes = styles.toolWindowTab.toString()) {
                    attributes["aria-pressed"] = (tab == shown).toString()
                    onClick { props.onTab?.invoke(tab.id) }
                    +tab.title
                }
            }
        }
    }
}
