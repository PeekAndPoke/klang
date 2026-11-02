/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import io.peekandpoke.klang.ui.GuiDance
import io.peekandpoke.klang.ui.KlangKeyBindings
import io.peekandpoke.klang.workshop.keys.OnKey
import io.peekandpoke.klang.workshop.keys.onKeyController
import io.peekandpoke.klang.workshop.keys.openKeyLayer
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.components.getAttributeRecursiveOrNull
import io.peekandpoke.kraft.routing.BackNavigationTrap
import io.peekandpoke.kraft.routing.BackNavigationTrap.Companion.trapBackNavigation
import io.peekandpoke.kraft.routing.Router
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
import io.peekandpoke.ultra.streams.Unsubscribe
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
    drawer: (FlowContent.(menu: KlangWorkbench.MenuHandle) -> Unit)? = null,
    drawerSide: (DIV.(menu: KlangWorkbench.MenuHandle) -> Unit)? = null,
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
        drawerSide = drawerSide,
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
 *   caller's: it gets the [Menu] to draw the menu button with.
 * - The Motor's menu (KLANG-00403): a modal of the workbench over everything right of the column (the content, the
 *   bottom panel, the tool windows, the strip), the same size whatever is pinned or open (the maintainer,
 *   2026-11-01: "it will always look the same and that is what you want from a navigation hub"). The page under it
 *   stays mounted (a song plays on) and is `inert` while it is open, and so is the column's list; the Motor stays. With
 *   a side of its own ([Props.drawerSide]) the menu lies over the waiting list too and fills the workbench.
 *   Opened (the menu button; Shift Shift or Ctrl+K, `KlangKeyBindings.SearchEverywhere`), it takes the focus; Esc,
 *   the same keys again, Back (it arms Kraft's back navigation trap) and any move to another page close it, and a focus lost with it goes
 *   back to where it came from (the menu button). A pick goes on with [MenuHandle.leaveTo], which takes the menu's
 *   place in the history.
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

        /**
         * The value of `inert` on what the menu covers. Not "": the renderer sets `inert` as the element's property,
         * where an empty string is false.
         */
        const val INERT = "true"
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
        /**
         * There on every page, not the page's own (the MIDI tool where a page has no panel): it never opens by itself,
         * only shown open while one of its tabs is the one last chosen, and a page's own panel flying in closes on the way
         * to it as on the way to none (the maintainer, 2026-11-02: closed until opened).
         */
        val ambient: Boolean = false,
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

    /**
     * What the open menu's content gets: the way to [close] it, and to [leaveTo] another page (the address, as the
     * router writes it). [leaveTo] writes the page in place of the history entry the menu's back navigation trap added,
     * so Back from there goes to the page the menu was opened on, not to it a second time; without a router it only
     * closes.
     */
    class MenuHandle(
        val close: () -> Unit,
        val leaveTo: (uri: String) -> Unit,
    )

    data class Props(
        val column: DIV.() -> Unit,
        /** The Motor; it gets the [Menu] for its menu button, null when there is no drawer to open. */
        val motor: FlowContent.(menu: Menu?) -> Unit,
        val content: DIV.() -> Unit,
        val tools: List<Tool>,
        val bottom: Bottom?,
        /** The menu the Motor's menu button opens; it gets the [MenuHandle] to close itself or to leave for a page. */
        val drawer: (FlowContent.(menu: MenuHandle) -> Unit)?,
        /**
         * The menu's left side, laid over the column's waiting list above the Motor while the menu is open: the menu then
         * fills the workbench, its Motor the column's own (the maintainer, 2026-11-02: "the Menu could be full size and
         * have its own Motor component. Then the left side is 'up for grabs' ... Put the performance graphs on the left
         * side"); null shows the waiting list. It is live for the pointer and the focus, outside the menu's key layer:
         * a way to a page from it leaves through the [MenuHandle] it gets ([MenuHandle.leaveTo]), as a pick does.
         */
        val drawerSide: (DIV.(menu: MenuHandle) -> Unit)?,
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

            if (!bottomShown()) {
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

    /** What the menu's content gets; made once, it closes and leaves through this workbench whatever its props. */
    private val menuHandle = MenuHandle(
        close = { closeDrawer() },
        leaveTo = { uri ->
            // Written in place of the trap's entry while the menu is still open, so the trap finds the address changed
            // and leaves the history as it is when it goes
            getAttributeRecursiveOrNull(Router.key)?.replaceUri(uri)
            closeDrawer()
        },
    )

    init {
        openWaitingTool(props.tools)

        lifecycle {
            onUpdate {
                reportLayout()
                useSatisfied()
            }

            // A tool window or a drawer that leaves the props closes, so it does not open again by itself when it comes
            // back; a pinned one waits for it, as it would after a reload (a page left and come back to)
            onNextProps { next, previous ->
                val leaving = openTool

                if (leaving != null && next.tools.none { it.id == leaving }) {
                    openTool = null

                    if (leaving in pinned) {
                        waitingTool = leaving
                    }
                }

                openWaitingTool(next.tools)

                // The bottom panel flying in goes with its page; pinned, it waits for the next page that brings one. A
                // drag ends with it either way. A page's own panel giving way to the one there on every page goes as to
                // none; between pages that both have only that one, nothing changes (props come anew at every redraw)
                val pageLeft = previous.bottom?.ambient != true && next.bottom?.ambient == true

                if (next.bottom == null || pageLeft) {
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

        if (!bottomShown()) {
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
        val bottom = props.bottom

        focusBeforeBottom = document.activeElement as? HTMLElement
        bottomOpen = true

        // A panel there on every page shows open only at its own tab: opened, its tab is the one chosen
        if (bottom != null && bottom.ambient && bottom.tabs.none { it.id == bottomTab }) {
            bottomTab = bottom.tabs.first().id
        }

        used(bottom?.beaconStep)
    }

    /**
     * Whether the bottom panel shows open: open, and, for a panel there on every page ([Bottom.ambient]), at one of its
     * own tabs, so the open state another page's panel left does not open it.
     */
    private fun bottomShown(): Boolean {
        val bottom = props.bottom ?: return false

        return bottomOpen && (!bottom.ambient || bottom.tabs.any { it.id == bottomTab })
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

        focusDrawer()
    }

    /** After the next draw: the drawer's field takes the focus (the search), else its first control, else the drawer. */
    private fun focusDrawer() {
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
            // The column and everything right of it, in the frame
            div(classes = styles.workbenchBody.toString()) {
                key = "body"

                renderBody(tool = tool, bottom = bottom)

                // The frame's inner corners, cut as a screen's: laid over them, never catching the mouse
                val corners = listOf(
                    styles.screenCornerTopLeft,
                    styles.screenCornerTopRight,
                    styles.screenCornerBottomLeft,
                    styles.screenCornerBottomRight,
                )

                for (corner in corners) {
                    div(classes = "${styles.screenCorner} $corner") {
                        attributes["aria-hidden"] = "true"
                    }
                }
            }
        }
    }

    /** The column, with the Motor at its foot, and everything right of it. */
    private fun FlowContent.renderBody(tool: Tool?, bottom: Bottom?) {
        val styles = KlangControlStyles

        aside(classes = styles.workbenchColumn.toString()) {
            key = "column"

            val drawer = props.drawer

            val drawerSide = props.drawerSide.takeIf { drawerOpen && drawer != null }

            div(classes = styles.workbenchColumnArea.toString()) {
                div(classes = styles.workbenchColumnScroll.toString()) {
                    key = "column-list"

                    // Under the open menu the column's list waits, kept as it is (its scroll, the focus to come back
                    // to): a page picked there would land past the menu's history entry, and Tab would leave the menu.
                    // The Motor below stays: its menu button closes it
                    if (drawerOpen && drawer != null) {
                        attributes["inert"] = INERT
                    }

                    // Under the menu's side not shown at all, its place and scroll kept: the side lies on the column's
                    // own brushed ground, and nothing of the list shows through it
                    if (drawerSide != null) {
                        css { put("visibility", "hidden") }
                    }

                    props.column(this)
                }

                // The open menu's own left side laid over the waiting list: the menu fills the workbench, the Motor
                // below its Motor
                if (drawerSide != null) {
                    div(classes = styles.workbenchColumnSide.toString()) {
                        key = "drawer-side"

                        drawerSide(menuHandle)
                    }
                }
            }

            div(classes = styles.workbenchMotor.toString()) {
                val menu = drawer?.let {
                    Menu(open = drawerOpen) { if (drawerOpen) closeDrawer() else openDrawer() }
                }

                // Shift Shift or Ctrl+K, the search over everything: the menu opens with the focus in its search field
                // (the maintainer, 2026-11-01: "now we also know what 'shift + shift' does"); open, its own keys answer,
                // and the same keys close it
                if (drawer != null) {
                    OnKey(action = KlangKeyBindings.SearchEverywhere, showHint = false) { openDrawer() }
                }

                props.motor(this, menu)
            }
        }

        // Everything right of the column, the menu laid over it while it is open
        div(classes = styles.workbenchRight.toString()) {
            key = "right"

            renderRight(tool = tool, bottom = bottom)

            val drawer = props.drawer

            if (drawerOpen && drawer != null) {
                KlangWorkbenchMenu(onClose = { closeDrawer() }) { drawer(menuHandle) }
            }
        }
    }

    /** The centre, a pinned tool window and the strip; `inert` while the menu covers them. */
    private fun FlowContent.renderRight(tool: Tool?, bottom: Bottom?) {
        val styles = KlangControlStyles
        val covered = drawerOpen && props.drawer != null

        main(classes = styles.workbenchCentre.toString()) {
            key = "centre"

            if (covered) {
                attributes["inert"] = INERT
            }

            div(classes = styles.workbenchContent.toString()) {
                key = "content"

                props.content(this)
            }

            // The bottom panel: pinned, docked under the content; else flying in over it from below (the
            // maintainer, 2026-10-27: "a "fly in" and a pinned variant", as the tool windows have)
            if (bottom != null && bottomShown()) {
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

                if (covered) {
                    attributes["inert"] = INERT
                }

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

            if (covered) {
                attributes["inert"] = INERT
            }

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
                            on = bottomShown(),
                            large = true,
                            beacon = lit(bottom.beaconStep),
                            onClick = { if (bottomShown()) closeBottom() else openBottom() },
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

@Suppress("FunctionName")
private fun Tag.KlangWorkbenchMenu(onClose: () -> Unit, content: DIV.() -> Unit) = comp(
    KlangWorkbenchMenu.Props(onClose = onClose, content = content)
) {
    KlangWorkbenchMenu(it)
}

/**
 * The open menu's frame over everything right of the column (KLANG-00403). Mounted only while the menu is open, so what
 * it arms lives exactly as long: Kraft's back navigation trap, as `FadingModal` has it (Back closes the menu and the
 * page stays; closed any other way, the trap goes back over its own entry), a watch on the router that closes the menu
 * when it moves to another page (a link inside it, Forward), and a layer of keys over the page's, as a modal has: the
 * page's keys wait under it. In it, the menu's own: its Esc (its content's close key), Alt+M closes it as the menu
 * button would, and so do Shift Shift and Ctrl+K, which opened it (the maintainer, 2026-11-02: "When the menu is open
 * 'Shift Shift' should close it again").
 */
private class KlangWorkbenchMenu(ctx: Ctx<Props>) : Component<KlangWorkbenchMenu.Props>(ctx) {

    data class Props(
        val onClose: () -> Unit,
        val content: DIV.() -> Unit,
    )

    /** Made first, so the keys of the content below it find it at once. */
    @Suppress("unused")
    private val layer = openKeyLayer()

    @Suppress("unused")
    private val menuKey = onKeyController(action = KlangKeyBindings.MotorMenu) { props.onClose() }

    @Suppress("unused")
    private val searchKey = onKeyController(action = KlangKeyBindings.SearchEverywhere) { props.onClose() }

    /** Back closes the menu and lets the navigation through, which only takes the trap's own entry. */
    @Suppress("unused")
    private val backTrap = trapBackNavigation {
        props.onClose()

        BackNavigationTrap.TrapResult.Continue
    }

    /** Ends the watch on the router's page, while mounted. */
    private var unwatchPage: Unsubscribe? = null

    init {
        lifecycle {
            onMount {
                // As FadingModal: a move to another page closes it, a query-only replaceUri (a urlParam) does not. Under
                // hash routing the router follows a written address a task later: until it arrives, the page it is still
                // on is no reason to close
                getAttributeRecursiveOrNull(Router.key)?.let { router ->
                    val page = Router.pageOf(router.strategy.getUriFromWindowLocation())
                    var stillOn = Router.pageOf(router.current().uri)

                    unwatchPage = router.current.subscribeToStream {
                        when (Router.pageOf(it.uri)) {
                            page -> stillOn = page
                            stillOn -> Unit
                            else -> props.onClose()
                        }
                    }
                }
            }

            onUnmount {
                unwatchPage?.invoke()
                unwatchPage = null
            }
        }
    }

    override fun VDom.render() {
        div(classes = KlangControlStyles.workbenchDrawer.toString()) {
            // The focus lands here when the menu has no control of its own
            attributes["tabindex"] = "-1"

            props.content(this)
        }
    }
}

/** No walkthroughs: no step is ever lit. */
private val NoStepLit: Stream<String?> = StreamSource(null)
