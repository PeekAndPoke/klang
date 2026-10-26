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
import kotlinx.css.height
import kotlinx.css.px
import kotlinx.css.width
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.input
import kotlinx.html.span
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.EventTarget
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.KeyboardEventInit

/** The workbench the host draws, so a spec can show a tool window from outside, as the song page does on Alt+Q. */
private val workbenchRef = ComponentRef.Tracker<KlangWorkbench>()

/** A workbench whose tool windows and drawer its parent changes, as a page's may. */
private class ChangingHost(ctx: NoProps) : PureComponent(ctx) {
    companion object {
        var current: ChangingHost? = null
    }

    var withDocs: Boolean by value(true)

    var withDrawer: Boolean by value(true)

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
            drawer = drawer,
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

    "the bottom panel shows and hides from its toggle, under the content" {
        withWorkbench { root ->
            root.q(".${styles.workbenchBottom}") shouldBe null

            root.toggle("Analysis").click()

            eventually {
                val bottom = root.q(".${styles.workbenchBottom}")

                bottom shouldNotBe null
                bottom!!.getAttribute("aria-label") shouldBe "Analysis"
                bottom.textContent!!.contains("BOTTOM BODY") shouldBe true
                root.q(".${styles.workbenchCentre}")!!.contains(bottom) shouldBe true
            }

            root.toggle("Analysis").click()

            eventually { root.q(".${styles.workbenchBottom}") shouldBe null }
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
