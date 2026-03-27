package io.peekandpoke.klang.comp

import io.peekandpoke.klang.utils.FullscreenController
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.semanticui.SemanticIconFn
import io.peekandpoke.ultra.semanticui.icon
import kotlinx.css.Cursor
import kotlinx.css.Display
import kotlinx.css.cursor
import kotlinx.css.display
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.title

@Suppress("FunctionName")
fun Tag.FullscreenToggleButton(
    fs: FullscreenController,
    style: SemanticIconFn = { circular },
) = comp(
    FullscreenToggleButton.Props(fs = fs, style = style)
) {
    FullscreenToggleButton(it)
}

class FullscreenToggleButton(ctx: Ctx<Props>) : Component<FullscreenToggleButton.Props>(ctx) {

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(
        val fs: FullscreenController,
        val style: SemanticIconFn,
    )

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private val state by subscribingTo(props.fs)

    private fun toggle() {
        props.fs.toggle()
    }

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        val styleFn = props.style
        div {
            css {
                cursor = Cursor.pointer
                display = Display.inlineBlock
            }
            onClick { toggle() }

            title = if (state.canExitWithClick) {
                "Toggle fullscreen"
            } else {
                "Press ESC to exit fullscreen"
            }

            if (state.isFullscreen) {
                icon.styleFn().compress()
            } else {
                icon.styleFn().expand()
            }
        }
    }
}
