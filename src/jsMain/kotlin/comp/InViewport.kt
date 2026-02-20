package io.peekandpoke.klang.comp

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.css
import io.peekandpoke.klang.externals.IntersectionObserver
import kotlinx.css.minHeight
import kotlinx.css.px
import kotlinx.html.Tag
import kotlinx.html.div

@Suppress("FunctionName")
fun Tag.InViewport(content: Tag.() -> Unit) = comp(
    InViewport.Props(content = content)
) {
    InViewport(it)
}

/**
 * Lazy viewport-tracking wrapper.
 *
 * Renders [Props.content] only while the component's root element is inside (or near) the
 * browser viewport. When the element is scrolled out of view the content is unmounted and
 * replaced with an invisible placeholder div that preserves the last known height, preventing
 * layout shift.
 *
 * Use this to avoid mounting expensive components (e.g. CodeMirror instances) for off-screen
 * content.
 */
class InViewport(ctx: Ctx<Props>) : Component<InViewport.Props>(ctx) {

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(val content: Tag.() -> Unit)

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private var isVisible: Boolean by value(false)

    /** Last measured height of the content. Shown as placeholder height when not visible. */
    private var lastHeight: Double by value(200.0)

    private var intersectionObserver: IntersectionObserver? = null

    //  LIFECYCLE  //////////////////////////////////////////////////////////////////////////////////////////////

    init {
        lifecycle {
            onMount {
                dom?.let { element ->
                    val options: dynamic = js("{}")
                    // Pre-load slightly before entering the viewport to eliminate perceived lag.
                    options.rootMargin = "300px"

                    intersectionObserver = IntersectionObserver({ entries, _ ->
                        entries.forEach { entry ->
                            if (entry.isIntersecting) {
                                isVisible = true
                            } else {
                                // Capture height while content is still in the DOM.
                                val h = dom?.clientHeight?.toDouble() ?: 0.0
                                if (h > 0) lastHeight = h
                                isVisible = false
                            }
                        }
                    }, options)

                    intersectionObserver?.observe(element)
                }
            }

            onUnmount {
                intersectionObserver?.disconnect()
            }
        }
    }

    //  RENDER  /////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        div {
            if (isVisible) {
                props.content(this)
            } else {
                css { minHeight = lastHeight.px }
            }
        }
    }
}
