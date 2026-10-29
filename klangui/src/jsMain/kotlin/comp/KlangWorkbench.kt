/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import io.peekandpoke.klang.ui.GuiDance
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.html.onPointerCancel
import io.peekandpoke.ultra.html.onPointerDown
import io.peekandpoke.ultra.html.onPointerMove
import io.peekandpoke.ultra.html.onPointerUp
import io.peekandpoke.ultra.semanticui.SemanticIconFn
import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.StreamSource
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.html.DIV
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.a
import kotlinx.html.aside
import kotlinx.html.div
import kotlinx.html.main
import kotlinx.html.span
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.pointerevents.PointerEvent

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
    markLink: KlangWorkbench.Link? = null,
    layout: KlangWorkbench.Layout? = null,
    onLayout: ((KlangWorkbench.Layout) -> Unit)? = null,
    guiDance: GuiDance? = null,
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
        markLink = markLink,
        layout = layout,
        onLayout = onLayout,
        guiDance = guiDance,
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
 *   (items 22, 23), a link home when [Props.markLink] says where. An open tool window flies in over the content, or,
 *   pinned, docks beside it; each tool window keeps its own pin, as in IntelliJ. Opened from the strip, it takes the
 *   focus. Esc closes one flying in, and the focus goes back to its toggle; a pinned one stays.
 *
 * Esc and the panels (the drawer, a tool window flying in): wherever the focus is, also in a field that does not use
 * the Esc itself, one Esc closes the newest open panel, through the key manager (`KlangKeyBindings.PanelClose`, the
 * pill "Close: Esc" in its header, its [KlangCloseKey]). The drawer's content brings that close key (`KlangDrawer`
 * with `closeOnEsc`); the workbench draws only the frame.
 *
 * It draws the frame only; what goes in it (the Motor, the routes behind the drawer) is the app's.
 */
class KlangWorkbench(ctx: Ctx<Props>) : Component<KlangWorkbench.Props>(ctx) {

    internal companion object {
        /** A field to type in, which takes the focus first when the drawer opens (its search). */
        const val FIELD = "input:not([disabled]):not([type=hidden])"

        /** What can take the focus in the drawer: links, enabled fields and buttons, any tab stop. */
        const val FOCUSABLE = "a[href], button:not([disabled]), input:not([disabled]):not([type=hidden]), " +
                "select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex='-1'])"

        /** What in the bottom panel's header is not its handle: what one operates there, its tabs among them. */
        val NOT_A_HANDLE = "button, a, input, label, select, textarea, [role=button]"

        /** The bottom panel's height at first. */
        const val BOTTOM_HEIGHT_PX = 260.0

        /** The least the bottom panel shrinks to: its header and a few lines. */
        const val BOTTOM_LEAST_PX = 120.0

        /** The content kept above the bottom panel at its highest. */
        const val CONTENT_KEPT_PX = 120.0
    }

    /**
     * A place a link leads to: its [href], a real link (a new tab with a key held), and [onFollow] for a plain click,
     * which goes there inside the app.
     */
    data class Link(val href: String, val onFollow: () -> Unit)

    /** A tool window: its [id], its [title] (and toggle name), a [chip] (what it follows), its strip [icon]. */
    data class Tool(
        val id: String,
        val title: String,
        val icon: SemanticIconFn,
        val chip: String? = null,
        val content: DIV.() -> Unit,
    )

    /**
     * The bottom panel: its [title] and its strip toggle's [icon], and its [tabs] in its header (KLANG-00365; the
     * song page's Quick help and Lexikon), the first shown at first. One pin and one close serve them all.
     */
    data class Bottom(
        val title: String,
        val icon: SemanticIconFn,
        val tabs: List<Tab>,
        /** The walkthrough's step its key stands for: lit, the key wears the beacon; the panel opened uses it. */
        val beaconStep: String? = null,
    ) {
        init {
            require(tabs.isNotEmpty()) { "The bottom panel $title has no tabs" }
        }
    }

    /**
     * One of the bottom panel's tabs: its [id] (kept with the layout), its [title] in the header, its [content]; the
     * walkthrough's step its key stands for, [beaconStep]: lit, the key wears the beacon; the tab shown uses it.
     */
    data class Tab(
        val id: String,
        val title: String,
        val beaconStep: String? = null,
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
        /** Where the mark leads (the app: its start screen); null for a mark that is only a mark. */
        val markLink: Link?,
        /** The layout to start with, as [onLayout] last reported it (a page kept it over a reload); null for none. */
        val layout: Layout?,
        /** Gets the layout whenever it changed, to keep it; a drag of the bottom panel reports once, at its end. */
        val onLayout: ((Layout) -> Unit)?,
        /**
         * The app's walkthroughs: the bottom panel's key and its tabs wear the beacon while their step is lit
         * ([Bottom.beaconStep], [Tab.beaconStep]), and report the step used when the panel opens or the tab shows; null
         * for none.
         */
        val guiDance: GuiDance?,
    )

    /**
     * What of the bench outlives a reload (the maintainer, 2026-10-27: "When there are pinned tools they need to survive
     * reloads"): the tool windows [pinned], the pinned one [open], and the bottom panel's [bottomPinned], whether it is
     * open pinned ([bottomOpen]), its [bottomHeight] and the tab it shows ([bottomTab]). A window that only flies in is
     * a passing look: it is not kept.
     */
    data class Layout(
        val pinned: Set<String> = emptySet(),
        val open: String? = null,
        val bottomOpen: Boolean = false,
        val bottomPinned: Boolean = false,
        val bottomHeight: Double = BOTTOM_HEIGHT_PX,
        val bottomTab: String? = null,
    )

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    /** The open tool window's id; null when none is open. */
    private var openTool: String? by value(null)

    /** The tool windows pinned beside the content; each keeps its own pin. */
    private var pinned: Set<String> by value(props.layout?.pinned ?: emptySet())

    /**
     * The pinned tool window the kept layout had open, waiting for its page to bring it (the song page fills its tools
     * just after the bench is drawn); opened when it comes, dropped when the user opens another.
     */
    private var waitingTool: String? = props.layout?.open?.takeIf { it in pinned }

    private var bottomOpen: Boolean by value(props.layout?.let { it.bottomOpen && it.bottomPinned } ?: false)

    /** The bottom panel docked under the content; else it flies in over it. */
    private var bottomPinned: Boolean by value(props.layout?.bottomPinned ?: false)

    /** The bottom panel's height, pinned or flying in; its header, dragged, sets it (the maintainer, 2026-10-27). */
    private var bottomHeight: Double by value((props.layout?.bottomHeight ?: BOTTOM_HEIGHT_PX).coerceAtLeast(BOTTOM_LEAST_PX))

    /** The bottom panel's tab shown, by its id; the first when null, or when the panel has no such tab. */
    private var bottomTab: String? by value(props.layout?.bottomTab)

    /** The walkthroughs' step lit now; the beacons on the bottom panel's key and its tabs follow it. */
    private val litStep: String? by subscribingTo(props.guiDance?.lit ?: NoStepLit) { useSatisfied() }

    /**
     * A step already satisfied counts as used (the maintainer, 2026-10-29: "when the toolbox is open and the tour wants
     * me to click the icon it hides the tools ... preconditions to skip a step, f.e. when the tools are already open?"):
     * the bottom panel's key while the panel is open, a tab's key while it shows. Reported a moment later, outside the
     * notification or the update that found it, so a run of satisfied steps does not nest.
     */
    private fun useSatisfied() {
        if (litStep == null || props.bottom == null) {
            return
        }

        window.setTimeout({
            val step = litStep ?: return@setTimeout
            val bottom = props.bottom ?: return@setTimeout

            if (!bottomOpen) {
                return@setTimeout
            }

            val shown = bottom.tabs.firstOrNull { it.id == bottomTab } ?: bottom.tabs.first()

            if (step == bottom.beaconStep || step == shown.beaconStep) {
                used(step)
            }
        }, 0)
    }

    /** Whether [step] is the walkthroughs' lit one. */
    private fun lit(step: String?): Boolean = step != null && step == litStep

    /** Reports [step] used to the walkthroughs, when there is one. */
    private fun used(step: String?) {
        step?.let { props.guiDance?.used(it) }
    }

    /** The layout as last reported (or started with), so a redraw that changed nothing of it reports nothing. */
    private var reported: Layout = props.layout ?: Layout()

    /** A drag on the bottom panel's header: the pointer, where it started, and the height then. */
    private class BottomDrag(val pointerId: Int, val fromY: Double, val fromHeight: Double)

    private var bottomDrag: BottomDrag? = null

    private var drawerOpen: Boolean by value(false)

    /** Where the focus was when the drawer opened (the menu button); it goes back there when the drawer closes. */
    private var focusBeforeDrawer: HTMLElement? = null

    /** Where the focus was when a tool window opened (its toggle in the strip, the code for [showTool]); back there on Esc. */
    private var focusBeforeTool: HTMLElement? = null

    /** Where the focus was when the bottom panel opened, to give it back when the panel closes. */
    private var focusBeforeBottom: HTMLElement? = null

    init {
        openWaitingTool(props.tools)

        lifecycle {
            onUpdate {
                reportLayout()
                useSatisfied()
            }

            // A tool window or a drawer that leaves the props closes, so it does not open again by itself when it comes
            // back; a pinned one waits for it, as it would after a reload (a page left and come back to)
            onNextProps { next, _ ->
                val leaving = openTool

                if (leaving != null && next.tools.none { it.id == leaving }) {
                    openTool = null

                    if (leaving in pinned) {
                        waitingTool = leaving
                    }
                }

                openWaitingTool(next.tools)

                // The bottom panel flying in goes with its page; pinned, it waits for the next page that brings one. A
                // drag ends with it either way
                if (next.bottom == null) {
                    bottomDrag = null

                    if (!bottomPinned) {
                        bottomOpen = false
                    }
                }

                if (next.drawer == null) {
                    closeDrawer()
                }
            }
        }
    }

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    /** The kept layout's pinned tool window, once its page brought it. */
    private fun openWaitingTool(tools: List<Tool>) {
        val waiting = waitingTool ?: return

        if (tools.any { it.id == waiting }) {
            waitingTool = null

            if (openTool == null) {
                openTool = waiting
            }
        }
    }

    /** The layout now, as it outlives a reload. */
    private fun layout(): Layout = Layout(
        pinned = pinned,
        open = openTool?.takeIf { it in pinned } ?: waitingTool,
        bottomOpen = bottomOpen && bottomPinned,
        bottomPinned = bottomPinned,
        bottomHeight = bottomHeight,
        bottomTab = bottomTab,
    )

    /** Reports the layout when it changed, not in the middle of a drag (its end reports it). */
    private fun reportLayout() {
        val report = props.onLayout ?: return

        if (bottomDrag != null) {
            return
        }

        val now = layout()

        if (now != reported) {
            reported = now
            report(now)
        }
    }

    private fun toggleTool(id: String) {
        waitingTool = null

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
     * Shows the bottom panel from outside the strip (the song page's help, on Alt+Q or a hover: KLANG-00349), open if
     * it is already, at its [tab] when one is given; the focus stays where it is. Without a bottom panel it does
     * nothing.
     */
    fun showBottom(tab: String? = null) {
        if (props.bottom == null) {
            return
        }

        if (tab != null) {
            bottomTab = tab
        }

        if (!bottomOpen) {
            openBottom()
        }
    }

    /**
     * Shows the tool window [id] from outside the strip (a key in the editor, say), open if it is already; the focus
     * stays where it is, so the user goes on typing, and Esc in the window gives it back there. An [id] the props do
     * not hold does nothing. The song page's help went to the bottom panel ([showBottom]); kept for the pages to come.
     */
    fun showTool(id: String) {
        if (props.tools.none { it.id == id }) {
            return
        }

        waitingTool = null

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

    private fun openBottom() {
        focusBeforeBottom = document.activeElement as? HTMLElement
        bottomOpen = true
        used(props.bottom?.beaconStep)
    }

    /** Closes the bottom panel (its toggle, its close button; Esc when it flies in); a focus lost with it goes back. */
    private fun closeBottom() {
        bottomOpen = false
        bottomDrag = null

        val back = focusBeforeBottom
        focusBeforeBottom = null

        restoreFocus(back)
    }

    /** After the next draw, the bottom panel takes a focus that was lost on the way (its pin's key was redrawn). */
    private fun focusBottomFrame() {
        window.requestAnimationFrame {
            val now = document.activeElement

            if (now == null || now == document.body) {
                val frame = dom?.querySelector(
                    ".${KlangControlStyles.workbenchBottomPinned}, .${KlangControlStyles.workbenchBottomFloating}"
                )

                (frame as? HTMLElement)?.focus()
            }
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

                // The bottom panel: pinned, docked under the content; else flying in over it from below (the
                // maintainer, 2026-10-27: "a "fly in" and a pinned variant", as the tool windows have)
                if (bottom != null && bottomOpen) {
                    val placement = if (bottomPinned) styles.workbenchBottomPinned else styles.workbenchBottomFloating

                    div(classes = placement.toString()) {
                        key = if (bottomPinned) "bottom-pinned" else "bottom-floating"
                        attributes["tabindex"] = "-1"
                        css { put("height", "${bottomHeight}px") }

                        // Its header is its handle: dragged up or down, the panel grows or shrinks
                        onPointerDown { e -> startBottomDrag(e) }
                        onPointerMove { e -> dragBottom(e) }
                        onPointerUp {
                            bottomDrag = null
                            reportLayout()
                        }
                        onPointerCancel {
                            bottomDrag = null
                            reportLayout()
                        }

                        renderBottom(bottom)
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

                val keys = props.keys
                val mark = props.mark

                if (bottom != null || keys != null || mark != null) {
                    div(classes = styles.workbenchStripFoot.toString()) {
                        // The bottom panel's key at the foot, near the panel it opens; the top keeps the tool windows
                        // on the right (the maintainer, 2026-10-28)
                        if (bottom != null) {
                            KlangIconToggle(
                                icon = bottom.icon,
                                label = bottom.title,
                                on = bottomOpen,
                                large = true,
                                beacon = lit(bottom.beaconStep),
                                onClick = { if (bottomOpen) closeBottom() else openBottom() },
                            )
                        }

                        if (keys != null) {
                            div(classes = styles.workbenchStripKeys.toString()) {
                                attributes["role"] = "group"
                                attributes["aria-label"] = "View"

                                keys()
                            }
                        }

                        if (mark != null) {
                            div(classes = styles.workbenchMark.toString()) {
                                val link = props.markLink

                                if (link == null) {
                                    span { +mark }
                                } else {
                                    // Home, the same way as any app link: a plain click inside the app
                                    a(href = link.href) {
                                        onClick { event ->
                                            val mouse = event.asDynamic()

                                            if (mouse.button == 0 && mouse.ctrlKey != true && mouse.metaKey != true &&
                                                mouse.shiftKey != true && mouse.altKey != true
                                            ) {
                                                event.preventDefault()
                                                link.onFollow()
                                            }
                                        }

                                        +mark
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * A press on the bottom panel's header starts a resize: on its title or its empty stretch, not on anything one
     * operates (a key, a checkbox) nor on what the panel brings into its header (the maintainer, 2026-10-27: the "Show
     * on hover" checkbox was "caught by the dragging of the header").
     */
    private fun startBottomDrag(e: PointerEvent) {
        val target = e.target as? Element ?: return
        val styles = KlangControlStyles

        // The main button only: a right press opens a menu, which may keep the release, and a drag begun so would
        // take over the next left-button gesture that crosses the panel (a selection in the code, say)
        if (e.button.toInt() != 0) {
            return
        }

        if (target.closest(".${styles.toolWindowHeader}") == null || target.closest(NOT_A_HANDLE) != null) {
            return
        }

        // No text selected on the way
        e.preventDefault()

        // From the height it shows, which the room may hold below the kept one (a smaller window than the last visit's)
        bottomDrag = BottomDrag(
            pointerId = e.pointerId,
            fromY = e.clientY.toDouble(),
            fromHeight = bottomHeight.coerceAtMost(mostBottomHeight()),
        )

        // The moves and the release reach the panel also outside it; a pointer that is already gone cannot be captured
        runCatching { (e.currentTarget as? Element)?.setPointerCapture(e.pointerId) }
    }

    private fun dragBottom(e: PointerEvent) {
        val drag = bottomDrag ?: return

        if (e.pointerId != drag.pointerId) {
            return
        }

        // The main button's release went elsewhere (the panel closed under it, a window took it): the drag is over,
        // not stuck
        if ((e.buttons.toInt() and 1) == 0) {
            bottomDrag = null
            reportLayout()

            return
        }

        // Up makes it higher; it keeps some content above it, and some room of its own
        bottomHeight = (drag.fromHeight + (drag.fromY - e.clientY)).coerceIn(BOTTOM_LEAST_PX, mostBottomHeight())
    }

    /** The bottom panel's height at most: the room, some content kept above it, as its style's max-height says. */
    private fun mostBottomHeight(): Double {
        val centre = dom?.querySelector(".${KlangControlStyles.workbenchCentre}") as? HTMLElement

        return ((centre?.clientHeight ?: 0).toDouble() - CONTENT_KEPT_PX).coerceAtLeast(BOTTOM_LEAST_PX)
    }

    private fun FlowContent.renderBottom(bottom: Bottom) {
        KlangToolWindow(
            title = bottom.title,
            pinned = bottomPinned,
            onPinnedChange = { pin ->
                bottomPinned = pin
                // The panel moves between the content and the bench: a focus it held moves with it
                focusBottomFrame()
            },
            onClose = { closeBottom() },
            // Flying in, Esc closes it through the key manager; pinned, it is part of the bench
            closeOnEsc = !bottomPinned,
            edge = KlangToolWindow.Edge.Bottom,
            tabs = bottom.tabs.map { KlangToolWindow.Tab(id = it.id, title = it.title, beacon = lit(it.beaconStep)) },
            selectedTab = bottomTab,
            onTab = { id ->
                bottomTab = id
                used(bottom.tabs.firstOrNull { it.id == id }?.beaconStep)
            },
        ) {
            val shown = bottom.tabs.firstOrNull { it.id == bottomTab } ?: bottom.tabs.first()

            div {
                key = "tab-${shown.id}"
                attributes["role"] = "region"
                attributes["aria-label"] = shown.title
                // The panel's height, so a tab can split it into parts that scroll on their own (the Lexikon's list)
                css { put("height", "100%") }

                shown.content(this)
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

/** No walkthroughs: no step is ever lit. */
private val NoStepLit: Stream<String?> = StreamSource(null)
