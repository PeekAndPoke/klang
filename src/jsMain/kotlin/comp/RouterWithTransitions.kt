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
import io.peekandpoke.ultra.maths.Ease
import io.peekandpoke.ultra.maths.Ease.timed
import io.peekandpoke.ultra.streams.ops.distinct
import io.peekandpoke.ultra.streams.ops.map
import io.peekandpoke.ultra.streams.ops.ticker
import kotlinx.browser.window
import kotlinx.css.PointerEvents
import kotlinx.css.Position
import kotlinx.css.bottom
import kotlinx.css.height
import kotlinx.css.left
import kotlinx.css.pct
import kotlinx.css.pointerEvents
import kotlinx.css.position
import kotlinx.css.px
import kotlinx.css.right
import kotlinx.css.top
import kotlinx.css.width
import kotlinx.css.zIndex
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Suppress("FunctionName")
fun Tag.RouterWithTransitions(
    router: Router? = null,
) = comp(RouterWithTransitions.Props(router)) {
    RouterWithTransitions(it)
}

class RouterWithTransitions internal constructor(ctx: Ctx<Props>) : Component<RouterWithTransitions.Props>(ctx) {

    companion object {
        private fun String.withoutQuery() = split("?").firstOrNull() ?: ""
    }

    data class Props(
        val router: Router?,
    )

    private data class InTransition(
        val route: ActiveRoute,
        /** Its own key, so a fade that ends early never hands its element (and its spent animation) to the next. */
        val id: Int,
        val timing: Ease.Timed = Ease.linear.timed(1.0, 0.0, 0.200.seconds),
    )

    ////  STATE  ///////////////////////////////////////////////////////////////////////////////////////////////////////

    private val activeRouter get() = props.router ?: router

    private var previousRoute: ActiveRoute? = null

    private var inTransitions by value(listOf<InTransition>())

    private var transitionCount = 0

    private val currentRoute: ActiveRoute by subscribingTo(activeRouter.current) { next ->
        previousRoute?.let { previous ->
            val previousUri = previous.uri.withoutQuery()
            val nextUri = next.uri.withoutQuery()

            if (previousUri != nextUri) {
                inTransitions += InTransition(route = previous, id = ++transitionCount)
            }
        }

        previousRoute = next
    }

    private val currentUri: String by subscribingTo(
        activeRouter.current
            .map { it.uri.withoutQuery() }
            .distinct()
    ) {
        scrollUp()
    }

    private val ticker = ticker(100.milliseconds)

    init {
        lifecycle {
            onMount {
                ticker {
                    // clean up transitions
                    inTransitions = inTransitions.filterNot { it.timing.isDone }
                }
            }
        }
    }

    ////  IMPL  ////////////////////////////////////////////////////////////////////////////////////////////////////////

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
        val layouts = currentRoute.mountedRoute.layouts
        // A page left under the same layouts fades inside them; one left under other layouts fades as a whole
        val (inside, outside) = inTransitions.partition { it.route.mountedRoute.layouts == layouts }

        div(classes = "router") {
            key = "router"

            css {
                position = Position.relative
            }

            div(classes = "current") {
                // The layouts stay mounted while their pages change, so the workbench and its Motor live across the
                // pages (KLANG-00341); a route under other layouts draws them anew
                key = layouts.joinToString(separator = "/", prefix = "layouts:") { it.hashCode().toString() }

                css {
                    zIndex = 1
                }

                renderInLayouts(layouts) {
                    div(classes = "router-page") {
                        key = "page"

                        css {
                            position = Position.relative
                            // The page's box, as its root had before it: full width also in a layout's flex row
                            width = 100.pct
                            height = 100.pct
                            // The pages fading out stack within the page, never over the layout (a tool window)
                            put("isolation", "isolate")
                        }

                        div {
                            // We define a key, so that the VDomEngine does a full redraw of the page, when the path changes
                            key = currentUri

                            css {
                                height = 100.pct
                            }

                            currentRoute.mountedRoute.view(this, currentRoute.matchedRoute)
                        }

                        renderFadingOut(inside) { route -> route.mountedRoute.view(this, route.matchedRoute) }
                    }
                }
            }

            renderFadingOut(outside) { route -> route.render(this) }
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

    /** The pages just left, fading out over the current one. */
    private fun FlowContent.renderFadingOut(transitions: List<InTransition>, render: FlowContent.(ActiveRoute) -> Unit) {
        div(classes = "previous") {
            key = "previous-states"
            // The page just left, fading out: inert, so it takes no focus and answers no keys (KLANG-00325). Not "":
            // Preact sets inert as the element's property, and an empty string is false there
            attributes["inert"] = "inert"
            css {
                position = Position.absolute
                top = 0.px
                left = 0.px
                bottom = 0.px
                right = 0.px
                pointerEvents = PointerEvents.none
            }

            transitions.forEachIndexed { index, previous ->
                div(classes = "fade-out") {
                    key = "in-transition-${previous.id}"
                    css {
                        position = Position.absolute
                        top = 0.px
                        left = 0.px
                        bottom = 0.px
                        right = 0.px
                        pointerEvents = PointerEvents.none
                        zIndex = 1000 + index
                    }

                    render(previous.route)
                }
            }
        }
    }
}
