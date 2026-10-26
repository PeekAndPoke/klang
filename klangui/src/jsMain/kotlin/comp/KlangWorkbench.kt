/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.key
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
    keys: (FlowContent.() -> Unit)? = null,
    mark: String? = null,
) = comp(
    KlangWorkbench.Props(
        column = column,
        motor = motor,
        content = content,
        tools = tools,
        bottom = bottom,
        drawer = drawer,
        keys = keys,
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
 * - The tool strip on the right: a toggle per tool window, the bottom panel's toggle; at its foot the caller's
 *   [Props.keys] for every page (the app's fullscreen: KLANG-00349) and the maker's [Props.mark] written upwards
 *   (items 22, 23). An open tool window flies in over the content, or, pinned, docks beside it;
 *   each tool window keeps its own pin, as in IntelliJ. Opened from the strip, it takes the focus. Esc closes one
 *   flying in, and the focus goes back to its toggle; a pinned one stays.
 *
 * Esc and the panels (the drawer, a tool window flying in): wherever the focus is, also in a field that does not use
 * the Esc itself, one Esc closes the newest open panel, through the key manager (`KlangKeyBindings.PanelClose`, the
 * pill "Close: Esc" in its header, its [KlangCloseKey]). The drawer's content brings that close key (`KlangDrawer`
 * with `closeOnEsc`); the workbench draws only the frame.
 *
 * It draws the frame only; what goes in it (the Motor, the routes behind the drawer) is the app's.
 */
class KlangWorkbench(ctx: Ctx<Props>) : Component<KlangWorkbench.Props>(ctx) {

    private companion object {
        /** A field to type in, which takes the focus first when the drawer opens (its search). */
        const val FIELD = "input:not([disabled]):not([type=hidden])"

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
        /** Keys at the strip's foot, above the mark, for what every page has (fullscreen); null for none. */
        val keys: (FlowContent.() -> Unit)?,
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

    /** Where the focus was when a tool window opened (its toggle in the strip, the code for [showTool]); back there on Esc. */
    private var focusBeforeTool: HTMLElement? = null

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
        if (openTool == id) {
            openTool = null
            focusBeforeTool = null

            return
        }

        focusBeforeTool = document.activeElement as? HTMLElement
        openTool = id

        focusToolFrame()
    }

    /**
     * Shows the tool window [id] from outside the strip (a key in the editor, as Alt+Q shows the help: KLANG-00349),
     * open if it is already; the focus stays where it is, so the user goes on typing, and Esc in the window gives it
     * back there. An [id] the props do not hold does nothing.
     */
    fun showTool(id: String) {
        if (props.tools.none { it.id == id }) {
            return
        }

        if (openTool != id) {
            focusBeforeTool = document.activeElement as? HTMLElement
        }

        openTool = id
    }

    /** After the next draw, the open tool window takes the focus, so Tab goes on inside it. */
    private fun focusToolFrame() {
        window.requestAnimationFrame {
            val frame = dom?.querySelector(".${KlangControlStyles.workbenchFloating}, .${KlangControlStyles.workbenchPinned}")

            (frame as? HTMLElement)?.focus()
        }
    }

    /** Closes the open tool window (its close button; Esc when it flies in) and gives the focus back to where it was. */
    private fun closeTool() {
        openTool = null

        val back = focusBeforeTool
        focusBeforeTool = null

        restoreFocus(back)
    }

    /** Gives the focus back to [back], but only when it was lost with what closed (a pick may have moved it on). */
    private fun restoreFocus(back: HTMLElement?) {
        window.requestAnimationFrame {
            val now = document.activeElement

            if (now == null || now == document.body) {
                back?.focus()
            }
        }
    }

    private fun openDrawer() {
        focusBeforeDrawer = document.activeElement as? HTMLElement
        drawerOpen = true

        // After the drawer is drawn: its field takes the focus (the search), else its first control, else the drawer
        window.requestAnimationFrame {
            val drawer = dom?.querySelector(".${KlangControlStyles.workbenchDrawer}")
            val target = drawer?.querySelector(FIELD) ?: drawer?.querySelector(FOCUSABLE) ?: drawer

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
        restoreFocus(back)
    }

    override fun VDom.render() {
        val styles = KlangControlStyles
        val tool = props.tools.firstOrNull { it.id == openTool }
        val bottom = props.bottom

        // Keys on the parts that come and go, so a part that appears never takes over its neighbour's element (and the
        // focus in it): the pinned window between the centre and the strip, the bottom panel before a floating window
        div(classes = styles.workbench.toString()) {
            aside(classes = styles.workbenchColumn.toString()) {
                key = "column"

                val drawer = props.drawer

                div(classes = styles.workbenchColumnArea.toString()) {
                    div(classes = styles.workbenchColumnScroll.toString()) {
                        props.column(this)
                    }

                    if (drawerOpen && drawer != null) {
                        div(classes = styles.workbenchDrawer.toString()) {
                            // The focus lands here when the drawer has no control of its own
                            attributes["tabindex"] = "-1"

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
                key = "centre"

                div(classes = styles.workbenchContent.toString()) {
                    key = "content"

                    props.content(this)
                }

                if (bottom != null && bottomOpen) {
                    section(classes = styles.workbenchBottom.toString()) {
                        key = "bottom"

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
                        // Per tool: another tool window is a new panel, its close key registers anew, so the newest
                        // open panel closes first
                        key = "floating-${tool.id}"

                        // The focus lands here when the window opens from the strip
                        attributes["tabindex"] = "-1"

                        renderTool(tool)
                    }
                }
            }

            if (tool != null && tool.id in pinned) {
                div(classes = styles.workbenchPinned.toString()) {
                    key = "pinned"

                    // The focus lands here when the window opens from the strip
                    attributes["tabindex"] = "-1"

                    renderTool(tool)
                }
            }

            // A group, not a toolbar: a toolbar promises arrow keys and one tab stop, which the strip does not have yet
            div(classes = styles.workbenchStrip.toString()) {
                key = "strip"

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

                val keys = props.keys
                val mark = props.mark

                if (keys != null || mark != null) {
                    div(classes = styles.workbenchStripFoot.toString()) {
                        if (keys != null) {
                            div(classes = styles.workbenchStripKeys.toString()) {
                                attributes["role"] = "group"
                                attributes["aria-label"] = "View"

                                keys()
                            }
                        }

                        if (mark != null) {
                            div(classes = styles.workbenchMark.toString()) {
                                span { +mark }
                            }
                        }
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
            onPinnedChange = { pin ->
                pinned = if (pin) pinned + tool.id else pinned - tool.id
                // The window moves between the content and the bench: the focus moves with it
                focusToolFrame()
            },
            onClose = { closeTool() },
            // Flying in, Esc closes it through the key manager; pinned, it is part of the bench
            closeOnEsc = tool.id !in pinned,
        ) {
            tool.content(this)
        }
    }
}
