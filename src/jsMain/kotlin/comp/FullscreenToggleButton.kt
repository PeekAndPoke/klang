package io.peekandpoke.klang.comp

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.onClick
import de.peekandpoke.ultra.semanticui.SemanticFn
import de.peekandpoke.ultra.semanticui.icon
import de.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.klang.utils.FullscreenController
import kotlinx.html.Tag
import kotlinx.html.title

@Suppress("FunctionName")
fun Tag.FullscreenToggleButton(
    fs: FullscreenController,
    style: SemanticFn = { large.circular.white },
) = comp(
    FullscreenToggleButton.Props(fs = fs, style = style)
) {
    FullscreenToggleButton(it)
}

class FullscreenToggleButton(ctx: Ctx<Props>) : Component<FullscreenToggleButton.Props>(ctx) {

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(
        val fs: FullscreenController,
        val style: SemanticFn,
    )

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private val state by subscribingTo(props.fs)

    private fun toggle() {
        props.fs.toggle()
    }

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        val styleFn = props.style
        ui.styleFn()
            .givenNot(state.canExitWithClick) { disabled }
            .icon.button {
                onClick { toggle() }

                title = if (state.canExitWithClick) {
                    "Toggle fullscreen"
                } else {
                    "Press ESC to exit fullscreen"
                }

                if (state.isFullscreen) {
                    icon.compress()
                } else {
                    icon.expand()
                }
            }
    }
}
