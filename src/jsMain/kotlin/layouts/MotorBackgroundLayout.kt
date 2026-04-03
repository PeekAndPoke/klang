package io.peekandpoke.klang.layouts

import io.peekandpoke.klang.comp.MotorBackground
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import kotlinx.css.Display
import kotlinx.css.Overflow
import kotlinx.css.display
import kotlinx.css.height
import kotlinx.css.maxHeight
import kotlinx.css.overflow
import kotlinx.css.pct
import kotlinx.css.vh
import kotlinx.css.width
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div

@Suppress("FunctionName")
fun Tag.MotorBackgroundLayout(
    inner: FlowContent.() -> Unit,
) = comp(
    MotorBackgroundLayout.Props(inner = inner)
) {
    MotorBackgroundLayout(it)
}

class MotorBackgroundLayout(ctx: Ctx<Props>) : Component<MotorBackgroundLayout.Props>(ctx) {

    data class Props(
        val inner: FlowContent.() -> Unit,
    )

    override fun VDom.render() {
        MotorBackground()

        div {
            key = "motor-bg-layout"
            css {
                width = 100.pct
                height = 100.vh
                maxHeight = 100.vh
                display = Display.flex
                overflow = Overflow.hidden
            }

            props.inner(this)
        }
    }
}
