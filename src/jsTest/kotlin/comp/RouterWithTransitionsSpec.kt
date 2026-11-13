/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.kraft.routing.LayoutFn
import io.peekandpoke.kraft.routing.RootRouterBuilder
import io.peekandpoke.kraft.routing.Router
import io.peekandpoke.kraft.routing.Static
import io.peekandpoke.kraft.testing.TestBed
import io.peekandpoke.kraft.testing.eventually
import io.peekandpoke.ultra.html.css
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.withTimeout
import kotlinx.css.Display
import kotlinx.css.Overflow
import kotlinx.css.display
import kotlinx.css.height
import kotlinx.css.overflowY
import kotlinx.css.px
import kotlinx.css.width
import kotlinx.html.div
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import kotlin.js.Promise

/**
 * A route table of the spec's own (KLANG-00343): two pages under layout A, whose page sits in a scroller, and one page
 * under layout B, a flex row. Hash routing, so the spec never moves the test page's own path.
 */
private class Routes {
    val layoutA: LayoutFn = { content ->
        div(classes = "layout-a") {
            div(classes = "scroller") {
                css {
                    height = 100.px
                    overflowY = Overflow.auto
                }

                content()
            }
        }
    }

    val layoutB: LayoutFn = { content ->
        div(classes = "layout-b") {
            css {
                display = Display.flex
                width = 300.px
            }

            content()
        }
    }

    val router: Router = RootRouterBuilder().apply {
        useHashStrategy()

        layout(layoutA) {
            mount(Static("/a1")) { div(classes = "page-a1") { css { height = 1000.px }; +"a1" } }
            mount(Static("/a2")) { match ->
                div(classes = "page-a2") {
                    attributes["data-x"] = match.queryParams["x"] ?: ""
                    css { height = 1000.px }
                    +"a2"
                }
            }
        }

        layout(layoutB) {
            mount(Static("/b")) { div(classes = "page-b") { +"b" } }
        }
    }.build()
}

/** What is drawn for the current route. */
private fun current(root: Element, selector: String): HTMLElement? =
    root.querySelector(".router > .current $selector") as HTMLElement?

/**
 * `RouterWithTransitions` (KLANG-00341, KLANG-00442): layouts that stay across pages, the scrollers, the page's box,
 * the page left gone at once, and a page change handed to the browser's view transition where it has one.
 */
class RouterWithTransitionsSpec : StringSpec({

    suspend fun withRouter(test: suspend (root: Element, routes: Routes) -> Unit) {
        val routes = Routes()

        try {
            TestBed.preact(view = { RouterWithTransitions(router = routes.router) }) { root ->
                test(root[0], routes)
            }
        } finally {
            // The router keeps listening to the hash; the next spec's router takes over from here
            routes.router.disable()
            window.location.hash = ""
        }
    }

    /**
     * The browser's `startViewTransition` replaced by one that keeps the callbacks handed to it, for the spec to call
     * as the browser would: each gives the promise that says when the next page is drawn.
     */
    suspend fun withHeldTransitions(test: suspend (held: MutableList<() -> Promise<Unit>>) -> Unit) {
        val held = mutableListOf<() -> Promise<Unit>>()

        document.asDynamic().startViewTransition = { update: () -> Promise<Unit> ->
            held.add(update)
            js("({ ready: Promise.resolve() })")
        }

        try {
            test(held)
        } finally {
            js("delete document.startViewTransition")
        }
    }

    suspend fun Routes.go(root: Element, uri: String, page: String) {
        router.navToUri(uri)

        eventually { current(root, page) shouldNotBe null }
    }

    "a page change under the same layouts keeps the layout; a change to other layouts draws them anew" {
        withRouter { root, routes ->
            routes.go(root, "/a1", ".page-a1")

            val layoutA = current(root, ".layout-a")

            layoutA shouldNotBe null

            routes.go(root, "/a2", ".page-a2")

            // The same element: the layout's components, and what they hold, live on
            (current(root, ".layout-a") === layoutA) shouldBe true

            routes.go(root, "/b", ".page-b")

            current(root, ".layout-a") shouldBe null

            routes.go(root, "/a1", ".page-a1")

            // Back under layout A: drawn anew, not the element of before
            (current(root, ".layout-a") === layoutA) shouldBe false
        }
    }

    "the scrollers around the page go up on a path change, not on a query change" {
        withRouter { root, routes ->
            routes.go(root, "/a1", ".page-a1")

            val scroller = current(root, ".scroller")!!

            scroller.scrollTop = 50.0
            scroller.scrollTop shouldBe 50.0

            routes.go(root, "/a2", ".page-a2")

            scroller.scrollTop shouldBe 0.0

            scroller.scrollTop = 50.0
            routes.router.navToUri("/a2?x=1")

            // The scrollers go up as the route arrives, so once it is there they would have
            eventually { routes.router.current().uri shouldBe "/a2?x=1" }

            scroller.scrollTop shouldBe 50.0
        }
    }

    "the page left is gone at once: it is never drawn beside the next one" {
        withRouter { root, routes ->
            routes.go(root, "/a1", ".page-a1")
            routes.go(root, "/a2", ".page-a2")

            root.querySelectorAll(".page-a1").length shouldBe 0
            root.querySelectorAll(".router-page").length shouldBe 1

            routes.go(root, "/b", ".page-b")

            root.querySelectorAll(".page-a2").length shouldBe 0
            root.querySelectorAll(".layout-a").length shouldBe 0
        }
    }

    "a new path is handed to the browser's view transition; a new query is not" {
        val original = document.asDynamic().startViewTransition
        var handed = 0

        // The browser's own, counted
        document.asDynamic().startViewTransition = { update: dynamic ->
            handed++
            original.call(document, update)
        }

        try {
            withRouter { root, routes ->
                routes.go(root, "/a1", ".page-a1")

                val before = handed

                routes.go(root, "/a2", ".page-a2")

                handed shouldBe before + 1

                routes.router.navToUri("/a2?x=1")
                eventually { routes.router.current().uri shouldBe "/a2?x=1" }

                handed shouldBe before + 1

                routes.go(root, "/b", ".page-b")

                handed shouldBe before + 2
            }
        } finally {
            js("delete document.startViewTransition")
        }
    }

    "the page left stands until the browser calls back; then the latest route is drawn" {
        withHeldTransitions { held ->
            withRouter { root, routes ->
                // The first page, called back for as the browser would
                routes.router.navToUri("/a1")
                eventually { held.size shouldBe 1 }
                held.removeAt(0)().await()
                current(root, ".page-a1") shouldNotBe null

                routes.router.navToUri("/a2")
                eventually { held.size shouldBe 1 }
                // A query that follows before the browser called back
                routes.router.navToUri("/a2?x=1")
                eventually { routes.router.current().uri shouldBe "/a2?x=1" }

                // The browser has not called back: its picture of the page left is still to be taken
                current(root, ".page-a1") shouldNotBe null
                current(root, ".page-a2") shouldBe null

                withTimeout(1000) { held.removeAt(0)().await() }

                // The next page is there when the browser hears that its callback is done
                current(root, ".page-a1") shouldBe null
                current(root, ".page-a2")!!.getAttribute("data-x") shouldBe "1"
            }
        }
    }

    "a page change that follows before the browser called back: only the last callback draws, the latest page" {
        withHeldTransitions { held ->
            withRouter { root, routes ->
                routes.router.navToUri("/a1")
                eventually { held.size shouldBe 1 }
                held.removeAt(0)().await()

                routes.router.navToUri("/a2")
                eventually { held.size shouldBe 1 }
                routes.router.navToUri("/b")
                eventually { held.size shouldBe 2 }

                // The browser calls the skipped one first: the page left stands for the next one's picture
                withTimeout(1000) { held.removeAt(0)().await() }

                current(root, ".page-a1") shouldNotBe null
                current(root, ".page-b") shouldBe null

                withTimeout(1000) { held.removeAt(0)().await() }

                current(root, ".page-a1") shouldBe null
                current(root, ".page-b") shouldNotBe null
            }
        }
    }

    "a browser without view transitions changes the page all the same, the scrollers up" {
        document.asDynamic().startViewTransition = undefined

        try {
            withRouter { root, routes ->
                routes.go(root, "/a1", ".page-a1")

                val scroller = current(root, ".scroller")!!

                scroller.scrollTop = 50.0

                routes.go(root, "/a2", ".page-a2")

                root.querySelectorAll(".page-a1").length shouldBe 0
                scroller.scrollTop shouldBe 0.0
            }
        } finally {
            js("delete document.startViewTransition")
        }
    }

    "the page keeps the layout's full width in a flex row, and its stacking to itself" {
        withRouter { root, routes ->
            routes.go(root, "/b", ".page-b")

            current(root, ".router-page")!!.getBoundingClientRect().width shouldBe 300.0
            // What the page stacks stays within it, never over the layout's tool windows
            window.getComputedStyle(current(root, ".router-page")!!).getPropertyValue("isolation") shouldBe "isolate"
        }
    }
})
