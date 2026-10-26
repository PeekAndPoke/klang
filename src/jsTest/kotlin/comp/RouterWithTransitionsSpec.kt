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
import kotlinx.browser.window
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
            mount(Static("/a2")) { div(classes = "page-a2") { css { height = 1000.px }; +"a2" } }
        }

        layout(layoutB) {
            mount(Static("/b")) { div(classes = "page-b") { +"b" } }
        }
    }.build()
}

/** The current route's part of the router, without the pages fading out. */
private fun current(root: Element, selector: String): HTMLElement? =
    root.querySelector(".router > .current $selector") as HTMLElement?

/** `RouterWithTransitions` (KLANG-00341): layouts that stay across pages, the scrollers, the page's box. */
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

    "the page keeps the layout's full width in a flex row" {
        withRouter { root, routes ->
            routes.go(root, "/b", ".page-b")

            current(root, ".router-page")!!.getBoundingClientRect().width shouldBe 300.0
        }
    }
})
