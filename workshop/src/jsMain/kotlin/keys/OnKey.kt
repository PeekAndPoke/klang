package io.peekandpoke.klang.workshop.keys

import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Component.Companion.getNextDomKey
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.span
import kotlinx.html.style
import org.w3c.dom.Element

/** Draws a key hint's box, in the app's own look; the [KeyHintStage] places it. [KeyHints.badge] is the plain default. */
typealias KeyHintRenderer = FlowContent.(KeyHintState) -> Unit

/**
 * Answers [action] with [onKey] while this sits in the page and [enabled] is true, and shows its key hint next to the
 * element it sits in while the hints show. Put it inside the element it acts for:
 *
 * ```kotlin
 * ui.button {
 *     OnKey(MyActions.Save) { save() }
 *     onClick { save() }
 *     +"Save"
 * }
 * ```
 *
 * The hint is drawn on the app's [KeyHintStage], not inside the element, so nothing around it fades, clips or covers
 * it; [placement] and [hint] default to the app's (`KraftApp.Builder.keys`), [showHint] false shows none. In a button,
 * a link or an element with a button's role, the key also goes into the element's hover text (`title`), after the
 * element's own: "Menu (Alt+M)"; it follows a key the user changes, and the element's own title when the page changes
 * it, and goes with the OnKey (Klang's maintainer, 2026-11-02). Of two OnKeys in one element, the first whose key shows
 * and acts writes it. It renders an empty `span`, so it belongs
 * where a `span` may stand (a button, a `div`; not straight into a `tr` or `ul`). The machinery is an
 * [OnKeyController]; a component that draws its key in its own way uses one directly.
 */
@Suppress("FunctionName")
fun Tag.OnKey(
    action: KeyAction,
    enabled: Boolean = true,
    placement: KeyHintPlacement? = null,
    hint: KeyHintRenderer? = null,
    showHint: Boolean = true,
    onKey: () -> Unit,
) = comp(
    OnKey.Props(action = action, enabled = enabled, placement = placement, hint = hint, showHint = showHint, onKey = onKey)
) {
    OnKey(it)
}

/** See [Tag.OnKey]: an [OnKeyController], and an entry on the [KeyHintStage]. */
class OnKey(ctx: Ctx<Props>) : Component<OnKey.Props>(ctx) {

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(
        val action: KeyAction,
        val enabled: Boolean,
        val placement: KeyHintPlacement?,
        val hint: KeyHintRenderer?,
        val showHint: Boolean,
        val onKey: () -> Unit,
    )

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private val key = onKeyController(action = props.action, enabled = { props.enabled }) { props.onKey() }

    private var onStage: KeyHintStage.Registration? = null

    /** The element's own title, as the page wrote it, and the title with the key this OnKey wrote over it. */
    private var ownTitle: String? = null
    private var writtenTitle: String? = null

    /** The element whose title this OnKey writes, marked as its; null while it writes none. */
    private var titledHost: Element? = null

    /** This OnKey's mark on the element whose title it writes, so a second OnKey in it leaves the title alone. */
    private val titleMark = getNextDomKey()

    init {
        lifecycle {
            onMount {
                onStage = keyHintStage?.add(
                    // The element OnKey sits in: the button it acts for
                    anchor = { dom?.parentElement },
                    state = { key.hint?.takeIf { props.showHint } },
                    placement = { props.placement },
                    renderer = { props.hint },
                )

                keyInTitle()
            }

            onNextProps { new, _ ->
                key.action = new.action
            }

            onUpdate {
                // Enabled, the action or the look may have changed: the stage draws its hints again
                keyHintStage?.changed()
                keyInTitle()
            }

            onUnmount {
                onStage?.remove()
                onStage = null
                titledHost?.let { release(it) }
            }
        }
    }

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    /** The element OnKey sits in, where it is one that takes a hover text with its key: a button or the like. */
    private fun titled(): Element? = dom?.parentElement?.takeIf { host ->
        host.tagName.lowercase() in TITLED_TAGS || host.getAttribute("role") == TITLED_ROLE
    }

    /**
     * Redraws with every redraw of its element: its render is an empty span, and a title the page changes needs the key
     * again even where the OnKey's props are the same.
     */
    override fun shouldRedraw(nextProps: Props): Boolean = true

    /**
     * The key into the element's title, after its own: "Menu (Alt+M)"; while it shows its hint and acts. An element that
     * no longer takes one, or a key hidden or not acting, lets the title go, so another OnKey in it may write its key.
     */
    private fun keyInTitle() {
        val host = titled()
        val label = keys.labelOf(props.action)?.takeIf { props.showHint && props.enabled }

        titledHost?.takeIf { it !== host || label == null }?.let { release(it) }

        if (host == null || label == null) {
            return
        }

        val mark = host.getAttribute(TITLE_MARK)

        // Another OnKey in this element writes its title
        if (mark != null && mark != titleMark) {
            return
        }

        if (mark == null) {
            host.setAttribute(TITLE_MARK, titleMark)
        }

        val now = host.getAttribute("title")
        // What the page wrote, unless it is what this OnKey wrote last
        val own = if (now != null && now == writtenTitle) ownTitle else now
        val title = if (own.isNullOrBlank()) label else "$own ($label)"

        ownTitle = own
        writtenTitle = title
        titledHost = host
        restore(host = host, title = title)
    }

    /** The element's own title back, unless the page wrote another meanwhile; this OnKey's mark goes. */
    private fun release(host: Element) {
        if (host.getAttribute(TITLE_MARK) == titleMark) {
            if (host.getAttribute("title") == writtenTitle) {
                restore(host = host, title = ownTitle)
            }

            host.removeAttribute(TITLE_MARK)
        }

        titledHost = null
        writtenTitle = null
        ownTitle = null
    }

    private fun restore(host: Element, title: String?) {
        if (title == null) {
            host.removeAttribute("title")
        } else if (host.getAttribute("title") != title) {
            host.setAttribute("title", title)
        }
    }

    private companion object {
        /** The elements a hover text with their key belongs on, and the role that makes an element one. */
        val TITLED_TAGS = setOf("button", "a")
        const val TITLED_ROLE = "button"

        /** The attribute that marks which OnKey writes an element's title. */
        const val TITLE_MARK = "data-key-title"
    }

    override fun VDom.render() {
        // Only a mark in the page: the hint itself is on the stage
        span {
            style = "display: none"
        }
    }
}
