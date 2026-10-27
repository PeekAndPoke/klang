/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.ui.KlangKeyBindings
import io.peekandpoke.klang.workshop.keys.KeyPlatform
import io.peekandpoke.klang.workshop.keys.KeysManager
import io.peekandpoke.klang.workshop.keys.keys
import io.peekandpoke.kraft.components.ComponentRef
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.testing.KQuery
import io.peekandpoke.kraft.testing.TestBed
import io.peekandpoke.kraft.testing.eventually
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.html.onKeyDown
import kotlinx.browser.document
import kotlinx.coroutines.delay
import kotlinx.css.height
import kotlinx.css.px
import kotlinx.css.width
import kotlinx.html.FlowContent
import kotlinx.html.InputType
import kotlinx.html.Tag
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.span
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.EventTarget
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.KeyboardEventInit
import org.w3c.dom.pointerevents.PointerEvent
import org.w3c.dom.pointerevents.PointerEventInit

/** The workbench the host draws, so a spec can show a tool window from outside, as the song page does on Alt+Q. */
private val workbenchRef = ComponentRef.Tracker<KlangWorkbench>()

/** A workbench whose tool windows and drawer its parent changes, as a page's may. */
private class ChangingHost(ctx: NoProps) : PureComponent(ctx) {
    companion object {
        var current: ChangingHost? = null

        /** Whether the next host starts with its Docs tool window. */
        var startWithDocs = true

        /** The layout the next host gives its workbench to start with. */
        var startLayout: KlangWorkbench.Layout? = null

        /** What the workbench reported of its layout, oldest first. */
        val reports = mutableListOf<KlangWorkbench.Layout>()

        fun reset() {
            startWithDocs = true
            startLayout = null
            reports.clear()
        }
    }

    var withDocs: Boolean by value(startWithDocs)

    var withDrawer: Boolean by value(true)

    var withBottom: Boolean by value(true)

    init {
        current = this
    }

    override fun VDom.render() {
        val drawer: (FlowContent.(close: () -> Unit) -> Unit)? = if (withDrawer) { _ -> button { +"DRAWER" } } else null

        KlangWorkbench(
            column = {},
            motor = { menu ->
                button {
                    attributes["data-test"] = "motor-menu"
                    attributes["data-menu"] = (menu != null).toString()
                    onClick { menu?.toggle?.invoke() }
                    +"MOTOR"
                }
            },
            content = {},
            tools = if (withDocs) listOf(KlangWorkbench.Tool(id = "docs", title = "Docs", icon = { book }) { +"DOCS" }) else emptyList(),
            bottom = if (!withBottom) null else KlangWorkbench.Bottom(
                title = "Help",
                icon = { question_circle },
                header = {
                    button { +"HOVER" }
                    label {
                        input(type = InputType.checkBox) { attributes["data-test"] = "header-check" }
                    }
                },
            ) {
                +"HELP BODY"
            },
            drawer = drawer,
            layout = startLayout,
            onLayout = { reports.add(it) },
        ).track(workbenchRef)
    }
}

/** A test bed with the app's key manager: the workbench's close keys answer through it (KLANG-00334 item 39). */
private suspend fun keyedBed(view: VDom.() -> Any?, test: suspend (element: KQuery<Element>) -> Unit) {
    val manager = KeysManager(platform = KeyPlatform.Pc, hintDelayMs = 10).apply { define(KlangKeyBindings) }

    try {
        TestBed.preact(appSetup = { keys(manager) }, view = view, test = test)
    } finally {
        manager.dispose()
    }
}

@Suppress("FunctionName")
private fun Tag.ChangingHost() = comp { ChangingHost(it) }

/**
 * The workbench (KLANG-00340): the column's content scrolls apart from the Motor, the strip opens the tool windows
 * (flying in or pinned) and the bottom panel, the Motor's menu opens the drawer, the mark sits at the strip's foot.
 */
class KlangWorkbenchSpec : StringSpec({

    val styles = KlangControlStyles

    suspend fun withWorkbench(block: suspend (root: Element) -> Unit) {
        keyedBed(
            view = {
                KlangWorkbench(
                    column = { span { +"PROJECT" } },
                    motor = { menu ->
                        button {
                            attributes["data-test"] = "motor-menu"
                            attributes["data-open"] = menu?.open.toString()
                            onClick { menu?.toggle?.invoke() }
                            +"MOTOR"
                        }
                    },
                    content = {
                        span { +"CONTENT" }
                        button {
                            attributes["data-test"] = "content-target"
                        }
                    },
                    tools = listOf(
                        KlangWorkbench.Tool(id = "docs", title = "Docs", icon = { book }) { span { +"DOCS BODY" } },
                        KlangWorkbench.Tool(id = "keys", title = "Keys", icon = { keyboard }) { span { +"KEYS BODY" } },
                    ),
                    bottom = KlangWorkbench.Bottom(title = "Analysis", icon = { chart_bar }) { span { +"BOTTOM BODY" } },
                    drawer = { close ->
                        // A drawer's content brings its close key, as KlangDrawer with closeOnEsc does in the app
                        KlangCloseKey(onClose = close)
                        button {
                            attributes["data-test"] = "drawer-close"
                            onClick { close() }
                            +"DRAWER"
                        }
                        // A pick that puts the focus elsewhere, as a destination whose page focuses its editor
                        button {
                            attributes["data-test"] = "drawer-pick"
                            onClick {
                                (document.querySelector("[data-test=content-target]") as HTMLElement).focus()
                                close()
                            }
                        }
                    },
                    keys = {
                        button {
                            attributes["aria-label"] = "Fullscreen"
                        }
                    },
                    mark = "KLANGMOTOR",
                )
            },
        ) { root ->
            block(root.single())
        }
    }

    fun Element.q(selector: String): HTMLElement? = querySelector(selector) as HTMLElement?

    fun Element.toggle(label: String): HTMLElement = q("[role=group] button[aria-label='$label']")!!

    "the column holds its content in a scroller of its own; the Motor sits below it, outside the scroller" {
        withWorkbench { root ->
            val scroller = root.q(".${styles.workbenchColumnScroll}")!!
            val motor = root.q(".${styles.workbenchMotor}")!!

            scroller.textContent shouldBe "PROJECT"
            motor.textContent shouldBe "MOTOR"
            scroller.contains(motor) shouldBe false
            // The scroller sits in the area above the Motor, beside it in the column
            scroller.parentElement?.className shouldBe styles.workbenchColumnArea.toString()
            (scroller.parentElement?.parentElement == motor.parentElement) shouldBe true
        }
    }

    "the strip has a toggle per tool window and one for the bottom panel; at its foot the keys, then the mark" {
        withWorkbench { root ->
            val strip = root.q("[role=group][aria-label='Tool windows']")!!
            val toggles = strip.querySelectorAll("button")

            (0 until toggles.length).map { (toggles.item(it) as Element).getAttribute("aria-label") } shouldContainExactly
                    listOf("Docs", "Keys", "Analysis", "Fullscreen")

            val foot = strip.q(".${styles.workbenchStripFoot}")!!
            val keys = foot.q("[role=group][aria-label='View']")!!

            keys.q("button")!!.getAttribute("aria-label") shouldBe "Fullscreen"
            // The keys first, the mark under them
            (keys.nextElementSibling == foot.q(".${styles.workbenchMark}")) shouldBe true
            foot.q(".${styles.workbenchMark}")!!.textContent shouldBe "KLANGMOTOR"
        }
    }

    "a toggle opens its tool window flying in over the content; another switches it; the same one closes it" {
        withWorkbench { root ->
            root.toggle("Docs").click()

            eventually {
                val floating = root.q(".${styles.workbenchCentre} .${styles.workbenchFloating}")

                floating shouldNotBe null
                floating!!.textContent!!.contains("DOCS BODY") shouldBe true
                root.toggle("Docs").getAttribute("aria-pressed") shouldBe "true"
            }

            root.toggle("Keys").click()

            eventually {
                root.q(".${styles.workbenchFloating}")!!.textContent!!.contains("KEYS BODY") shouldBe true
                root.toggle("Docs").getAttribute("aria-pressed") shouldBe "false"
            }

            root.toggle("Keys").click()

            eventually {
                root.q(".${styles.workbenchFloating}") shouldBe null
            }
        }
    }

    "the pin docks the open window beside the content, out of the content's area; its close button closes it" {
        withWorkbench { root ->
            root.toggle("Docs").click()

            eventually { root.q(".${styles.workbenchFloating}") shouldNotBe null }

            root.q(".${styles.workbenchFloating} button[aria-pressed]")!!.click()

            eventually {
                root.q(".${styles.workbenchFloating}") shouldBe null

                val pinned = root.q(".${styles.workbenchPinned}")

                pinned shouldNotBe null
                pinned!!.textContent!!.contains("DOCS BODY") shouldBe true
                root.q(".${styles.workbenchCentre}")!!.contains(pinned) shouldBe false
            }

            root.q(".${styles.workbenchPinned} button[aria-label='Close Docs']")!!.click()

            eventually {
                root.q(".${styles.workbenchPinned}") shouldBe null
                root.toggle("Docs").getAttribute("aria-pressed") shouldBe "false"
            }
        }
    }

    "the bottom panel flies in over the content from its toggle; pinned it docks under it; the toggle hides it" {
        withWorkbench { root ->
            fun floating() = root.q(".${styles.workbenchBottomFloating}")
            fun pinned() = root.q(".${styles.workbenchBottomPinned}")

            floating() shouldBe null

            root.toggle("Analysis").click()

            eventually {
                val bottom = floating()!!

                bottom.q("section")!!.getAttribute("aria-label") shouldBe "Analysis"
                bottom.textContent!!.contains("BOTTOM BODY") shouldBe true
                root.q(".${styles.workbenchCentre}")!!.contains(bottom) shouldBe true
                (styles.toolWindowFloatingBottom.toString() in bottom.q("section")!!.className) shouldBe true
            }

            floating()!!.q("button[aria-label='Pin under the editor']")!!.click()

            eventually {
                floating() shouldBe null
                pinned()!!.textContent!!.contains("BOTTOM BODY") shouldBe true
            }

            root.toggle("Analysis").click()

            eventually {
                floating() shouldBe null
                pinned() shouldBe null
            }
        }
    }

    "the bottom panel's header, dragged, makes it higher or lower; a drag that starts on a header key does not" {
        withWorkbench { root ->
            fun panel() = root.q(".${styles.workbenchBottomFloating}")!!

            fun pointer(target: Element, type: String, y: Int) = target.dispatchEvent(
                PointerEvent(
                    type,
                    PointerEventInit(pointerId = 1, isPrimary = true, clientY = y, buttons = 1, bubbles = true, cancelable = true),
                )
            )

            // Room to grow: the workbench as high as a window
            (root as HTMLElement).style.setProperty("height", "800px")
            root.toggle("Analysis").click()
            eventually { panel().style.height shouldBe "260px" }

            val header = panel().q(".${styles.toolWindowHeader}")!!

            pointer(header, "pointerdown", y = 500)
            pointer(header, "pointermove", y = 400)
            pointer(header, "pointerup", y = 400)

            eventually { panel().style.height shouldBe "360px" }

            val pin = panel().q("button[aria-label='Pin under the editor']")!!

            pointer(pin, "pointerdown", y = 500)
            pointer(pin, "pointermove", y = 300)
            pointer(pin, "pointerup", y = 300)
            delay(50)

            panel().style.height shouldBe "360px"

            // Down past its least height: it stops there
            pointer(header, "pointerdown", y = 100)
            pointer(header, "pointermove", y = 700)
            pointer(header, "pointerup", y = 700)

            eventually { panel().style.height shouldBe "120px" }
        }
    }

    "a checkbox in the bottom panel's header is a checkbox, not a handle: pressed, it toggles and nothing resizes" {
        keyedBed(view = { ChangingHost() }) { root ->
            val r = root[0]

            workbenchRef { it.showBottom() }
            eventually { (r.q(".${styles.workbenchBottomFloating}") != null) shouldBe true }

            val panel = r.q(".${styles.workbenchBottomFloating}")!!
            val check = panel.q("[data-test=header-check]") as HTMLInputElement
            val before = panel.style.height

            fun pointer(type: String, y: Int) = check.dispatchEvent(
                PointerEvent(type, PointerEventInit(pointerId = 1, isPrimary = true, clientY = y, buttons = 1, bubbles = true)),
            )

            pointer("pointerdown", y = 500)
            pointer("pointermove", y = 300)
            pointer("pointerup", y = 300)
            check.click()

            eventually { check.checked shouldBe true }
            // A resize would show after the next draw
            delay(100)

            panel.style.height shouldBe before
        }
    }

    "Esc closes the bottom panel while it flies in; pinned, it stays" {
        withWorkbench { root ->
            fun esc() {
                (document.activeElement ?: document.body!!).dispatchEvent(
                    KeyboardEvent("keydown", KeyboardEventInit(key = "Escape", bubbles = true, cancelable = true))
                )
            }

            root.toggle("Analysis").click()
            eventually { root.q(".${styles.workbenchBottomFloating}") shouldNotBe null }

            esc()

            eventually { root.q(".${styles.workbenchBottomFloating}") shouldBe null }

            root.toggle("Analysis").click()
            eventually { root.q(".${styles.workbenchBottomFloating}") shouldNotBe null }
            root.q(".${styles.workbenchBottomFloating}")!!.q("button[aria-label='Pin under the editor']")!!.click()
            eventually { root.q(".${styles.workbenchBottomPinned}") shouldNotBe null }

            esc()
            delay(100)

            root.q(".${styles.workbenchBottomPinned}") shouldNotBe null
        }
    }

    "the bottom panel gives back a focus lost with it: closed by Esc from inside, and kept in it when it is pinned" {
        withWorkbench { root ->
            val target = root.q("[data-test=content-target]")!!

            // A click from a script moves no focus: it stays in the content as the panel opens
            target.focus()
            root.toggle("Analysis").click()
            eventually { root.q(".${styles.workbenchBottomFloating}") shouldNotBe null }

            // A click on the panel's pin takes the focus there; the key is redrawn, the panel keeps the focus
            val pin = root.q(".${styles.workbenchBottomFloating} button[aria-label='Pin under the editor']")!!

            pin.focus()
            pin.click()

            eventually {
                val pinned = root.q(".${styles.workbenchBottomPinned}")

                pinned shouldNotBe null
                pinned!!.contains(document.activeElement) shouldBe true
            }

            root.q(".${styles.workbenchBottomPinned} button[aria-pressed]")!!.click()
            eventually { root.q(".${styles.workbenchBottomFloating}") shouldNotBe null }

            root.q(".${styles.workbenchBottomFloating} button[aria-label='Close Analysis']")!!.click()

            eventually {
                root.q(".${styles.workbenchBottomFloating}") shouldBe null
                document.activeElement shouldBe target
            }
        }
    }

    "a header drag ends when a move comes without the button held; a right press starts none" {
        withWorkbench { root ->
            fun panel() = root.q(".${styles.workbenchBottomFloating}")!!

            fun pointer(target: Element, type: String, y: Int, button: Short = 0, buttons: Short = 1) = target.dispatchEvent(
                PointerEvent(
                    type,
                    PointerEventInit(
                        pointerId = 1,
                        isPrimary = true,
                        clientY = y,
                        button = button,
                        buttons = buttons,
                        bubbles = true,
                        cancelable = true,
                    ),
                )
            )

            (root as HTMLElement).style.setProperty("height", "800px")
            root.toggle("Analysis").click()
            eventually { panel().style.height shouldBe "260px" }

            val header = panel().q(".${styles.toolWindowHeader}")!!

            // The release went elsewhere: the next move, no button held, ends the drag; later moves change nothing
            pointer(header, "pointerdown", y = 500)
            pointer(header, "pointermove", y = 450)
            pointer(header, "pointermove", y = 300, buttons = 0)
            pointer(header, "pointermove", y = 200, buttons = 0)
            delay(50)

            panel().style.height shouldBe "310px"

            // A right press whose release a menu kept: the next left-button gesture across the panel moves nothing
            pointer(header, "pointerdown", y = 500, button = 2, buttons = 2)
            pointer(header, "pointermove", y = 400, buttons = 1)
            pointer(header, "pointerup", y = 400, buttons = 0)
            delay(50)

            panel().style.height shouldBe "310px"
        }
    }

    "the bottom panel flying in goes with its page; pinned it comes back with the next page that has one" {
        ChangingHost.reset()

        try {
            keyedBed(view = { ChangingHost() }) { root ->
                val r = root.single()

                r.toggle("Help").click()
                eventually { r.q(".${styles.workbenchBottomFloating}") shouldNotBe null }

                ChangingHost.current!!.withBottom = false
                eventually { r.q(".${styles.workbenchBottomFloating}") shouldBe null }

                ChangingHost.current!!.withBottom = true
                delay(50)

                r.q(".${styles.workbenchBottomFloating}") shouldBe null

                r.toggle("Help").click()
                eventually { r.q(".${styles.workbenchBottomFloating}") shouldNotBe null }
                r.q(".${styles.workbenchBottomFloating} button[aria-label='Pin under the editor']")!!.click()
                eventually { r.q(".${styles.workbenchBottomPinned}") shouldNotBe null }

                ChangingHost.current!!.withBottom = false
                eventually { r.q(".${styles.workbenchBottomPinned}") shouldBe null }

                ChangingHost.current!!.withBottom = true
                eventually { r.q(".${styles.workbenchBottomPinned}") shouldNotBe null }
            }
        } finally {
            ChangingHost.reset()
        }
    }

    "the Motor's menu opens the drawer above the Motor and puts the focus in it; Esc and its close close it, the focus goes back" {
        withWorkbench { root ->
            fun menu() = root.q("[data-test=motor-menu]")!!
            fun drawer() = root.q(".${styles.workbenchColumnArea} > .${styles.workbenchDrawer}")

            drawer() shouldBe null
            menu().getAttribute("data-open") shouldBe "false"

            menu().focus()
            menu().click()

            eventually {
                drawer()?.textContent?.contains("DRAWER") shouldBe true
                menu().getAttribute("data-open") shouldBe "true"
                drawer()!!.contains(document.activeElement) shouldBe true
            }

            // Esc where the focus is: in the drawer
            document.activeElement!!.dispatchEvent(
                KeyboardEvent("keydown", KeyboardEventInit(key = "Escape", bubbles = true, cancelable = true))
            )

            eventually {
                drawer() shouldBe null
                menu().getAttribute("data-open") shouldBe "false"
                (document.activeElement == menu()) shouldBe true
            }

            menu().click()

            eventually { drawer() shouldNotBe null }

            root.q("[data-test=drawer-close]")!!.click()

            eventually {
                drawer() shouldBe null
                (document.activeElement == menu()) shouldBe true
            }
        }
    }

    "each tool window keeps its own pin" {
        withWorkbench { root ->
            root.toggle("Docs").click()

            eventually { root.q(".${styles.workbenchFloating}") shouldNotBe null }

            root.q(".${styles.workbenchFloating} button[aria-pressed]")!!.click()

            eventually { root.q(".${styles.workbenchPinned}") shouldNotBe null }

            root.toggle("Keys").click()

            // Keys flies in: Docs' pin is Docs' own
            eventually {
                root.q(".${styles.workbenchFloating}")?.textContent?.contains("KEYS BODY") shouldBe true
                root.q(".${styles.workbenchPinned}") shouldBe null
            }

            root.toggle("Docs").click()

            eventually {
                root.q(".${styles.workbenchPinned}")!!.textContent!!.contains("DOCS BODY") shouldBe true
            }
        }
    }

    "without a drawer the Motor gets no menu" {
        keyedBed(
            view = {
                KlangWorkbench(
                    column = {},
                    motor = { menu -> span { +"menu: ${menu == null}" } },
                    content = {},
                )
            },
        ) { root ->
            root.single().q(".${styles.workbenchMotor}")!!.textContent shouldBe "menu: true"
        }
    }

    "a tool window shown from outside opens and leaves the focus where it is; Esc in it gives the focus back there" {
        keyedBed(view = { ChangingHost() }) { root ->
            val r = root[0]
            fun floating() = r.q(".${styles.workbenchFloating}")

            val field = document.createElement("input") as HTMLElement

            r.appendChild(field)
            field.focus()

            workbenchRef { it.showTool("docs") }

            eventually { floating()?.textContent?.contains("DOCS") shouldBe true }

            // The user goes on typing where they were
            document.activeElement shouldBe field

            // A click into the window, then Esc: back to the field
            (floating() as HTMLElement).focus()
            document.activeElement!!.dispatchEvent(
                KeyboardEvent("keydown", KeyboardEventInit(key = "Escape", bubbles = true, cancelable = true))
            )

            eventually {
                floating() shouldBe null
                document.activeElement shouldBe field
            }
        }
    }

    "the bottom panel shown from outside opens, the focus where it is; its header holds what it brings, at its end" {
        keyedBed(view = { ChangingHost() }) { root ->
            val r = root[0]
            val field = document.createElement("input") as HTMLElement

            r.appendChild(field)
            field.focus()

            r.q(".${styles.workbenchBottomFloating}") shouldBe null

            workbenchRef { it.showBottom() }

            eventually {
                val bottom = r.q(".${styles.workbenchBottomFloating}")!!

                bottom.textContent!!.contains("HELP BODY") shouldBe true
                bottom.q(".${styles.toolWindowHeaderEnd}")!!.textContent shouldBe "HOVER"
            }

            document.activeElement shouldBe field
        }
    }

    "a tool window that leaves the list closes, and does not open again by itself when it comes back" {
        keyedBed(view = { ChangingHost() }) { root ->
            root.single().toggle("Docs").click()

            eventually { root.single().q(".${styles.workbenchFloating}") shouldNotBe null }

            ChangingHost.current!!.withDocs = false

            eventually { root.single().q(".${styles.workbenchFloating}") shouldBe null }

            ChangingHost.current!!.withDocs = true

            eventually {
                root.single().q("[role=group] button[aria-label='Docs']") shouldNotBe null
                root.single().q(".${styles.workbenchFloating}") shouldBe null
            }
        }
    }

    "a kept layout comes back: the bottom panel pinned at its height, the pinned tool window open whenever the page brings it" {
        ChangingHost.reset()
        ChangingHost.startWithDocs = false
        ChangingHost.startLayout = KlangWorkbench.Layout(
            pinned = setOf("docs"),
            open = "docs",
            bottomOpen = true,
            bottomPinned = true,
            bottomHeight = 300.0,
        )

        try {
            keyedBed(view = { ChangingHost() }) { root ->
                val r = root.single()

                eventually {
                    r.q(".${styles.workbenchBottomPinned}")?.style?.height shouldBe "300px"
                    r.q(".${styles.workbenchPinned}") shouldBe null
                }

                ChangingHost.current!!.withDocs = true

                eventually {
                    r.q(".${styles.workbenchPinned}")?.textContent?.contains("DOCS") shouldBe true
                    r.q(".${styles.workbenchFloating}") shouldBe null
                }

                // A page left and come back to: the pinned window goes with it and comes back with it, still kept open
                ChangingHost.current!!.withDocs = false

                eventually { r.q(".${styles.workbenchPinned}") shouldBe null }

                delay(50)

                ChangingHost.reports.filter { it.open != "docs" } shouldBe emptyList()

                ChangingHost.current!!.withDocs = true

                eventually { r.q(".${styles.workbenchPinned}")?.textContent?.contains("DOCS") shouldBe true }
            }
        } finally {
            ChangingHost.reset()
        }
    }

    "a kept height taller than the room keeps the content and the panel's header in it; a drag starts from what shows" {
        ChangingHost.reset()
        ChangingHost.startLayout = KlangWorkbench.Layout(bottomOpen = true, bottomPinned = true, bottomHeight = 5000.0)

        try {
            keyedBed(view = { ChangingHost() }) { root ->
                val r = root.single() as HTMLElement

                fun pointer(target: Element, type: String, y: Int) = target.dispatchEvent(
                    PointerEvent(
                        type,
                        PointerEventInit(pointerId = 1, isPrimary = true, clientY = y, buttons = 1, bubbles = true, cancelable = true),
                    )
                )

                fun headerInside(placement: String) {
                    val top = r.getBoundingClientRect().top
                    val header = r.q(".$placement .${styles.toolWindowHeader}")!!.getBoundingClientRect()

                    (header.top >= top) shouldBe true
                    (header.bottom - top <= 800.0) shouldBe true
                }

                r.style.setProperty("height", "800px")

                eventually {
                    headerInside(styles.workbenchBottomPinned.toString())
                    (r.q(".${styles.workbenchContent}")!!.getBoundingClientRect().height >= 100.0) shouldBe true
                }

                // Flying in, too: its header stays in the room
                r.q(".${styles.workbenchBottomPinned} button[aria-pressed]")!!.click()
                eventually { headerInside(styles.workbenchBottomFloating.toString()) }
                r.q(".${styles.workbenchBottomFloating} button[aria-label='Pin under the editor']")!!.click()
                eventually { r.q(".${styles.workbenchBottomPinned}") shouldNotBe null }

                // Dragged down by 100 from where it shows: 100 lower at once, not first the hidden excess
                val panel = r.q(".${styles.workbenchBottomPinned}")!!
                val shown = panel.getBoundingClientRect().height
                val header = panel.q(".${styles.toolWindowHeader}")!!

                pointer(header, "pointerdown", y = 300)
                pointer(header, "pointermove", y = 400)
                pointer(header, "pointerup", y = 400)

                eventually { r.q(".${styles.workbenchBottomPinned}")!!.getBoundingClientRect().height shouldBe shown - 100.0 }
            }
        } finally {
            ChangingHost.reset()
        }
    }

    "the workbench reports its layout as it changes; a window that only flies in is not kept open, a drag reports once" {
        ChangingHost.reset()

        try {
            keyedBed(view = { ChangingHost() }) { root ->
                val r = root.single()

                fun pointer(target: Element, type: String, y: Int) = target.dispatchEvent(
                    PointerEvent(
                        type,
                        PointerEventInit(pointerId = 1, isPrimary = true, clientY = y, buttons = 1, bubbles = true, cancelable = true),
                    )
                )

                (r as HTMLElement).style.setProperty("height", "800px")

                r.toggle("Docs").click()

                eventually { r.q(".${styles.workbenchFloating}") shouldNotBe null }
                delay(50)

                ChangingHost.reports shouldBe emptyList()

                r.q(".${styles.workbenchFloating} button[aria-label='Pin beside the editor']")!!.click()

                eventually { ChangingHost.reports.lastOrNull() shouldBe KlangWorkbench.Layout(pinned = setOf("docs"), open = "docs") }

                r.toggle("Help").click()

                eventually { r.q(".${styles.workbenchBottomFloating}") shouldNotBe null }

                r.q(".${styles.workbenchBottomFloating} button[aria-label='Pin under the editor']")!!.click()

                val pinnedBoth = KlangWorkbench.Layout(pinned = setOf("docs"), open = "docs", bottomOpen = true, bottomPinned = true)

                eventually { ChangingHost.reports.lastOrNull() shouldBe pinnedBoth }

                val before = ChangingHost.reports.size
                val header = r.q(".${styles.workbenchBottomPinned} .${styles.toolWindowHeader}")!!

                pointer(header, "pointerdown", y = 500)
                pointer(header, "pointermove", y = 450)
                pointer(header, "pointermove", y = 400)
                delay(50)

                ChangingHost.reports.size shouldBe before

                pointer(header, "pointerup", y = 400)

                eventually {
                    ChangingHost.reports.drop(before) shouldContainExactly listOf(pinnedBoth.copy(bottomHeight = 360.0))
                }

                r.toggle("Docs").click()

                eventually {
                    ChangingHost.reports.lastOrNull() shouldBe pinnedBoth.copy(open = null, bottomHeight = 360.0)
                }
            }
        } finally {
            ChangingHost.reset()
        }
    }

    "a pick that puts the focus elsewhere keeps it there; the focus does not jump back to the menu button" {
        withWorkbench { root ->
            val menu = root.q("[data-test=motor-menu]")!!

            menu.focus()
            menu.click()

            eventually { root.q("[data-test=drawer-pick]") shouldNotBe null }

            root.q("[data-test=drawer-pick]")!!.click()

            eventually {
                root.q(".${styles.workbenchDrawer}") shouldBe null
            }

            // Past the frame the drawer's close returns a lost focus in
            nextFrame()
            nextFrame()

            document.activeElement?.getAttribute("data-test") shouldBe "content-target"
        }
    }

    "a drawer that leaves the props closes, and does not open again by itself when it comes back" {
        keyedBed(view = { ChangingHost() }) { root ->
            root.single().q("[data-test=motor-menu]")!!.click()

            eventually { root.single().q(".${styles.workbenchDrawer}") shouldNotBe null }

            ChangingHost.current!!.withDrawer = false

            eventually { root.single().q("[data-test=motor-menu]")?.getAttribute("data-menu") shouldBe "false" }

            ChangingHost.current!!.withDrawer = true

            // Drawn again with the drawer back in the props: the menu is there, the drawer stays closed
            eventually { root.single().q("[data-test=motor-menu]")?.getAttribute("data-menu") shouldBe "true" }

            root.single().q(".${styles.workbenchDrawer}") shouldBe null
        }
    }

    "the drawer's field takes the focus, else its first control (a link counts, a disabled button not), else the drawer" {
        suspend fun focusedOnOpen(drawer: FlowContent.(close: () -> Unit) -> Unit): Element? {
            var focused: Element? = null

            keyedBed(
                view = {
                    KlangWorkbench(
                        column = {},
                        motor = { menu ->
                            button {
                                attributes["data-test"] = "motor-menu"
                                onClick { menu?.toggle?.invoke() }
                            }
                        },
                        content = {},
                        drawer = drawer,
                    )
                },
            ) { root ->
                root.single().q("[data-test=motor-menu]")!!.click()

                eventually {
                    root.single().q(".${styles.workbenchDrawer}")!!.contains(document.activeElement) shouldBe true
                }

                focused = document.activeElement
            }

            return focused
        }

        val link = focusedOnOpen {
            button {
                disabled = true
                +"OFF"
            }
            a(href = "#") { +"LINK" }
        }

        link?.textContent shouldBe "LINK"

        val bare = focusedOnOpen { span { +"NOTHING TO PRESS" } }

        bare?.className shouldBe styles.workbenchDrawer.toString()

        // A field comes first, before the controls above it: the drawer's search, so Alt+M lands where one types
        val field = focusedOnOpen {
            button { +"FIRST" }
            input { }
        }

        field?.tagName shouldBe "INPUT"
    }

    "a tool window opened from the strip takes the focus; Esc closes one flying in, the focus goes back to its toggle; a pinned one stays" {
        withWorkbench { root ->
            fun esc() {
                document.activeElement!!.dispatchEvent(
                    KeyboardEvent("keydown", KeyboardEventInit(key = "Escape", bubbles = true, cancelable = true))
                )
            }

            root.toggle("Docs").focus()
            root.toggle("Docs").click()

            eventually {
                root.q(".${styles.workbenchFloating}")?.contains(document.activeElement) shouldBe true
            }

            esc()

            eventually {
                root.q(".${styles.workbenchFloating}") shouldBe null
                document.activeElement?.getAttribute("aria-label") shouldBe "Docs"
            }

            root.toggle("Docs").click()

            eventually { root.q(".${styles.workbenchFloating}") shouldNotBe null }

            root.q(".${styles.workbenchFloating} button[aria-pressed]")!!.click()

            // Pinned, the window moves beside the content and the focus moves with it
            eventually {
                root.q(".${styles.workbenchPinned}")?.contains(document.activeElement) shouldBe true
            }

            // Its close button gives the focus back to the toggle, as Esc does
            root.q(".${styles.workbenchPinned} button[aria-label='Close Docs']")!!.click()

            eventually {
                root.q(".${styles.workbenchPinned}") shouldBe null
                document.activeElement?.getAttribute("aria-label") shouldBe "Docs"
            }

            // Opened from the strip again it comes pinned, and takes the focus
            root.toggle("Docs").click()

            eventually {
                root.q(".${styles.workbenchPinned}")?.contains(document.activeElement) shouldBe true
            }

            esc()

            // Past the frames a close would take: the pinned window is part of the bench
            nextFrame()
            nextFrame()

            root.q(".${styles.workbenchPinned}") shouldNotBe null
        }
    }

    "a page wider than the content area stays inside it; the workbench never grows past its box, also with a window pinned" {
        keyedBed(
            view = {
                div {
                    css {
                        width = 1000.px
                        height = 600.px
                    }

                    KlangWorkbench(
                        column = {},
                        motor = {},
                        content = {
                            div {
                                attributes["data-test"] = "wide-page"
                                css { width = 1500.px }
                                +"WIDE"
                            }
                        },
                        tools = listOf(KlangWorkbench.Tool(id = "docs", title = "Docs", icon = { book }) { +"DOCS" }),
                    )
                }
            },
        ) { root ->
            val bench = root.single().q(".${styles.workbench}")!!

            bench.toggle("Docs").click()

            eventually { bench.q(".${styles.workbenchFloating}") shouldNotBe null }

            bench.q(".${styles.workbenchFloating} button[aria-pressed]")!!.click()

            eventually { bench.q(".${styles.workbenchPinned}") shouldNotBe null }

            nextFrame()
            nextFrame()

            bench.scrollWidth shouldBe bench.clientWidth
            bench.scrollLeft shouldBe 0.0
        }
    }

    "a tool window flying in shows Close: Esc, and Esc from outside it closes it through the key manager; pinned, no pill" {
        withWorkbench { root ->
            root.toggle("Docs").click()

            eventually {
                root.q(".${styles.workbenchFloating} .${styles.keyPill}")?.textContent?.startsWith("Close:") shouldBe true
            }

            // The focus outside the window: the key manager answers all the same
            (document.activeElement as? HTMLElement)?.blur()
            document.body!!.dispatchEvent(
                KeyboardEvent("keydown", KeyboardEventInit(key = "Escape", bubbles = true, cancelable = true))
            )

            eventually { root.q(".${styles.workbenchFloating}") shouldBe null }

            root.toggle("Docs").click()

            eventually { root.q(".${styles.workbenchFloating}") shouldNotBe null }

            root.q(".${styles.workbenchFloating} button[aria-pressed]")!!.click()

            eventually {
                root.q(".${styles.workbenchPinned}") shouldNotBe null
                root.q(".${styles.workbenchPinned} .${styles.keyPill}") shouldBe null
            }
        }
    }

    /** A workbench with real panels: a drawer that closes on Esc, and two tool windows, the first with a field. */
    suspend fun withPanels(block: suspend (root: Element) -> Unit) {
        keyedBed(
            view = {
                KlangWorkbench(
                    column = {},
                    motor = { menu ->
                        button {
                            attributes["data-test"] = "motor-menu"
                            onClick { menu?.toggle?.invoke() }
                        }
                    },
                    content = {},
                    tools = listOf(
                        KlangWorkbench.Tool(id = "docs", title = "Docs", icon = { book }) {
                            input { attributes["data-test"] = "docs-field" }
                            // A control that keeps Esc for itself (prevents it) without stopping it
                            button {
                                attributes["data-test"] = "docs-keeper"
                                onKeyDown { e ->
                                    if (e.key == "Escape") {
                                        e.preventDefault()
                                    }
                                }
                            }
                        },
                        KlangWorkbench.Tool(id = "keys", title = "Keys", icon = { keyboard }) { span { +"KEYS" } },
                    ),
                    drawer = { close ->
                        KlangDrawer(
                            title = "Where to",
                            entries = listOf(KlangDrawer.Entry(id = "songs", label = "Songs")),
                            current = null,
                            onPick = {},
                            onClose = close,
                            closeOnEsc = true,
                        )
                    },
                )
            },
        ) { root ->
            block(root.single())
        }
    }

    fun escOn(target: EventTarget) {
        target.dispatchEvent(KeyboardEvent("keydown", KeyboardEventInit(key = "Escape", bubbles = true, cancelable = true)))
    }

    "one Esc, one panel: from outside, the newest open panel closes first, then the one before it" {
        withPanels { root ->
            root.q("[data-test=motor-menu]")!!.click()

            eventually { root.q(".${styles.workbenchDrawer}") shouldNotBe null }

            root.toggle("Keys").click()

            eventually { root.q(".${styles.workbenchFloating}") shouldNotBe null }

            (document.activeElement as? HTMLElement)?.blur()
            escOn(document.body!!)

            eventually {
                root.q(".${styles.workbenchFloating}") shouldBe null
                root.q(".${styles.workbenchDrawer}") shouldNotBe null
            }

            escOn(document.body!!)

            eventually { root.q(".${styles.workbenchDrawer}") shouldBe null }
        }
    }

    "Esc from inside the drawer still closes the newest panel first: a newer tool window, then the drawer" {
        withPanels { root ->
            root.q("[data-test=motor-menu]")!!.click()

            eventually { root.q(".${styles.workbenchDrawer}") shouldNotBe null }

            root.toggle("Keys").click()

            eventually { root.q(".${styles.workbenchFloating}") shouldNotBe null }

            val tile = root.q(".${styles.workbenchDrawer} .${styles.drawerGrid} button")!!

            tile.focus()
            escOn(tile)

            eventually {
                root.q(".${styles.workbenchFloating}") shouldBe null
                root.q(".${styles.workbenchDrawer}") shouldNotBe null
            }

            escOn(document.activeElement ?: document.body!!)

            eventually { root.q(".${styles.workbenchDrawer}") shouldBe null }
        }
    }

    "Esc in a field inside a tool window flying in closes it" {
        withPanels { root ->
            root.toggle("Docs").click()

            eventually { root.q("[data-test=docs-field]") shouldNotBe null }

            val field = root.q("[data-test=docs-field]")!!

            field.focus()
            escOn(field)

            eventually { root.q(".${styles.workbenchFloating}") shouldBe null }
        }
    }

    "switching to another tool window makes it the newest panel: Esc closes it before the drawer opened earlier" {
        withPanels { root ->
            root.toggle("Docs").click()

            eventually { root.q(".${styles.workbenchFloating}") shouldNotBe null }

            root.q("[data-test=motor-menu]")!!.click()

            eventually { root.q(".${styles.workbenchDrawer}") shouldNotBe null }

            root.toggle("Keys").click()

            eventually { root.q(".${styles.workbenchFloating}")?.textContent?.contains("KEYS") shouldBe true }

            (document.activeElement as? HTMLElement)?.blur()
            escOn(document.body!!)

            eventually {
                root.q(".${styles.workbenchFloating}") shouldBe null
                root.q(".${styles.workbenchDrawer}") shouldNotBe null
            }
        }
    }

    "an Esc a control inside a tool window already took leaves the window open" {
        withPanels { root ->
            root.toggle("Docs").click()

            eventually { root.q("[data-test=docs-keeper]") shouldNotBe null }

            val keeper = root.q("[data-test=docs-keeper]")!!

            keeper.focus()
            escOn(keeper)

            // Past the frames a close would take
            nextFrame()
            nextFrame()

            root.q(".${styles.workbenchFloating}") shouldNotBe null
        }
    }
})
