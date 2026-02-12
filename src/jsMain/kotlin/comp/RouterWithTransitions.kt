package io.peekandpoke.klang.comp

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.routing.ActiveRoute
import de.peekandpoke.kraft.routing.Router
import de.peekandpoke.kraft.routing.Router.Companion.router
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.common.maths.Ease
import de.peekandpoke.ultra.common.maths.Ease.timed
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.key
import de.peekandpoke.ultra.streams.ops.distinct
import de.peekandpoke.ultra.streams.ops.map
import de.peekandpoke.ultra.streams.ops.ticker
import kotlinx.browser.window
import kotlinx.css.*
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
        val uri: String = route.uri.withoutQuery(),
        val timing: Ease.Timed = Ease.linear.timed(1.0, 0.0, 0.200.seconds),
    )

    ////  STATE  ///////////////////////////////////////////////////////////////////////////////////////////////////////

    private val activeRouter get() = props.router ?: router

    private var previousRoute: ActiveRoute? = null

    private var inTransitions by value(listOf<InTransition>())

    private val currentRoute: ActiveRoute by subscribingTo(activeRouter.current) { next ->
        previousRoute?.let { previous ->
            val previousUri = previous.uri.withoutQuery()
            val nextUri = next.uri.withoutQuery()

            if (previousUri != nextUri) {
                inTransitions += InTransition(route = previous)
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
        dom?.let {
            window.scrollTo(0.0, 0.0)
        }
    }

    override fun VDom.render() {
        div(classes = "router") {
            key = "router"

            css {
                position = Position.relative
            }

            div(classes = "current") {
                // We define a key, so that the VDomEngine does a full redraw of the content, when the matched route changes
                key = currentUri

                css {
                    zIndex = 1
                }

                currentRoute.render(this)
            }

//            console.log("Render states:", inTransitions.map {
//                it.uri to it.timing.isDone
//            }.toTypedArray())

            div(classes = "previous") {
                key = "previous-states"
                css {
                    position = Position.absolute
                    top = 0.px
                    left = 0.px
                    bottom = 0.px
                    right = 0.px
                    pointerEvents = PointerEvents.none
                }

                if (inTransitions.isNotEmpty()) {
                    inTransitions.forEachIndexed { index, previous ->
                        div(classes = "fade-out") {
                            key = "in-transition-$index"
                            css {
                                position = Position.absolute
                                top = 0.px
                                left = 0.px
                                bottom = 0.px
                                right = 0.px
                                pointerEvents = PointerEvents.none
                                zIndex = 1000 + index
                            }

                            previous.route.render(this)
                        }
                    }
                }
            }
        }
    }
}
