/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.onKeyDown
import io.peekandpoke.ultra.semanticui.SemanticIconFn
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.html.DIV
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.aside
import kotlinx.html.div
import kotlinx.html.main
import kotlinx.html.section
import kotlinx.html.span
import org.w3c.dom.HTMLElement

@Suppress("FunctionName")
fun Tag.KlangWorkbench(
    column: DIV.() -> Unit,
    motor: FlowContent.(menu: KlangWorkbench.Menu?) -> Unit,
    content: DIV.() -> Unit,
    tools: List<KlangWorkbench.Tool> = emptyList(),
    bottom: KlangWorkbench.Bottom? = null,
    drawer: (FlowContent.(close: () -> Unit) -> Unit)? = null,
    mark: String? = null,
) = comp(
    KlangWorkbench.Props(
        column = column,
        motor = motor,
        content = content,
        tools = tools,
        bottom = bottom,
        drawer = drawer,
        mark = mark,
    )
) {
    KlangWorkbench(it)
}

/**
 * The workbench (KLANG-00340, KLANG-00334 layout A): the shell the pages live in.
 *
 * - The column on the left: its content scrolls on its own, the Motor under it never moves (item 25). The Motor is the
 *   caller's: it gets the [Menu] to draw the menu button with, and the drawer opens just above it. Opened, the
 *   drawer takes the focus (Esc then closes it); closed, the focus goes back to where it came from (the menu
 *   button).
 * - The content in the middle, and under it the bottom panel, docked, shown or hidden from the strip.
 * - The tool strip on the right: a toggle per tool window, the bottom panel's toggle, the maker's [Props.mark]
 *   written up its foot (items 22, 23). An open tool window flies in over the content, or, pinned, docks beside it;
 *   each tool window keeps its own pin, as in IntelliJ.
 *
 * It draws the frame only; what goes in it (the Motor, the routes behind the drawer) is the app's.
 */
class KlangWorkbench(ctx: Ctx<Props>) : Component<KlangWorkbench.Props>(ctx) {

    private companion object {
        /** What can take the focus in the drawer: links, enabled fields and buttons, any tab stop. */
        const val FOCUSABLE = "a[href], button:not([disabled]), input:not([disabled]):not([type=hidden]), " +
                "select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex='-1'])"
    }

    /** A tool window: its [id], its [title] (and toggle name), a [chip] (what it follows), its strip [icon]. */
    data class Tool(
        val id: String,
        val title: String,
        val icon: SemanticIconFn,
        val chip: String? = null,
        val content: DIV.() -> Unit,
    )

    /** The bottom panel: its [title] and its [content]; [icon] is its strip toggle. */
    data class Bottom(
        val title: String,
        val icon: SemanticIconFn,
        val content: DIV.() -> Unit,
    )

    /** What the Motor's menu button needs: whether the drawer is open, and the way to open or close it. */
    data class Menu(
        val open: Boolean,
        val toggle: () -> Unit,
    )

    data class Props(
        val column: DIV.() -> Unit,
        /** The Motor; it gets the [Menu] for its menu button, null when there is no drawer to open. */
        val motor: FlowContent.(menu: Menu?) -> Unit,
        val content: DIV.() -> Unit,
        val tools: List<Tool>,
        val bottom: Bottom?,
        /** The drawer the Motor's menu button opens; it gets the way to close itself (after a pick, say). */
        val drawer: (FlowContent.(close: () -> Unit) -> Unit)?,
        /** The maker's mark at the strip's foot ("KLANGMOTOR"); null for none. */
        val mark: String?,
    )

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    /** The open tool window's id; null when none is open. */
    private var openTool: String? by value(null)

    /** The tool windows pinned beside the content; each keeps its own pin. */
    private var pinned: Set<String> by value(emptySet())

    private var bottomOpen: Boolean by value(false)

    private var drawerOpen: Boolean by value(false)

    /** Where the focus was when the drawer opened (the menu button); it goes back there when the drawer closes. */
    private var focusBeforeDrawer: HTMLElement? = null

    init {
        lifecycle {
            // A tool window or a drawer that leaves the props closes, so it does not open again by itself when it comes back
            onNextProps { next, _ ->
                if (openTool != null && next.tools.none { it.id == openTool }) {
                    openTool = null
                }

                if (next.drawer == null) {
                    closeDrawer()
                }
            }
        }
    }

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    private fun toggleTool(id: String) {
        openTool = if (openTool == id) null else id
    }

    private fun openDrawer() {
        focusBeforeDrawer = document.activeElement as? HTMLElement
        drawerOpen = true

        // After the drawer is drawn: its first control takes the focus, or the drawer itself when it has none
        window.requestAnimationFrame {
            val drawer = dom?.querySelector(".${KlangControlStyles.workbenchDrawer}")
            val target = drawer?.querySelector(FOCUSABLE) ?: drawer

            (target as? HTMLElement)?.focus()
        }
    }

    private fun closeDrawer() {
        if (!drawerOpen) {
            return
        }

        drawerOpen = false

        val back = focusBeforeDrawer
        focusBeforeDrawer = null

        // Only a focus lost with the drawer goes back; one a pick put elsewhere (a page's editor) stays there
        window.requestAnimationFrame {
            val now = document.activeElement

            if (now == null || now == document.body) {
                back?.focus()
            }
        }
    }

    override fun VDom.render() {
        val styles = KlangControlStyles
        val tool = props.tools.firstOrNull { it.id == openTool }
        val bottom = props.bottom

        div(classes = styles.workbench.toString()) {
            aside(classes = styles.workbenchColumn.toString()) {
                val drawer = props.drawer

                div(classes = styles.workbenchColumnArea.toString()) {
                    div(classes = styles.workbenchColumnScroll.toString()) {
                        props.column(this)
                    }

                    if (drawerOpen && drawer != null) {
                        div(classes = styles.workbenchDrawer.toString()) {
                            // The focus lands here when the drawer has no control of its own
                            attributes["tabindex"] = "-1"

                            // Esc closes the drawer; the focus is in it from the moment it opens
                            onKeyDown { e ->
                                if (e.key == "Escape") {
                                    e.preventDefault()
                                    closeDrawer()
                                }
                            }

                            drawer { closeDrawer() }
                        }
                    }
                }

                div(classes = styles.workbenchMotor.toString()) {
                    val menu = drawer?.let {
                        Menu(open = drawerOpen) { if (drawerOpen) closeDrawer() else openDrawer() }
                    }

                    props.motor(this, menu)
                }
            }

            main(classes = styles.workbenchCentre.toString()) {
                div(classes = styles.workbenchContent.toString()) {
                    props.content(this)
                }

                if (bottom != null && bottomOpen) {
                    section(classes = styles.workbenchBottom.toString()) {
                        attributes["aria-label"] = bottom.title

                        div(classes = styles.workbenchBottomHeader.toString()) {
                            span(classes = styles.toolWindowTitle.toString()) { +bottom.title }
                        }

                        div(classes = styles.workbenchBottomBody.toString()) {
                            bottom.content(this)
                        }
                    }
                }

                if (tool != null && tool.id !in pinned) {
                    div(classes = styles.workbenchFloating.toString()) {
                        renderTool(tool)
                    }
                }
            }

            if (tool != null && tool.id in pinned) {
                div(classes = styles.workbenchPinned.toString()) {
                    renderTool(tool)
                }
            }

            // A group, not a toolbar: a toolbar promises arrow keys and one tab stop, which the strip does not have yet
            div(classes = styles.workbenchStrip.toString()) {
                attributes["role"] = "group"
                attributes["aria-label"] = "Tool windows"

                for (t in props.tools) {
                    KlangIconToggle(
                        icon = t.icon,
                        label = t.title,
                        on = t.id == openTool,
                        large = true,
                        onClick = { toggleTool(t.id) },
                    )
                }

                if (bottom != null) {
                    KlangIconToggle(
                        icon = bottom.icon,
                        label = bottom.title,
                        on = bottomOpen,
                        large = true,
                        onClick = { bottomOpen = !bottomOpen },
                    )
                }

                props.mark?.let { mark ->
                    div(classes = styles.workbenchMark.toString()) {
                        span { +mark }
                    }
                }
            }
        }
    }

    private fun FlowContent.renderTool(tool: Tool) {
        KlangToolWindow(
            title = tool.title,
            chip = tool.chip,
            pinned = tool.id in pinned,
            onPinnedChange = { pin -> pinned = if (pin) pinned + tool.id else pinned - tool.id },
            onClose = { openTool = null },
        ) {
            tool.content(this)
        }
    }
}
