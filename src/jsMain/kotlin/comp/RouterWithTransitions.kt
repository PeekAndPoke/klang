/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.routing.ActiveRoute
import io.peekandpoke.kraft.routing.MountedLayout
import io.peekandpoke.kraft.routing.Router
import io.peekandpoke.kraft.routing.Router.Companion.router
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.css.Position
import kotlinx.css.height
import kotlinx.css.pct
import kotlinx.css.position
import kotlinx.css.width
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div
import kotlin.js.Promise

@Suppress("FunctionName")
fun Tag.RouterWithTransitions(
    router: Router? = null,
) = comp(RouterWithTransitions.Props(router)) {
    RouterWithTransitions(it)
}

/**
 * The router's pages inside their layouts, with the change of a page faded by the browser (KLANG-00442).
 *
 * The page left goes at once: it is never kept mounted beside the next one. Where the browser has view transitions
 * (`document.startViewTransition`), it takes a picture of the window, this component draws the next page, and the
 * browser fades the picture into the window as it is then, off the main thread and only once the next page is drawn:
 * Kraft redraws in a microtask (Preact's `setState`), which runs before the browser hears that its callback is done.
 * Where it has none, or the user asked for reduced motion, the page changes with no fade.
 *
 * The browser calls back a frame after it was asked, so for that frame the page left still stands while the router
 * says the next route: a page takes its route from the view it is mounted with (`mount(route) { Page(it) }`), which
 * changes with the page, and never from `router.current`, or it would draw the next route's content before it goes.
 *
 * The layouts stay mounted while their pages change (KLANG-00341), and the scrollers around the page go up when the
 * path changes.
 */
class RouterWithTransitions internal constructor(ctx: Ctx<Props>) : Component<RouterWithTransitions.Props>(ctx) {

    companion object {
        private fun String.withoutQuery() = split("?").firstOrNull() ?: ""
    }

    data class Props(
        val router: Router?,
    )

    ////  STATE  ///////////////////////////////////////////////////////////////////////////////////////////////////////

    private val activeRouter get() = props.router ?: router

    /** The route the router gave last; [shown] follows it, at once or inside the browser's transition. */
    private var latest: ActiveRoute = activeRouter.current()

    /** The route that is drawn. */
    private var shown: ActiveRoute by value(latest)

    /** The page changes handed to the browser that it has not called back for yet. */
    private var handedOver = 0

    init {
        lifecycle {
            onMount {
                activeRouter.current { next -> follow(next) }
            }
        }
    }

    ////  IMPL  ////////////////////////////////////////////////////////////////////////////////////////////////////////

    /** Takes the router's [next] route: a new path as a page change, anything else at once. */
    private fun follow(next: ActiveRoute) {
        val previous = latest

        latest = next

        if (previous.uri.withoutQuery() == next.uri.withoutQuery()) {
            // While the browser has a page change in hand, its callback draws the latest route: drawn here, the next
            // page would stand before the picture of the page left is taken
            if (handedOver == 0) {
                shown = next
            }

            return
        }

        val reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches

        if (reducedMotion || document.asDynamic().startViewTransition == undefined) {
            show(next)

            return
        }

        handedOver++

        // The browser takes its picture, then calls back: the next page is drawn inside the callback
        val transition = document.asDynamic().startViewTransition({
            handedOver--

            // A page change that followed has skipped this one, and its own callback draws: drawn here, the latest
            // page would stand before that one's picture is taken
            if (handedOver == 0) {
                show(latest)
            }

            Promise.resolve(Unit)
        })

        // A page change that follows before this one faded skips it, which the browser reports as a rejection
        transition.ready.then({}, {})
    }

    /** Draws [route] in place of the page shown, the scrollers up. */
    private fun show(route: ActiveRoute) {
        shown = route

        scrollUp()
    }

    private fun scrollUp() {
        val root = dom ?: return

        window.scrollTo(0.0, 0.0)

        // A layout that stays mounted keeps its scrollers (the workbench's content): each one around the page goes up
        var scroller = root.querySelector(".router-page")?.parentElement

        while (scroller != null && scroller != root) {
            scroller.scrollTop = 0.0
            scroller = scroller.parentElement
        }
    }

    override fun VDom.render() {
        val layouts = shown.mountedRoute.layouts

        div(classes = "router") {
            key = "router"

            css {
                position = Position.relative
            }

            div(classes = "current") {
                // The layouts stay mounted while their pages change, so the workbench and its Motor live across the
                // pages (KLANG-00341); a route under other layouts draws them anew
                key = layouts.joinToString(separator = "/", prefix = "layouts:") { it.hashCode().toString() }

                renderInLayouts(layouts) {
                    div(classes = "router-page") {
                        key = "page"

                        css {
                            position = Position.relative
                            // The page's box, as its root had before it: full width also in a layout's flex row
                            width = 100.pct
                            height = 100.pct
                            // What a page stacks (the editor's key hints, its search panel) stays within the page, never
                            // over the layout (a tool window)
                            put("isolation", "isolate")
                        }

                        div {
                            // We define a key, so that the VDomEngine does a full redraw of the page, when the path changes
                            key = shown.uri.withoutQuery()

                            css {
                                height = 100.pct
                            }

                            shown.mountedRoute.view(this, shown.matchedRoute)
                        }
                    }
                }
            }
        }
    }

    /** Draws [content] inside [layouts], the outermost first, as Kraft's `ActiveRoute.render` does. */
    private fun FlowContent.renderInLayouts(layouts: List<MountedLayout>, content: FlowContent.() -> Unit) {
        val first = layouts.firstOrNull()

        if (first == null) {
            content()

            return
        }

        first.layout(this) { renderInLayouts(layouts.drop(1), content) }
    }
}
