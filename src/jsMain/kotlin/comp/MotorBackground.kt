package io.peekandpoke.klang.comp

import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.key
import kotlinx.html.Tag
import kotlinx.html.canvas
import kotlinx.html.id
import kotlinx.html.style

@Suppress("FunctionName")
fun Tag.MotorBackground() = comp {
    MotorBackground(it)
}

class MotorBackground(ctx: NoProps) : PureComponent(ctx) {

    init {
        lifecycle {
            onMount {
                js("if (typeof window.initMotorBackground === 'function') window.initMotorBackground()")
            }
        }
    }

    override fun VDom.render() {
        canvas {
            key = "motor-bg"
            id = "motor-bg"
            style = "position:fixed;top:0;left:0;width:100%;height:100%;z-index:-1;pointer-events:none;background-color:#191C22;"
        }
    }
}
