package io.peekandpoke.klang.workshop.keys

import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.span
import kotlinx.html.style

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
 * it; [placement] and [hint] default to the app's (`KraftApp.Builder.keys`), [showHint] false shows none. It renders
 * an empty `span`, so it belongs where a `span` may stand (a button, a `div`; not straight into a `tr` or `ul`). The
 * machinery is an [OnKeyController]; a component that draws its key in its own way uses one directly.
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
            }

            onNextProps { new, _ ->
                key.action = new.action
            }

            onUpdate {
                // Enabled, the action or the look may have changed: the stage draws its hints again
                keyHintStage?.changed()
            }

            onUnmount {
                onStage?.remove()
                onStage = null
            }
        }
    }

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        // Only a mark in the page: the hint itself is on the stage
        span {
            style = "display: none"
        }
    }
}
